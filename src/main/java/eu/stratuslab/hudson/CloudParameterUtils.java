package eu.stratuslab.hudson;

import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CloudParameterUtils {

    private static final Pattern ENDPOINT_PATTERN = Pattern
            .compile("^(?(\\w+)://)?([\\w\\d\\-\\.]+)(?:(\\d+))?$");

    private static final String EMPTY_STRING = "";

    private static final String SPACE = " ";

    private CloudParameterUtils() {

    }

    public static FormValidation validateEndpoint(String endpoint) {
        try {
            getEndpoint(endpoint);
        } catch (StratusLabException e) {
            return FormValidation.error(e.getMessage());
        }
        return FormValidation.ok();
    }

    public static String getEndpoint(String endpoint)
            throws StratusLabException {

        if (isEmptyStringOrNull(endpoint)) {
            throw new StratusLabException("endpoint cannot be empty");
        }

        Matcher matcher = ENDPOINT_PATTERN.matcher(endpoint);
        if (!matcher.matches()) {
            throw new StratusLabException("endpoint does not match pattern: "
                    + ENDPOINT_PATTERN.toString());
        }
        String scheme = matcher.group(1);
        String authority = matcher.group(2);
        String portString = matcher.group(3);

        if (EMPTY_STRING.equals(scheme)) {
            scheme = "https";
        }
        if (EMPTY_STRING.equals(portString)) {
            portString = "2634";
        }

        int port = 0;
        try {
            port = Integer.parseInt(portString);
        } catch (IllegalArgumentException e) {
            throw new StratusLabException(e.getMessage());
        }

        URL url = null;
        try {
            url = new URL(scheme, authority, port, EMPTY_STRING);
        } catch (MalformedURLException e) {
            throw new StratusLabException(e.getMessage());
        }

        return url.toString();
    }

    public static boolean isEmptyStringOrNull(String s) {
        return (EMPTY_STRING.equals(s) || s == null);
    }

    public static boolean isPositiveInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String isValidClientLocation(String clientLocation) {

        String msg = EMPTY_STRING; // OK

        try {
            runCommand(clientLocation, "stratus-describe-instance", "--help");
        } catch (StratusLabException e) {
            msg = e.getMessage();
        }

        return msg;
    }

    private static void runCommand(String clientLocation, String cmd,
            String... options) throws StratusLabException {

        if (clientLocation == null) {
            throw new StratusLabException("client location cannot be null");
        }

        Map<String, String> env = new HashMap<String, String>();
        File bin = getBinDirectory(clientLocation);
        if (bin != null) {
            env.put("PATH", bin.getAbsolutePath());
        }
        File lib = getLibDirectory(clientLocation);
        if (lib != null) {
            env.put("PYTHONPATH", lib.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder();

        Map<String, String> environment = pb.environment();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            updatePathEntry(environment, entry.getKey(), entry.getValue());
        }

        List<String> cmdElements = new LinkedList<String>();
        StringBuilder fullCmd = new StringBuilder(cmd);
        cmdElements.add(cmd);
        for (String option : options) {
            cmdElements.add(option);
            fullCmd.append(SPACE);
            fullCmd.append(option);
        }

        Process process = null;
        try {
            process = pb.start();
            int rc = process.waitFor();
            if (rc != 0) {
                throw new StratusLabException(
                        "command returned non-zero exit code (" + rc + "; "
                                + fullCmd + ")");
            }
        } catch (InterruptedException e) {
            throw new StratusLabException(e.getMessage());
        } catch (IOException e) {
            throw new StratusLabException(e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

    }

    public static File getRootDirectory(String clientLocation)
            throws StratusLabException {

        File root = null;

        if (!EMPTY_STRING.equals(clientLocation)) {
            root = (new File(clientLocation)).getAbsoluteFile();
            checkDirectory(root);
        }

        return root;
    }

    public static File getBinDirectory(String clientLocation)
            throws StratusLabException {

        File bin = null;

        File root = getRootDirectory(clientLocation);
        if (root != null) {
            bin = new File(root, "bin");
            checkDirectory(bin);
        }

        return bin;
    }

    public static File getLibDirectory(String clientLocation)
            throws StratusLabException {

        File lib = null;

        File root = getRootDirectory(clientLocation);
        if (root != null) {
            lib = new File(root, "lib");
            lib = new File(lib, "stratuslab");
            lib = new File(lib, "python");
            checkDirectory(lib);
        }

        return lib;
    }

    public static void checkDirectory(File directory)
            throws StratusLabException {
        if (!directory.isDirectory()) {
            throw new StratusLabException("directory ("
                    + directory.getAbsolutePath()
                    + ") doesn't exist or isn't a directory");
        }
    }

    public static void updatePathEntry(Map<String, String> environment,
            String key, String value) {

        String oldValue = environment.get(key);
        StringBuilder newValue = new StringBuilder(value);
        if (oldValue != null && !EMPTY_STRING.equals(oldValue)) {
            newValue.append(System.getProperty("path.separator"));
            newValue.append(oldValue);
        }
        environment.put(key, newValue.toString());

    }

    public static FormValidation constructValidationResult(String msg) {
        if (EMPTY_STRING.equals(msg)) {
            return FormValidation.ok();
        } else if (msg == null) {
            return FormValidation.error("<no message>");
        } else {
            return FormValidation.error(msg);
        }
    }

}
