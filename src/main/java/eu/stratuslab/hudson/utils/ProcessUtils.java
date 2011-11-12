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
package eu.stratuslab.hudson.utils;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import eu.stratuslab.hudson.StratusLabCloud;
import eu.stratuslab.hudson.StratusLabException;

public final class ProcessUtils {

    private static final String EMPTY_STRING = "";

    private static final String SPACE = " ";

    private static final int BUFFER_SIZE = 2048;

    private ProcessUtils() {

    }

    public static void runCommand(String clientLocation, String cmd,
            String... options) throws StratusLabException {

        ProcessResult results = runCommandWithResults(clientLocation, cmd,
                options);

        if (results.rc != 0) {
            throw new StratusLabException(
                    "command returned non-zero exit code (" + results.rc + "; "
                            + results.cmd + ")");
        }

    }

    public static ProcessResult runCommandWithResults(String clientLocation,
            String cmd, String... options) throws StratusLabException {

        Logger logger = Logger.getLogger(StratusLabCloud.class.getName());

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
            return new ProcessResult(fullCmd.toString(), pb.start());
        } catch (IOException e) {
            logger.severe(e.getMessage());
            throw new StratusLabException(e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

    }

    public static ProcessResult runSystemCommandWithResults(String cmd,
            String... options) throws StratusLabException {

        Logger logger = Logger.getLogger(StratusLabCloud.class.getName());

        ProcessBuilder pb = new ProcessBuilder();

        List<String> cmdElements = new LinkedList<String>();
        cmdElements.add(cmd);

        StringBuilder fullCmd = new StringBuilder(cmd);
        for (String option : options) {
            cmdElements.add(option);
            fullCmd.append(SPACE);
            fullCmd.append(option);
        }

        pb.command(cmdElements);

        Process process = null;
        try {
            return new ProcessResult(fullCmd.toString(), pb.start());
        } catch (IOException e) {
            logger.severe(e.getMessage());
            throw new StratusLabException(e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

    }

    public static String slurp(InputStream is) {

        Logger logger = Logger.getLogger(StratusLabCloud.class.getName());

        InputStream bis = new BufferedInputStream(is);

        final char[] buffer = new char[BUFFER_SIZE];

        StringBuilder sb = new StringBuilder(BUFFER_SIZE);

        Reader reader = null;
        try {

            reader = new InputStreamReader(bis);

            int nbytes = reader.read(buffer, 0, BUFFER_SIZE);
            while (nbytes != -1) {
                sb.append(buffer, 0, nbytes);
                nbytes = reader.read(buffer, 0, BUFFER_SIZE);
            }

        } catch (IOException consumed) {
            logger.severe(consumed.getMessage());
        } finally {
            closeReliably(reader);
        }

        return sb.toString();
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

    public static class ProcessResult {

        private static final int POOL_SIZE = 8;
        private static final int POOL_MAX_SIZE = POOL_SIZE * 2;
        private static final long KEEP_ALIVE_TIME = 1; // 1 minute
        private static final BlockingQueue<Runnable> QUEUE = new LinkedBlockingQueue<Runnable>();
        private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
                POOL_SIZE, POOL_MAX_SIZE, KEEP_ALIVE_TIME, TimeUnit.MINUTES,
                QUEUE);

        public final String cmd;

        public final int rc;

        public final String output;

        public final String error;

        public ProcessResult(String cmd, Process process) {

            this.cmd = cmd;

            Logger logger = Logger.getLogger(StratusLabCloud.class.getName());

            Future<String> futureOutput = asyncSlurp(process.getInputStream());
            Future<String> futureError = asyncSlurp(process.getErrorStream());

            String o = EMPTY_STRING;
            try {
                logger.fine("waiting for stdout: " + cmd);
                o = futureOutput.get();
            } catch (InterruptedException consumed) {
                logger.severe(consumed.getMessage());
            } catch (ExecutionException consumed) {
                logger.severe(consumed.getMessage());
            }
            output = o;

            String e = EMPTY_STRING;
            try {
                logger.fine("waiting for stderr: " + cmd);
                e = futureError.get();
            } catch (InterruptedException consumed) {
                logger.severe(consumed.getMessage());
            } catch (ExecutionException consumed) {
                logger.severe(consumed.getMessage());
            }
            error = e;

            logger.fine("waiting for process: " + cmd);

            int r = -1;
            try {
                r = process.waitFor();
            } catch (InterruptedException consumed) {
                logger.severe(consumed.getMessage());
            }
            rc = r;

            logger.fine("finished process: " + cmd + " " + rc);
        }

        public static Future<String> asyncSlurp(InputStream is) {
            return EXECUTOR.submit(new SlurpCallable(is));
        }

        public static class SlurpCallable implements Callable<String> {

            private final InputStream is;

            public SlurpCallable(InputStream is) {
                this.is = is;
            }

            public String call() throws Exception {
                return slurp(is);
            }
        }

    }

    public static void closeReliably(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException consumed) {
            }
        }
    }

}
