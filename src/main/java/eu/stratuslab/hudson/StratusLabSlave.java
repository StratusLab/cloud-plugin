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

@SuppressWarnings("serial")
public class StratusLabSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    public StratusLabSlave(String name, String nodeDescription,
            String remoteFS, int numExecutors, Node.Mode mode,
            String labelString, ComputerLauncher launcher,
            CloudRetentionStrategy retentionStrategy,
            List<? extends NodeProperty<?>> nodeProperties)
            throws FormException, IOException {

        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
                launcher, retentionStrategy, nodeProperties);
    }

    @Override
    public StratusLabComputer createComputer() {
        // TODO Auto-generated method stub

        LOGGER.log(Level.INFO, "createComputer called");
        return new StratusLabComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException,
            InterruptedException {
        // TODO Auto-generated method stub

        LOGGER.log(Level.INFO, "_terminate called");

    }

}
