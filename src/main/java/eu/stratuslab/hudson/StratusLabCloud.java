package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.isPositiveInteger;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.validateClientLocation;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.validateEndpoint;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.AbstractCloudImpl;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import eu.stratuslab.hudson.SlaveTemplate.InstanceTypes;

public class StratusLabCloud extends AbstractCloudImpl {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private static final String CLOUD_NAME = "StratusLab Cloud";

    public final StratusLabProxy.StratusLabParams params;

    public final String clientLocation;

    public final String endpoint;

    public final String username;

    public final String password;

    public final int instanceLimit;

    public final List<SlaveTemplate> templates;

    private final Map<String, SlaveTemplate> labelToTemplateMap;

    private static final AtomicInteger serial = new AtomicInteger(0);

    @DataBoundConstructor
    public StratusLabCloud(String clientLocation, String endpoint,
            String username, String password, int instanceLimit,
            List<SlaveTemplate> templates) {

        super(CLOUD_NAME, String.valueOf(instanceLimit));

        this.clientLocation = clientLocation;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;

        params = new StratusLabProxy.StratusLabParams(clientLocation, endpoint,
                username, password);

        this.instanceLimit = instanceLimit;
        this.templates = copyToImmutableList(templates);

        labelToTemplateMap = mapLabelsToTemplates(this.templates);

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

            String displayName = generateDisplayName(label,
                    template.marketplaceId, template.instanceType);

            int numberOfInstances = StratusLabProxy
                    .getNumberOfDefinedInstances(params);

            for (int i = 0; i < excessWorkload; i += template.executors) {
                SlaveCreator c = new SlaveCreator(template, this, label,
                        displayName);
                Future<Node> futureNode = Computer.threadPoolForRemoting
                        .submit(c);
                if (numberOfInstances < instanceLimit) {
                    nodes.add(new PlannedNode(displayName, futureNode,
                            template.executors));
                    numberOfInstances++;
                } else {
                    LOGGER.warning("instance limit (" + instanceLimit
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
                marketplaceId, type.tag());
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
                @QueryParameter int instanceLimit) {

            if (!isPositiveInteger(instanceLimit)) {
                return FormValidation
                        .error("instance limit must be a positive integer");
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
