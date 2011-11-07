package eu.stratuslab.hudson;

import hudson.slaves.AbstractCloudComputer;

public class StratusLabComputer extends AbstractCloudComputer<StratusLabSlave> {

    public StratusLabComputer(StratusLabSlave slave) {
        super(slave);
    }

}
