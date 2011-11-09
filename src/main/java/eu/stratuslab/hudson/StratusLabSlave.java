package eu.stratuslab.hudson;

import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.model.Node;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.stratuslab.hudson.StratusLabProxy.InstanceInfo;
import eu.stratuslab.hudson.StratusLabProxy.StratusLabParams;

@SuppressWarnings("serial")
public class StratusLabSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private final StratusLabParams cloudParams;

    private final InstanceInfo info;

    private StratusLabComputer computer;

    public StratusLabSlave(StratusLabParams cloudParams, InstanceInfo info,
            String name, String nodeDescription, String remoteFS,
            int numExecutors, Node.Mode mode, String labelString,
            ComputerLauncher launcher,
            CloudRetentionStrategy retentionStrategy,
            List<? extends NodeProperty<?>> nodeProperties)
            throws FormException, IOException {

        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
                launcher, retentionStrategy, nodeProperties);

        this.cloudParams = cloudParams;
        this.info = info;
    }

    @Override
    public StratusLabComputer createComputer() {

        LOGGER.log(Level.INFO, "createComputer called");
        computer = new StratusLabComputer(this);
        return computer;
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException,
            InterruptedException {

        LOGGER.log(Level.INFO, "_terminate called");

        String msg = "killing instance " + info + ", " + computer.getName();
        LOGGER.info(msg);
        listener.getLogger().println(msg);

        try {

            StratusLabProxy
                    .killInstance(cloudParams, String.valueOf(info.vmid));

        } catch (StratusLabException e) {
            LOGGER.severe(e.getMessage());
            listener.error(e.getMessage());
        }

    }

}
