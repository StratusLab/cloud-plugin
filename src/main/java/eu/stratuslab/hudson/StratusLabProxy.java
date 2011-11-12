package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.ProcessUtils.runCommand;
import static eu.stratuslab.hudson.utils.ProcessUtils.runCommandWithResults;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;

import eu.stratuslab.hudson.utils.ProcessUtils;
import eu.stratuslab.hudson.utils.ProcessUtils.ProcessResult;

/*
 * This class handles the interactions between Hudson and a StratusLab cloud infrastructure.
 */
public class StratusLabProxy {

    public static void testConnection(CloudParameters params)
            throws StratusLabException {

        runCommand(params.clientLocation, "stratus-describe-instance",
                "--endpoint", params.endpoint, "--username", params.username,
                "--password", params.password);
    }

    public static void testInstallation(CloudParameters params)
            throws StratusLabException {

        runCommand(params.clientLocation, "stratus-describe-instance", "--help");
    }

    public static InstanceInfo startInstance(CloudParameters params,
            String marketplaceId) throws StratusLabException {

        ProcessResult results = runCommandWithResults(params.clientLocation,
                "stratus-run-instance", "--endpoint", params.endpoint,
                "--username", params.username, "--password", params.password,
                "--key", params.sshPublicKey, "--quiet", marketplaceId);
        if (results.rc != 0) {
            throw new StratusLabException(results.error);
        }
        return parseForVmidAndIpAddress(results.output);

    }

    public static String getInstanceStatus(CloudParameters params, String vmid)
            throws StratusLabException {

        ProcessResult results = runCommandWithResults(params.clientLocation,
                "stratus-describe-instance", "--endpoint", params.endpoint,
                "--username", params.username, "--password", params.password,
                vmid);
        if (results.rc != 0) {
            throw new StratusLabException(results.error);
        }
        return parseForVmStatus(results.output, vmid);

    }

    public static void killInstance(CloudParameters params, String vmid)
            throws StratusLabException {

        ProcessResult results = runCommandWithResults(params.clientLocation,
                "stratus-kill-instance", "--endpoint", params.endpoint,
                "--username", params.username, "--password", params.password,
                vmid);
        if (results.rc != 0) {
            throw new StratusLabException(results.error);
        }

    }

    public static int getNumberOfDefinedInstances(CloudParameters params) {

        int definedInstances = Integer.MAX_VALUE;

        try {
            ProcessResult results = runCommandWithResults(
                    params.clientLocation, "stratus-describe-instance",
                    "--endpoint", params.endpoint, "--username",
                    params.username, "--password", params.password);

            if (results.rc == 0) {
                String lines[] = results.output.split("\\r?\\n");
                definedInstances = lines.length - 1;
            }
        } catch (StratusLabException e) {

        }

        return definedInstances;
    }

    public static InstanceInfo parseForVmidAndIpAddress(String output)
            throws StratusLabException {
        String[] fields = output.split("\\s*,\\s*");
        if (fields.length != 2) {
            throw new StratusLabException(
                    "wrong number of fields from stratus-run-instance: "
                            + fields.length);
        }
        int vmid = 0;
        try {
            vmid = Integer.parseInt(fields[0]);
        } catch (IllegalArgumentException e) {
            throw new StratusLabException("extracted VM ID is not an integer: "
                    + fields[0]);
        }
        String ip = fields[1].trim();

        return new InstanceInfo(vmid, ip);
    }

    public static String parseForVmStatus(String output, String vmid)
            throws StratusLabException {

        String vmidAsString = String.valueOf(vmid);

        BufferedReader reader = new BufferedReader(new StringReader(output));

        try {

            String s;
            while ((s = reader.readLine()) != null) {
                String[] fields = s.split("\\s+");
                if (fields.length > 2) {
                    if (vmidAsString.equals(fields[0])) {
                        return fields[1];
                    }
                }
            }

        } catch (IOException consumed) {

        } finally {
            ProcessUtils.closeReliably(reader);
        }

        return "unknown";
    }

    @SuppressWarnings("serial")
    public static class InstanceInfo implements Serializable {

        public final int vmid;
        public final String ip;

        public InstanceInfo(int vmid, String ip) {
            this.vmid = vmid;
            this.ip = ip;
        }

        public String toString() {
            return String.format("%d, %s", vmid, ip);
        }
    }

}
