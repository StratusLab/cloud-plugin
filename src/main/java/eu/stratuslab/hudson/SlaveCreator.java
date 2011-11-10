package eu.stratuslab.hudson;

import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.NodeProperty;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class SlaveCreator implements Callable<Node> {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private final SlaveTemplate template;

    private final CloudParameters cloudParams;

    private final Label label;

    public SlaveCreator(SlaveTemplate template, CloudParameters cloud,
            Label label) {

        this.template = template;
        this.cloudParams = cloud;
        this.label = label;
    }

    public Node call() throws IOException, StratusLabException,
            Descriptor.FormException {

        List<? extends NodeProperty<?>> nodeProperties = new LinkedList<NodeProperty<Node>>();

        String description = template.marketplaceId + ", "
                + template.instanceType.tag();

        LOGGER.info("creating slave for " + label + " " + description);

        CloudSlave slave = new CloudSlave(cloudParams, template,
                label.getName(), description, template.remoteFS,
                template.executors, Node.Mode.NORMAL, label.getName(),
                nodeProperties);

        String msg = "slave created for " + label + " " + description;
        LOGGER.info(msg);

        return slave;
    }

}
