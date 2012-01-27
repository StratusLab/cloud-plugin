/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.CloudParameterUtils.isEmptyStringOrNull;
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

    private final long pollIntervalMillis;

    private final long timeoutMillis;

    private final CloudParameters cloudParams;

    private final SlaveTemplate template;

    private final InstanceInfo info;

    public StratusLabLauncher(CloudParameters cloudParams,
            SlaveTemplate template, InstanceInfo info) {

        super(getDelegate(cloudParams, template, info));
        this.cloudParams = cloudParams;
        this.template = template;
        this.info = info;

        pollIntervalMillis = template.pollInterval * 1000;
        timeoutMillis = template.timeout * 60 * 1000;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener)
            throws IOException, InterruptedException {

        try {

            listener.getLogger().println("waiting for running state");
            waitForRunningStatus(listener, pollIntervalMillis, timeoutMillis);

            waitForSuccessfulSshConnection(listener, pollIntervalMillis,
                    timeoutMillis);

            if (copyInitScript(listener)) {
                runInitScript(listener);
            }

            copySlaveJar(listener);

        } catch (StratusLabException e) {
            LOGGER.severe("launch failed for " + computer.getDisplayName());
            LOGGER.severe(e.getMessage());
            listener.fatalError(e.getMessage());
            listener.fatalError("launch failed");
            throw new IOException(e);
        }

        super.launch(computer, listener);

    }

    @Override
    public boolean isLaunchSupported() {
        return true;
    }

    private void waitForSuccessfulSshConnection(TaskListener listener,
            long sleep, long timeout) throws StratusLabException {

        String fmt, msg;
        long waitTime = 0L;

        fmt = "%s: attempting to ping instance via ssh";
        msg = String.format(fmt, info.toString());
        listener.getLogger().println(msg);

        for (; waitTime < timeout; waitTime += sleep) {

            try {

                pingInstanceViaSsh();
                fmt = "%s: successful ssh ping";
                msg = String.format(fmt, info.toString());
                listener.getLogger().println(msg);

                break;

            } catch (IOException consumed) {

                fmt = "%s: ssh ping failed";
                msg = String.format(fmt, info.toString());
                listener.getLogger().println(msg);

                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {

                }
            }

        }

        if (waitTime >= timeout) {
            fmt = "%s: timeout waiting for running state";
            msg = String.format(fmt, info.toString());
            listener.fatalError(msg);
            throw new StratusLabException(msg);
        } else {
            fmt = "%s: ping via ssh was successful";
            msg = String.format(fmt, info.toString());
            listener.getLogger().println(msg);
        }

    }

    private void waitForRunningStatus(TaskListener listener, long sleep,
            long timeout) throws StratusLabException {

        String fmt, msg;
        long waitTime = 0L;

        fmt = "%s: wait for running state";
        msg = String.format(fmt, info.toString());
        listener.getLogger().println(msg);

        for (; waitTime < timeout; waitTime += sleep) {

            String status = StratusLabProxy.getInstanceStatus(cloudParams,
                    String.valueOf(info.vmid));

            fmt = "%s: %s";
            msg = String.format(fmt, info.toString(), status);
            listener.getLogger().println(msg);

            if ("Running".equalsIgnoreCase(status)) {
                break;
            } else if ("Done".equalsIgnoreCase(status)
                    || "Failed".equalsIgnoreCase(status)) {
                fmt = "%s: unexpected machine status %s";
                msg = String.format(fmt, info.toString(), status);
                listener.fatalError(msg);
                throw new StratusLabException(msg);
            } else {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException consumed) {

                }
            }

        }

        if (waitTime >= timeout) {
            fmt = "%s: timeout waiting for running state";
            msg = String.format(fmt, info.toString());
            listener.fatalError(msg);
            throw new StratusLabException(msg);
        } else {
            fmt = "%s: instance is running";
            msg = String.format(fmt, info.toString());
            listener.getLogger().println(msg);
        }

    }

    private void copySlaveJar(TaskListener listener) throws StratusLabException {

        String fmt = "copying slave.jar to %s on instance";
        listener.getLogger().println(String.format(fmt, template.remoteFS));

        Connection connection = null;
        try {

            connection = openSshConnection();

            SCPClient scp = connection.createSCPClient();
            scp.put(Hudson.getInstance().getJnlpJars("slave.jar").readFully(),
                    "slave.jar", template.remoteFS);

        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            listener.fatalError(e.getMessage());
            throw new StratusLabException(e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        listener.getLogger().println("copied slave.jar to instance");
    }

    private boolean copyInitScript(TaskListener listener)
            throws StratusLabException {

        if (isEmptyStringOrNull(template.initScriptDir)
                || isEmptyStringOrNull(template.initScriptName)
                || isEmptyStringOrNull(template.initScript)) {

            listener.getLogger().println("no init script to copy");
            return false;
        }

        String fmt = "copying init script to %s with name %s";
        String msg = String.format(fmt, template.initScriptDir,
                template.initScriptName);
        listener.getLogger().println(msg);

        Connection connection = null;
        try {

            connection = openSshConnection();

            SCPClient scp = connection.createSCPClient();
            scp.put(template.initScript.getBytes(), template.initScriptName,
                    template.initScriptDir, "0755");

        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            listener.fatalError(e.getMessage());
            throw new StratusLabException(e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        listener.getLogger().println("copied init script");

        return true;
    }

    private void runInitScript(TaskListener listener)
            throws StratusLabException {

        listener.getLogger().println("running init script");

        Connection connection = null;
        try {

            connection = openSshConnection();

            Session session = connection.openSession();
            session.requestDumbPTY();
            session.execCommand(template.initScriptDir
                    + template.initScriptName);

            IOUtils.copy(session.getStdout(), listener.getLogger());
            IOUtils.copy(session.getStderr(), listener.getLogger());

            int rc = session.getExitStatus();
            if (rc != 0) {
                String fmt = "error running %s%s on instance; rc is %d";
                listener.error(String.format(fmt, template.initScriptDir,
                        template.initScriptName, rc));
            }

        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            listener.fatalError(e.getMessage());
            throw new StratusLabException(e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        listener.getLogger().println("executed init script");

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

    private static ComputerLauncher getDelegate(CloudParameters cloudParams,
            SlaveTemplate template, InstanceInfo info) {

        String fmt = "ssh -o StrictHostKeyChecking=no -i %s %s@%s java %s -jar %sslave.jar";

        String javaopts = "";
        if (template.jvmOpts != null && !"".equals(template.jvmOpts.trim())) {
            javaopts = template.jvmOpts.trim();
        }

        String cmd = String.format(fmt, cloudParams.sshPrivateKey,
                template.remoteUser, info.ip, javaopts, template.remoteFS);

        return new CommandLauncher(cmd);
    }

}
