package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.ProcessUtils.runSystemCommandWithResults;
import hudson.model.TaskListener;
import hudson.model.Hudson;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.logging.Logger;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.ServerHostKeyVerifier;

import eu.stratuslab.hudson.StratusLabProxy.InstanceInfo;
import eu.stratuslab.hudson.utils.ProcessUtils.ProcessResult;

public class StratusLabLauncher extends DelegatingComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private final CloudParameters cloudParams;

    private final InstanceInfo info;

    public StratusLabLauncher(CloudParameters cloudParams, InstanceInfo info) {

        super(getDelegate(cloudParams, info));
        this.cloudParams = cloudParams;
        this.info = info;
    }

    private static ComputerLauncher getDelegate(CloudParameters cloudParams,
            InstanceInfo info) {
        String cmd = "ssh -o StrictHostKeyChecking=no " + cloudParams.username
                + "@" + info.ip + " java -jar /tmp/slave.jar";
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
                        String.valueOf(info.vmid));

                msg = "status " + status + " for " + info + " "
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

                String user = cloudParams.username + "@" + info.ip;
                ProcessResult results = runSystemCommandWithResults("ssh",
                        "-o", "StrictHostKeyChecking=no", user, "/bin/true");

                msg = "ssh exit code is " + results.rc + " with error "
                        + results.error + " for " + info + ", "
                        + computer.getName();

                LOGGER.info(msg);
                listener.getLogger().println(msg);

                if (results.rc == 0) {
                    break;
                } else {
                    i++;
                    if (i > 100) {
                        throw new StratusLabException(
                                "timeout trying to connect by ssh to " + info
                                        + ", " + computer.getName());
                    }
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException consumed) {

                    }
                }
            }

            // install java on the machine
            String user = cloudParams.username + "@" + info.ip;
            ProcessResult results = runSystemCommandWithResults("ssh", "-o",
                    "StrictHostKeyChecking=no", user, "apt-get", "update");

            msg = "update packages " + results.rc + " with error "
                    + results.error + " for " + info + ", "
                    + computer.getName();
            LOGGER.info(msg);
            listener.getLogger().println(msg);

            if (results.rc != 0) {
                throw new StratusLabException(results.error);
            }

            // install java on the machine
            user = cloudParams.username + "@" + info.ip;

            results = runSystemCommandWithResults("ssh", "-o",
                    "StrictHostKeyChecking=no", user, "apt-get", "install",
                    "-y", "--fix-missing", "openjdk-6-jdk");

            msg = "java installation " + results.rc + " with error "
                    + results.error + " for " + info + ", "
                    + computer.getName();
            LOGGER.info(msg);
            listener.getLogger().println(msg);

            if (results.rc != 0) {
                throw new StratusLabException(results.error);
            }

            // copy slave.jar to instance
            LOGGER.info("copying slave.jar to instance");
            listener.getLogger().println("copying slave.jar to instance");

            Connection connection = null;
            try {
                connection = sshConnectionToInstance();
                SCPClient scp = connection.createSCPClient();
                scp.put(Hudson.getInstance().getJnlpJars("slave.jar")
                        .readFully(), "slave.jar", "/tmp");
            } catch (StratusLabException e) {
                throw e;
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
            LOGGER.info("copied slave to machine");
            listener.getLogger().println("copied slave to machine");

            // copy slave.jar to machine
            // user = cloudParams.username + "@" + info.ip;
            // results = runSystemCommandWithResults("scp", "-o",
            // "StrictHostKeyChecking=no", "/tmp/slave.jar", user
            // + ":/tmp/slave.jar");
            //
            // msg = "copy slave.jar " + results.rc + " with error "
            // + results.error + " for " + info + ", "
            // + computer.getName();
            // LOGGER.info(msg);
            // listener.getLogger().println(msg);
            //
            // if (results.rc != 0) {
            // throw new StratusLabException(results.error);
            // }

        } catch (StratusLabException e) {
            LOGGER.severe(e.getMessage());
            listener.error(e.getMessage());
        }

        super.launch(computer, listener);

    }

    private Connection sshConnectionToInstance() throws StratusLabException {

        String host = info.ip;

        // FIXME: This should be taken from slave template!
        int port = 22;
        Connection conn = new Connection(host, port);

        try {

            conn.connect(new ServerHostKeyVerifier() {
                public boolean verifyServerHostKey(String hostname, int port,
                        String server, byte[] serverHostKey) throws Exception {
                    return true;
                }
            });

        } catch (IOException e) {
            throw new StratusLabException(e.getMessage());
        }

        return conn;
    }

    // TODO: Is it necessary to override this?
    @Override
    public boolean isLaunchSupported() {
        // TODO: Is this correct? What is a programmatic launch?
        return true;
    }

}
