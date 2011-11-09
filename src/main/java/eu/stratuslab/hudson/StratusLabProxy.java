package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.ProcessUtils.runCommand;
import static eu.stratuslab.hudson.utils.ProcessUtils.runCommandWithResults;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import eu.stratuslab.hudson.utils.ProcessUtils;
import eu.stratuslab.hudson.utils.ProcessUtils.ProcessResult;


/*
 * This class handles the interactions between Hudson and a StratusLab cloud infrastructure.
 */
public class StratusLabProxy {

    public static void testConnection(StratusLabParams params)
            throws StratusLabException {

        runCommand(params.clientLocation, "stratus-describe-instance",
                "--endpoint", params.endpoint, "--username", params.username,
                "--password", params.password);
    }

    public static void testInstallation(StratusLabParams params)
            throws StratusLabException {

        runCommand(params.clientLocation, "stratus-describe-instance", "--help");
    }

    public static String[] startInstance(StratusLabParams params,
            String marketplaceId) throws StratusLabException {

        ProcessResult results = runCommandWithResults(params.clientLocation,
                "stratus-run-instance", "--endpoint", params.endpoint,
                "--username", params.username, "--password", params.password,
                "--quiet", marketplaceId);
        if (results.rc != 0) {
            throw new StratusLabException(results.error);
        }
        return parseForVmidAndIpAddress(results.output);

    }

    public static String getInstanceStatus(StratusLabParams params, String vmid)
            throws StratusLabException {

        ProcessResult results = runCommandWithResults(params.clientLocation,
                "stratus-describe-instance", "--endpoint", params.endpoint,
                "--username", params.username, "--password", params.password,
                String.valueOf(vmid));
        if (results.rc != 0) {
            throw new StratusLabException(results.error);
        }
        return parseForVmStatus(results.output, vmid);

    }

    public static void killInstance(StratusLabParams params, String vmid)
            throws StratusLabException {

        ProcessResult results = runCommandWithResults(params.clientLocation,
                "stratus-kill-instance", "--endpoint", params.endpoint,
                "--username", params.username, "--password", params.password,
                vmid);
        if (results.rc != 0) {
            throw new StratusLabException(results.error);
        }

    }

    public static int getNumberOfDefinedInstances(StratusLabParams params) {

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

    public static String[] parseForVmidAndIpAddress(String output)
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

        return new String[] { String.valueOf(vmid), ip };
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

    public static class StratusLabParams {

        public final String clientLocation;
        public final String endpoint;
        public final String username;
        public final String password;

        public StratusLabParams(String clientLocation, String endpoint,
                String username, String password) {
            this.clientLocation = clientLocation;
            this.endpoint = endpoint;
            this.username = username;
            this.password = password;
        }
    }

}
