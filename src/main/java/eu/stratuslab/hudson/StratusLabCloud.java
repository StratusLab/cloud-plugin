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
import hudson.slaves.NodeProperty;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
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

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class StratusLabCloud extends AbstractCloudImpl {

    private static final long DEMAND_DELAY = 60 * 1000L; // 1 minute

    private static final long IDLE_DELAY = 60 * 1000L; // 1 minute

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

        this.labelToTemplateMap = createLabelToTemplateMap(this.templates);
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

    @SuppressWarnings("unchecked")
    public Descriptor<Cloud> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public boolean canProvision(Label label) {
        return labelToTemplateMap.containsKey(label.getName());
    }

    public Collection<PlannedNode> provision(Label label, int excessWorkload) {

        SlaveTemplate template = labelToTemplateMap.get(label.getName());
        String id = template.marketplaceId;
        String type = template.instanceType.name();

        Collection<PlannedNode> nodes = new LinkedList<PlannedNode>();

        for (int i = 0; i < excessWorkload; i++) {
            PlannedNode node = new PlannedNode(id, getNodeCreator(id, type), 1);
            nodes.add(node);
        }

        return nodes;
    }

    public Future<Node> getNodeCreator(String id, String type) {
        return new FutureTask<Node>(new VmCreator(id, type, endpoint, username,
                password));
    }

    public static class VmCreator implements Callable<Node> {

        private final String id;

        private final String type;

        private final String endpoint;

        private final String username;

        private final String password;

        public VmCreator(String id, String type, String endpoint,
                String username, String password) {
            this.id = id;
            this.type = type;
            this.endpoint = endpoint;
            this.username = username;
            this.password = password;
        }

        public Node call() throws IOException, Descriptor.FormException {
            ComputerLauncher launcher = null;
            RetentionStrategy.Demand strategy = new RetentionStrategy.Demand(
                    DEMAND_DELAY, IDLE_DELAY);

            List<? extends NodeProperty<?>> nodeProperties = new LinkedList<NodeProperty<Node>>();

            return new DumbSlave(id, id, "/var/lib/hudson", "1",
                    Node.Mode.NORMAL, id, launcher, strategy, nodeProperties);

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
