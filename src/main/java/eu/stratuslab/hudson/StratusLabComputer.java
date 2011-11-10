package eu.stratuslab.hudson;

import hudson.slaves.AbstractCloudComputer;

public class StratusLabComputer extends AbstractCloudComputer<CloudSlave> {

    public StratusLabComputer(CloudSlave slave) {
        super(slave);
    }

}
