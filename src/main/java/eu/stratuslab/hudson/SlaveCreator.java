package eu.stratuslab.hudson;

import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import eu.stratuslab.hudson.StratusLabProxy.InstanceInfo;

public class SlaveCreator implements Callable<Node> {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private final SlaveTemplate template;

    private final StratusLabCloud cloud;

    private final Label label;

    private final String displayName;

    private InstanceInfo info;

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

        ComputerLauncher launcher = new StratusLabLauncher(cloud.params, info);

        CloudRetentionStrategy retentionStrategy = new CloudRetentionStrategy(
                template.idleMinutes);

        List<? extends NodeProperty<?>> nodeProperties = new LinkedList<NodeProperty<Node>>();

        String description = template.marketplaceId + ", "
                + template.instanceType.tag();

        LOGGER.info("generating slave for " + label + " " + description);

        StratusLabSlave slave = new StratusLabSlave(cloud.params, info,
                label.getName(), description, template.remoteFS,
                template.executors, Node.Mode.NORMAL, label.getName(),
                launcher, retentionStrategy, nodeProperties);

        String msg = "created node for " + displayName;
        LOGGER.info(msg);

        return slave;
    }

    private void createInstance() throws StratusLabException {

        String msg = "creating instance for " + displayName;
        LOGGER.info(msg);

        info = StratusLabProxy.startInstance(cloud.params,
                template.marketplaceId);

        msg = "created instance for " + displayName + " with " + info;

        LOGGER.info(msg);
    }
}
