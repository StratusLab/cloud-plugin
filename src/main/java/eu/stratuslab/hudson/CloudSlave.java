package eu.stratuslab.hudson;

import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.model.Node;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProperty;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.stratuslab.hudson.StratusLabProxy.InstanceInfo;

@SuppressWarnings("serial")
public class CloudSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private final CloudParameters cloudParams;

    private InstanceInfo info;

    private final SlaveTemplate template;

    public CloudSlave(CloudParameters cloudParams, SlaveTemplate template,
            String name, String nodeDescription, String remoteFS,
            int numExecutors, Node.Mode mode, String labelString,
            List<? extends NodeProperty<?>> nodeProperties)
            throws FormException, IOException, StratusLabException {

        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
                null, null, nodeProperties);

        this.cloudParams = cloudParams;
        this.template = template;

        createInstance();

        setLauncher(new StratusLabLauncher(cloudParams, info));

        this.setRetentionStrategy(new CloudRetentionStrategy(
                template.idleMinutes));

    }

    @Override
    public StratusLabComputer createComputer() {

        LOGGER.log(Level.INFO, "createComputer called");
        return new StratusLabComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException,
            InterruptedException {

        LOGGER.log(Level.INFO, "_terminate called");

        String msg = "killing instance " + info;
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

    private void createInstance() throws StratusLabException {

        String msg = "creating instance ";
        LOGGER.info(msg);

        info = StratusLabProxy.startInstance(cloudParams,
                template.marketplaceId);

        msg = "created instance with " + info;
        LOGGER.info(msg);
    }

}
