package eu.stratuslab.hudson;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.AbstractCloudImpl;

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

public class StratusLabCloud extends AbstractCloudImpl {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private static final String CLOUD_NAME = "StratusLab Cloud";

    public final CloudParameters params;

    public final ArrayList<CloudParameters> cloudParameters;

    public final List<SlaveTemplate> templates;

    private final Map<String, SlaveTemplate> labelToTemplateMap;

    private static final AtomicInteger serial = new AtomicInteger(0);

    @DataBoundConstructor
    public StratusLabCloud(ArrayList<CloudParameters> cloudParameters,
            List<SlaveTemplate> templates) {

        super(CLOUD_NAME, String.valueOf(cloudParameters.get(0).instanceLimit));

        this.cloudParameters = cloudParameters;
        this.params = cloudParameters.get(0);

        this.templates = copyToImmutableList(templates);

        labelToTemplateMap = mapLabelsToTemplates(this.templates);

        String format = "configuration updated with %s label(s) and %s slave template(s)";
        LOGGER.info(String.format(format, labelToTemplateMap.size(),
                this.templates.size()));
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

            int numberOfInstances = StratusLabProxy
                    .getNumberOfDefinedInstances(params);

            for (int i = 0; i < excessWorkload; i += template.executors) {
                if (numberOfInstances < params.instanceLimit) {
                    String displayName = generateDisplayName(label, template);
                    SlaveCreator c = new SlaveCreator(template, params, label);
                    Future<Node> futureNode = Computer.threadPoolForRemoting
                            .submit(c);
                    nodes.add(new PlannedNode(displayName, futureNode,
                            template.executors));
                    numberOfInstances++;
                } else {
                    String fmt = "instance limit (%s) exceeded; not provisioning node";
                    LOGGER.warning(String.format(fmt, params.instanceLimit));
                }
            }
        }

        String fmt = "allocating %s node(s)";
        LOGGER.info(String.format(fmt, nodes.size()));
        return nodes;
    }

    public static String generateDisplayName(Label label, SlaveTemplate template) {

        final String fmt = "%s-%d (%s, %s)";
        return String.format(fmt, label.getName(), serial.incrementAndGet(),
                template.marketplaceId, template.instanceType.tag());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return CLOUD_NAME;
        }

    }

}
