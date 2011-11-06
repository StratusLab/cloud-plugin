package eu.stratuslab.hudson;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class ProcessUtils {

    private static final String EMPTY_STRING = "";

    private static final String SPACE = " ";

    private ProcessUtils() {

    }

    public static void runCommand(String clientLocation, String cmd,
            String... options) throws StratusLabException {

        if (clientLocation == null) {
            throw new StratusLabException("client location cannot be null");
        }

        ProcessBuilder pb = new ProcessBuilder();

        String absoluteCmd = cmd;

        //
        // Don't rely on updating the PATH environment variable.
        // Changes in the path appear to be completely ignored
        // by the started process.
        //
        File bin = getBinDirectory(clientLocation);
        if (bin != null) {
            absoluteCmd = (new File(bin, cmd)).getAbsolutePath();
        }
        File lib = getLibDirectory(clientLocation);
        if (lib != null) {
            updatePathEntry(pb.environment(), "PYTHONPATH",
                    lib.getAbsolutePath());
        }

        List<String> cmdElements = new LinkedList<String>();
        cmdElements.add(absoluteCmd);

        StringBuilder fullCmd = new StringBuilder(cmd);
        for (String option : options) {
            cmdElements.add(option);
            fullCmd.append(SPACE);
            fullCmd.append(option);
        }

        pb.command(cmdElements);

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

}
