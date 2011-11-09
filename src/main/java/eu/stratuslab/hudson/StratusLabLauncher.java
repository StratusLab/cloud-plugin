package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.ProcessUtils.runSystemCommandWithResults;
import hudson.model.TaskListener;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.logging.Logger;

import eu.stratuslab.hudson.utils.ProcessUtils.ProcessResult;



public class StratusLabLauncher extends DelegatingComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private final StratusLabProxy.StratusLabParams cloudParams;

    private final int vmid;

    private final String ip;

    public StratusLabLauncher(StratusLabProxy.StratusLabParams cloud, int vmid,
            String ip) {

        super(getDelegate(vmid, ip));
        this.cloudParams = cloud;
        this.vmid = vmid;
        this.ip = ip;
    }

    private static ComputerLauncher getDelegate(int vmid, String ip) {
        String cmd = "ssh -o StrictHostKeyChecking=no root@" + ip
                + " java -jar /tmp/slave.jar";
        return new CommandLauncher(cmd);
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener)
            throws IOException, InterruptedException {

        try {

            // wait until running
            String msg;
            int i = 0;
            while (true) {

                String status = StratusLabProxy.getInstanceStatus(cloudParams,
                        String.valueOf(vmid));

                msg = "status " + status + " for " + vmid + ", " + ip + ", "
                        + computer.getName();
                LOGGER.info(msg);
                listener.getLogger().println(msg);

                if ("Running".equalsIgnoreCase(status)) {
                    break;
                } else {
                    i++;
                    if (i > 100) {
                        throw new StratusLabException(
                                "timeout looking for running state");
                    }
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException consumed) {

                    }
                }
            }

            // ssh into machine
            i = 0;
            while (true) {

                String user = "root@" + ip;
                ProcessResult results = runSystemCommandWithResults("ssh",
                        "-o", "StrictHostKeyChecking=no", user, "/bin/true");

                msg = "ssh exit code is " + results.rc + " with error "
                        + results.error + " for " + vmid + ", " + ip + ", "
                        + computer.getName();

                LOGGER.info(msg);
                listener.getLogger().println(msg);

                if (results.rc == 0) {
                    break;
                } else {
                    i++;
                    if (i > 100) {
                        throw new StratusLabException(
                                "timeout trying to connect by ssh to " + vmid
                                        + ", " + ip + ", " + computer.getName());
                    }
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException consumed) {

                    }
                }
            }

            // install java on the machine
            String user = "root@" + ip;
            ProcessResult results = runSystemCommandWithResults("ssh", "-o",
                    "StrictHostKeyChecking=no", user, "apt-get", "update");

            msg = "update packages " + results.rc + " with error "
                    + results.error + " for " + vmid + ", " + ip + ", "
                    + computer.getName();
            LOGGER.info(msg);
            listener.getLogger().println(msg);

            if (results.rc != 0) {
                throw new StratusLabException(results.error);
            }

            // install java on the machine
            user = "root@" + ip;

            results = runSystemCommandWithResults("ssh", "-o",
                    "StrictHostKeyChecking=no", user, "apt-get", "install",
                    "-y", "--fix-missing", "openjdk-6-jdk");

            msg = "java installation " + results.rc + " with error "
                    + results.error + " for " + vmid + ", " + ip + ", "
                    + computer.getName();
            LOGGER.info(msg);
            listener.getLogger().println(msg);

            if (results.rc != 0) {
                throw new StratusLabException(results.error);
            }

            // copy slave.jar to machine
            user = "root@" + ip;
            results = runSystemCommandWithResults("scp", "-o",
                    "StrictHostKeyChecking=no", "/tmp/slave.jar", user
                            + ":/tmp/slave.jar");

            msg = "copy slave.jar " + results.rc + " with error "
                    + results.error + " for " + vmid + ", " + ip + ", "
                    + computer.getName();
            LOGGER.info(msg);
            listener.getLogger().println(msg);

            if (results.rc != 0) {
                throw new StratusLabException(results.error);
            }

        } catch (StratusLabException e) {
            LOGGER.severe(e.getMessage());
            listener.error(e.getMessage());
        }

        super.launch(computer, listener);

    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {

        LOGGER.info("beforeDisconnect no-op " + computer.getName());

    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {

        String msg = "killing instance " + vmid + ", " + ip + ", "
                + computer.getName();
        LOGGER.info(msg);
        listener.getLogger().println(msg);

        try {

            StratusLabProxy.killInstance(cloudParams, String.valueOf(vmid));

        } catch (StratusLabException e) {
            LOGGER.severe(e.getMessage());
            listener.error(e.getMessage());
        }

    }

    // TODO: Is it necessary to override this?
    @Override
    public boolean isLaunchSupported() {
        // TODO: Is this correct? What is a programmatic launch?
        return true;
    }

}
