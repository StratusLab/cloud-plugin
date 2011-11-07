package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.ProcessUtils.runCommandWithResults;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.PrintStream;

import eu.stratuslab.hudson.ProcessUtils.ProcessOutput;

public class StratusLabLauncher extends ComputerLauncher {

    private final StratusLabCloud cloud;

    private final String marketplaceId;

    private int vmid = -1;

    private String ip = null;

    public StratusLabLauncher(StratusLabCloud cloud, String marketplaceId) {
        this.cloud = cloud;
        this.marketplaceId = marketplaceId;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {

        StratusLabComputer stratusLabComputer = (StratusLabComputer) computer;

        PrintStream logger = listener.getLogger();

        logger.println("using StratusLab launcher for " + computer.getName());

        try {

            logger.println("StratusLabLauncher: running stratus-run-instance for "
                    + computer.getName());

            // stratus-run-instance
            ProcessOutput results = runCommandWithResults(cloud.clientLocation,
                    "stratus-run-instance", "--endpoint", cloud.endpoint,
                    "--username", cloud.username, "--password", cloud.password,
                    "--quiet", marketplaceId);
            if (results.rc != 0) {
                throw new StratusLabException(results.error);
            }
            parseForVmidAndIpAddress(results.output);

            logger.println("StratusLabLauncher: allocated VM ID and IP Address are "
                    + vmid + ", " + ip + " for " + computer.getName());

            // stratus-describe-instance
            results = runCommandWithResults(cloud.clientLocation,
                    "stratus-describe-instance", "--endpoint", cloud.endpoint,
                    "--username", cloud.username, "--password", cloud.password,
                    String.valueOf(vmid));
            if (results.rc != 0) {
                throw new StratusLabException(results.error);
            }
            parseForVmStatus(results.output);

        } catch (StratusLabException e) {
            listener.error(e.getMessage());
        }

        // status until running
        // ping
        // ssh into machine

    }

    private void parseForVmidAndIpAddress(String output)
            throws StratusLabException {
        String[] fields = output.split("\\s*,\\s*");
        if (fields.length != 2) {
            throw new StratusLabException(
                    "wrong number of fields from stratus-run-instance: "
                            + fields.length);
        }
        try {
            vmid = Integer.parseInt(fields[0]);
        } catch (IllegalArgumentException e) {
            throw new StratusLabException("extracted VM ID is not an integer: "
                    + fields[0]);
        }
        ip = fields[1];
    }

    private String parseForVmStatus(String output) throws StratusLabException {
        // FIXME: Need real implementation.
        return "running";
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {

        PrintStream logger = listener.getLogger();

        logger.println("StratusLab launcher: no-op for beforeDisconnect on "
                + computer.getName());

    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {

        PrintStream logger = listener.getLogger();

        logger.println("StratusLab launcher: running stratus-kill-instance on "
                + vmid + ", " + computer.getName());

        try {

            // stratus-kill-instance
            ProcessOutput results = runCommandWithResults(cloud.clientLocation,
                    "stratus-kill-instance", "--endpoint", cloud.endpoint,
                    "--username", cloud.username, "--password", cloud.password,
                    "--quiet", String.valueOf(vmid));
            if (results.rc != 0) {
                throw new StratusLabException(results.error);
            }

        } catch (StratusLabException e) {
            listener.error(e.getMessage());
        }

    }

    @Override
    public boolean isLaunchSupported() {
        // TODO: Is this correct? What is a programmatic launch?
        return true;
    }
}
