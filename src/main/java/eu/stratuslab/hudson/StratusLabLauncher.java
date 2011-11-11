package eu.stratuslab.hudson;

import hudson.model.TaskListener;
import hudson.model.Hudson;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.IOUtils;

import java.io.IOException;
import java.util.logging.Logger;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.Session;

import eu.stratuslab.hudson.StratusLabProxy.InstanceInfo;

public class StratusLabLauncher extends DelegatingComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private final CloudParameters cloudParams;

    private final SlaveTemplate template;

    private final InstanceInfo info;

    public StratusLabLauncher(CloudParameters cloudParams,
            SlaveTemplate template, InstanceInfo info) {

        super(getDelegate(cloudParams, template, info));
        this.cloudParams = cloudParams;
        this.template = template;
        this.info = info;
    }

    private static ComputerLauncher getDelegate(CloudParameters cloudParams,
            SlaveTemplate template, InstanceInfo info) {

        String fmt = "ssh -o StrictHostKeyChecking=no %s@%s java %s -jar %sslave.jar";

        String javaopts = "";
        if (template.jvmOpts != null && !"".equals(template.jvmOpts.trim())) {
            javaopts = template.jvmOpts.trim();
        }

        String cmd = String.format(fmt, template.remoteUser, info.ip, javaopts,
                template.remoteFS);

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

                msg = "status %s for %s";
                LOGGER.info(String.format(msg, status, info.toString()));
                listener.getLogger().println(
                        String.format(msg, status, info.toString()));

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

                try {
                    pingInstanceViaSsh();
                    msg = "successful ssh ping of instance";
                    LOGGER.info(msg);
                    listener.getLogger().println(msg);

                    break;

                } catch (IOException e) {

                    i++;
                    if (i > 100) {
                        String fmt = "timeout trying to connect via ssh to %s";
                        throw new StratusLabException(String.format(fmt,
                                info.toString()));
                    }
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException consumed) {

                    }

                }

            }

            if (copyInitScript(listener)) {
                runInitScript(listener);
            }

            copySlaveJar(listener);

        } catch (StratusLabException e) {
            LOGGER.severe(e.getMessage());
            listener.error(e.getMessage());
        }

        super.launch(computer, listener);

    }

    private void copySlaveJar(TaskListener listener) {

        String fmt = "copying slave.jar to %s on instance";
        LOGGER.info(String.format(fmt, template.remoteFS));
        listener.getLogger().println(String.format(fmt, template.remoteFS));

        Connection connection = null;
        try {

            connection = openSshConnection();

            SCPClient scp = connection.createSCPClient();
            scp.put(Hudson.getInstance().getJnlpJars("slave.jar").readFully(),
                    "slave.jar", template.remoteFS);

        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace(listener.getLogger());
            listener.error(e.getMessage());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        LOGGER.info("copied slave.jar to instance");
        listener.getLogger().println("copied slave.jar to instance");

    }

    private boolean copyInitScript(TaskListener listener) {

        if (template.initScript == null
                || "".equals(template.initScript.trim())) {

            LOGGER.info("no init script to copy");
            listener.getLogger().println("no init script to copy");
            return false;
        }

        String fmt = "copying init script to %s on instance";
        LOGGER.info(String.format(fmt, "/tmp"));
        listener.getLogger().println(String.format(fmt, "/tmp"));

        Connection connection = null;
        try {

            connection = openSshConnection();

            SCPClient scp = connection.createSCPClient();
            scp.put(template.initScript.getBytes(), "init.sh", "/tmp", "0755");

        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace(listener.getLogger());
            listener.error(e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        LOGGER.info("copied init script to instance");
        listener.getLogger().println("copied init script to instance");

        return true;
    }

    private void runInitScript(TaskListener listener) {

        LOGGER.info("running init script on instance");
        listener.getLogger().println("running init script on instance");

        Connection connection = null;
        try {

            connection = openSshConnection();

            Session session = connection.openSession();
            session.requestDumbPTY();
            session.execCommand("/tmp/" + "init.sh");

            IOUtils.copy(session.getStdout(), listener.getLogger());
            IOUtils.copy(session.getStderr(), listener.getLogger());

            int rc = session.getExitStatus();
            if (rc != 0) {
                String fmt = "error running init.sh on instance; rc is %d";
                LOGGER.severe(String.format(fmt, rc));
                listener.error(String.format(fmt, rc));
            }

        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace(listener.getLogger());
            listener.error(e.getMessage());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        LOGGER.info("executed init script on instance");
        listener.getLogger().println("executed init script on instance");

    }

    private void pingInstanceViaSsh() throws IOException {

        Connection connection = null;
        try {

            connection = openSshConnection();
            connection.ping();

        } catch (IOException e) {

            throw e;

        } finally {
            if (connection != null) {
                connection.close();
            }
        }

    }

    private Connection openSshConnection() throws IOException {

        Connection connection = new Connection(info.ip, template.sshPort);
        connection.connect();

        connection.authenticateWithPublicKey(template.remoteUser,
                cloudParams.getSshPrivateKeyData(),
                cloudParams.sshPrivateKeyPassword);

        return connection;
    }

    // TODO: Is it necessary to override this?
    @Override
    public boolean isLaunchSupported() {
        // TODO: Is this correct? What is a programmatic launch?
        return true;
    }

}
