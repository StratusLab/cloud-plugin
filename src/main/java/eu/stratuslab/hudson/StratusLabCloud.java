package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.CloudParameterUtils.isPositiveInteger;
import static eu.stratuslab.hudson.CloudParameterUtils.validateClientLocation;
import static eu.stratuslab.hudson.CloudParameterUtils.validateEndpoint;
import static eu.stratuslab.hudson.ProcessUtils.runCommand;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.ComputerLauncher;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class StratusLabCloud extends AbstractCloudImpl {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private static final int IDLE_MINUTES = 1;

    private static final String CLOUD_NAME = "StratusLab Cloud";

    private static final String EMPTY_STRING = "";

    public final String clientLocation;

    public final String endpoint;

    public final String username;

    public final String password;

    public final String instanceLimit;

    public final List<SlaveTemplate> templates;

    private final Map<String, SlaveTemplate> labelToTemplateMap;

    @DataBoundConstructor
    public StratusLabCloud(String clientLocation, String endpoint,
            String username, String password, String instanceLimit,
            List<SlaveTemplate> templates) {
        super(CLOUD_NAME, instanceLimit);

        this.clientLocation = clientLocation;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.instanceLimit = instanceLimit;
        this.templates = copyToImmutableList(templates);

        labelToTemplateMap = createLabelToTemplateMap(this.templates);

        String msg = "StratusLab cloud: configuration updated with "
                + labelToTemplateMap.size() + " label(s) and "
                + templates.size() + " slave template(s)";
        LOGGER.log(Level.INFO, msg);
    }

    private List<SlaveTemplate> copyToImmutableList(
            List<SlaveTemplate> templates) {
        ArrayList<SlaveTemplate> list = new ArrayList<SlaveTemplate>();
        if (templates != null) {
            list.addAll(templates);
        }
        list.trimToSize();
        return Collections.unmodifiableList(list);
    }

    private Map<String, SlaveTemplate> createLabelToTemplateMap(
            List<SlaveTemplate> templates) {

        Map<String, SlaveTemplate> map = new HashMap<String, SlaveTemplate>();

        for (SlaveTemplate template : templates) {
            for (String label : template.labels) {
                map.put(label, template);
            }
        }

        return Collections.unmodifiableMap(map);
    }

    // TODO: Check if this is necessary or defined in superclass.
    @SuppressWarnings("unchecked")
    public Descriptor<Cloud> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public boolean canProvision(Label label) {
        if (label != null) {
            return labelToTemplateMap.containsKey(label.getName());
        } else {
            return false;
        }
    }

    public Collection<PlannedNode> provision(Label label, int excessWorkload) {

        Collection<PlannedNode> nodes = new LinkedList<PlannedNode>();

        // The returned value for template should never be null because
        // only labels will be used for which the canProvision method
        // returned true. However, label can be null if Hudson is searching
        // for a node without a label. This implementation doesn't support
        // that.
        if (label != null) {
            String stringLabel = label.getName();
            SlaveTemplate template = labelToTemplateMap.get(stringLabel);

            String id = template.marketplaceId;
            String type = template.instanceType.name();
            int executorsPerInstance = template.getExecutors();

            String displayName = stringLabel + " (" + id + ", " + type + ")";

            for (int i = 0; i < excessWorkload; i += executorsPerInstance) {
                PlannedNode node = new PlannedNode(displayName, getNodeCreator(
                        template, stringLabel), executorsPerInstance);
                nodes.add(node);
            }
        }

        String msg = "StratusLab cloud: allocating " + nodes.size()
                + " node(s)";
        LOGGER.log(Level.INFO, msg);
        return nodes;
    }

    public Future<Node> getNodeCreator(SlaveTemplate template, String label) {
        VmCreator creator = new VmCreator(template, this, label);
        return new FutureTask<Node>(creator);
    }

    public static class VmCreator implements Callable<Node> {

        private final SlaveTemplate template;

        private final StratusLabCloud cloud;

        private final String label;

        public VmCreator(SlaveTemplate template, StratusLabCloud cloud,
                String label) {
            this.template = template;
            this.cloud = cloud;
            this.label = label;
        }

        public Node call() throws IOException, Descriptor.FormException {

            Logger logger = Logger.getLogger(StratusLabCloud.class.getName());

            ComputerLauncher launcher = new StratusLabLauncher(cloud,
                    template.marketplaceId);

            CloudRetentionStrategy retentionStrategy = new CloudRetentionStrategy(
                    IDLE_MINUTES);

            List<? extends NodeProperty<?>> nodeProperties = new LinkedList<NodeProperty<Node>>();

            String name = template.labelString;
            String description = template.marketplaceId + ", "
                    + template.instanceType.name();

            StratusLabSlave slave = new StratusLabSlave(name, description,
                    template.remoteFS, template.getExecutors(),
                    Node.Mode.NORMAL, name, launcher, retentionStrategy,
                    nodeProperties);

            String msg = "StratusLab cloud: generated node for label " + label
                    + " with Marketplace Id " + template.marketplaceId;
            logger.log(Level.INFO, msg);

            return slave;

        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return CLOUD_NAME;
        }

        public FormValidation doCheckClientLocation(
                @QueryParameter String clientLocation) {
            return validateClientLocation(clientLocation);
        }

        public FormValidation doCheckEndpoint(@QueryParameter String endpoint) {
            return validateEndpoint(endpoint);
        }

        public FormValidation doCheckUsername(@QueryParameter String username) {
            if (isEmptyStringOrNull(username)) {
                return FormValidation.error("username must be defined");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckPassword(@QueryParameter String password) {
            if (isEmptyStringOrNull(password)) {
                return FormValidation.error("password must be defined");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckInstanceLimit(
                @QueryParameter String instanceLimit) {
            if (!isPositiveInteger(instanceLimit)
                    && !EMPTY_STRING.equals(instanceLimit)) {
                return FormValidation
                        .error("instance cap must be a valid positive integer or blank");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doTestConnection(
                @QueryParameter String clientLocation,
                @QueryParameter String endpoint,
                @QueryParameter String username, @QueryParameter String password) {

            try {
                runCommand(clientLocation, "stratus-describe-instance",
                        "--endpoint", endpoint, "--username", username,
                        "--password", password);
            } catch (StratusLabException e) {
                return FormValidation.error(e.getMessage());
            }

            return FormValidation.ok();
        }

    }

}
