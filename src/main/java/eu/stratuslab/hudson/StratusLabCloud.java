package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.CloudParameterUtils.isPositiveInteger;
import static eu.stratuslab.hudson.CloudParameterUtils.validateClientLocation;
import static eu.stratuslab.hudson.CloudParameterUtils.validateEndpoint;
import hudson.Extension;
import hudson.model.Computer;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import eu.stratuslab.hudson.SlaveTemplate.InstanceTypes;

public class StratusLabCloud extends AbstractCloudImpl {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private static final int IDLE_MINUTES = 10;

    private static final String CLOUD_NAME = "StratusLab Cloud";

    private static final String EMPTY_STRING = "";

    public final StratusLabProxy.StratusLabParams params;

    public final String clientLocation;

    public final String endpoint;

    public final String username;

    public final String password;

    public final String instanceLimit;

    public final int instanceLimitInt;

    public final List<SlaveTemplate> templates;

    private final Map<String, SlaveTemplate> labelToTemplateMap;

    private static final AtomicInteger serial = new AtomicInteger(0);

    @DataBoundConstructor
    public StratusLabCloud(String clientLocation, String endpoint,
            String username, String password, String instanceLimit,
            List<SlaveTemplate> templates) {

        super(CLOUD_NAME, instanceLimit);

        this.clientLocation = clientLocation;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;

        params = new StratusLabProxy.StratusLabParams(clientLocation, endpoint,
                username, password);

        this.instanceLimit = instanceLimit;
        this.templates = copyToImmutableList(templates);

        labelToTemplateMap = mapLabelsToTemplates(this.templates);

        int value = 1;
        try {
            value = Integer.parseInt(instanceLimit);
        } catch (IllegalArgumentException e) {
        }
        instanceLimitInt = value;

        String msg = "configuration updated with " + labelToTemplateMap.size()
                + " label(s) and " + this.templates.size()
                + " slave template(s)";
        LOGGER.info(msg);
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

    private Map<String, SlaveTemplate> mapLabelsToTemplates(
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
            SlaveTemplate template = labelToTemplateMap.get(label.getName());

            int executors = template.getExecutors();

            String displayName = generateDisplayName(label,
                    template.marketplaceId, template.instanceType);

            int numberOfInstances = StratusLabProxy
                    .getNumberOfDefinedInstances(params);

            for (int i = 0; i < excessWorkload; i += executors) {
                SlaveCreator c = new SlaveCreator(template, this, label,
                        displayName);
                Future<Node> futureNode = Computer.threadPoolForRemoting
                        .submit(c);
                if (numberOfInstances < instanceLimitInt) {
                    nodes.add(new PlannedNode(displayName, futureNode,
                            executors));
                    numberOfInstances++;
                } else {
                    LOGGER.warning("instance limit (" + instanceLimitInt
                            + ") exceeded not provisioning node");
                }
            }
        }

        String msg = "allocating " + nodes.size() + " node(s)";
        LOGGER.info(msg);
        return nodes;
    }

    public static String generateDisplayName(Label label, String marketplaceId,
            InstanceTypes type) {

        final String format = "%s-%d (%s, %s)";
        return String.format(format, label.getName(), serial.incrementAndGet(),
                marketplaceId, type.label());
    }

    public static class SlaveCreator implements Callable<Node> {

        private static final Logger LOGGER = Logger
                .getLogger(StratusLabCloud.class.getName());

        private final SlaveTemplate template;

        private final StratusLabCloud cloud;

        private final Label label;

        private final String displayName;

        private int vmid;

        private String ip;

        public SlaveCreator(SlaveTemplate template, StratusLabCloud cloud,
                Label label, String displayName) {
            this.template = template;
            this.cloud = cloud;
            this.label = label;
            this.displayName = displayName;
        }

        public Node call() throws IOException, StratusLabException,
                Descriptor.FormException {

            createInstance();

            ComputerLauncher launcher = new StratusLabLauncher(cloud.params,
                    vmid, ip);

            CloudRetentionStrategy retentionStrategy = new CloudRetentionStrategy(
                    IDLE_MINUTES);

            List<? extends NodeProperty<?>> nodeProperties = new LinkedList<NodeProperty<Node>>();

            String description = template.marketplaceId + ", "
                    + template.instanceType.tag();

            LOGGER.info("generating slave for " + label + " " + description);

            StratusLabSlave slave = new StratusLabSlave(label.getName(),
                    description, template.remoteFS, template.getExecutors(),
                    Node.Mode.NORMAL, label.getName(), launcher,
                    retentionStrategy, nodeProperties);

            String msg = "created node for " + displayName;
            LOGGER.info(msg);

            return slave;
        }

        private void createInstance() throws StratusLabException {

            String msg = "creating instance for " + displayName;
            LOGGER.info(msg);

            String[] fields = StratusLabProxy.startInstance(cloud.params,
                    template.marketplaceId);

            ip = fields[1];

            vmid = -1;
            try {
                vmid = Integer.parseInt(fields[0]);
            } catch (IllegalArgumentException e) {
                throw new StratusLabException(
                        "extracted VM ID is not an integer: " + fields[0]);
            }

            msg = "created instance for " + displayName + " with " + vmid
                    + ", " + ip;

            LOGGER.info(msg);
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

            StratusLabProxy.StratusLabParams params = new StratusLabProxy.StratusLabParams(
                    clientLocation, endpoint, username, password);

            try {
                StratusLabProxy.testConnection(params);
            } catch (StratusLabException e) {
                return FormValidation.error(e.getMessage());
            }

            return FormValidation.ok();
        }

    }

}
