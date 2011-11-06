package eu.stratuslab.hudson;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class StratusLabCloud extends AbstractCloudImpl {

    private static final String NODE_PREFIX = "StratusLab:";

    private static final Pattern MACHINE_ID_PATTERN = Pattern.compile("^"
            + NODE_PREFIX + "([a-zA-Z0-9_-]{27})$");

    private static final Pattern ENDPOINT_PATTERN = Pattern
            .compile("^(?(\\w+)://)?([\\w\\d\\-\\.]+)(?:(\\d+))?$");

    private static final long DEMAND_DELAY = 60 * 1000L; // 1 minute

    private static final long IDLE_DELAY = 60 * 1000L; // 1 minute

    private static final String CLOUD_NAME = "StratusLab Cloud";

    private static final String EMPTY_STRING = "";

    private static final String SPACE = " ";

    public final String clientLocation;

    public final String endpoint;

    public final String username;

    public final String password;

    public final String instanceLimit;

    // public final List<SlaveTemplate> templates;

    @DataBoundConstructor
    public StratusLabCloud(String clientLocation, String endpoint,
            String username, String password, String instanceLimit) {
        super(CLOUD_NAME, instanceLimit);

        this.clientLocation = clientLocation;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.instanceLimit = instanceLimit;
        // this.templates = copyToImmutableList(templates);
    }

    List<SlaveTemplate> copyToImmutableList(List<SlaveTemplate> templates) {
        ArrayList<SlaveTemplate> list = new ArrayList<SlaveTemplate>();
        list.addAll(templates);
        list.trimToSize();
        return Collections.unmodifiableList(list);
    }

    @SuppressWarnings("unchecked")
    public Descriptor<Cloud> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public boolean canProvision(Label label) {
        return isMachineId(label);
    }

    public Collection<PlannedNode> provision(Label label, int excessWorkload) {

        String id = extractMachineId(label);

        Collection<PlannedNode> nodes = new LinkedList<PlannedNode>();

        for (int i = 0; i < excessWorkload; i++) {
            PlannedNode node = new PlannedNode(id, getNodeCreator(id), 1);
            nodes.add(node);
        }

        return nodes;
    }

    public static boolean isMachineId(Label label) {
        return isMachineId(label.getName());
    }

    public static boolean isMachineId(String machine_uri) {
        Matcher matcher = MACHINE_ID_PATTERN.matcher(machine_uri);
        return matcher.matches();
    }

    public static String extractMachineId(Label label) {
        String id = EMPTY_STRING;

        String name = label.getName();
        Matcher matcher = MACHINE_ID_PATTERN.matcher(name);
        if (matcher.matches()) {
            id = matcher.group(1);
        }
        return id;
    }

    public Future<Node> getNodeCreator(String id) {
        return new FutureTask<Node>(new VmCreator(id, endpoint, username,
                password));
    }

    public static class VmCreator implements Callable<Node> {

        private final String id;

        // private final String endpoint;

        // private final String username;

        // private final String password;

        public VmCreator(String id, String endpoint, String username,
                String password) {
            this.id = id;
            // this.endpoint = endpoint;
            // this.username = username;
            // this.password = password;
        }

        public Node call() throws IOException, Descriptor.FormException {
            ComputerLauncher launcher = null;
            RetentionStrategy.Demand strategy = new RetentionStrategy.Demand(
                    DEMAND_DELAY, IDLE_DELAY);

            List<? extends NodeProperty<?>> nodeProperties = new LinkedList<NodeProperty<Node>>();

            return new DumbSlave(id, id, "/var/lib/hudson", "1",
                    Node.Mode.NORMAL, id, launcher, strategy, nodeProperties);

        }

    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return CLOUD_NAME;
        }

        public FormValidation doCheckClientLocation(
                @QueryParameter String clientLocation) {
            return constructValidationResult(isValidClientLocation(clientLocation));
        }

        public FormValidation doCheckEndpoint(@QueryParameter String endpoint) {
            return constructValidationResult(isValidEndpoint(endpoint));
        }

        public FormValidation doCheckUsername(@QueryParameter String username) {
            String msg = "";
            if (isEmptyStringOrNull(username)) {
                msg = "username must be defined";
            }
            return constructValidationResult(msg);
        }

        public FormValidation doCheckPassword(@QueryParameter String password) {
            String msg = "";
            if (isEmptyStringOrNull(password)) {
                msg = "password must be defined";
            }
            return constructValidationResult(msg);
        }

        public FormValidation doCheckInstanceLimit(
                @QueryParameter String instanceLimit) {
            String msg = "";
            if (!isPositiveInteger(instanceLimit)
                    && !EMPTY_STRING.equals(instanceLimit)) {
                msg = "instance cap must be a valid positive integer or blank";
            }
            return constructValidationResult(msg);
        }

        public FormValidation doTestConnection(
                @QueryParameter String clientLocation,
                @QueryParameter String endpoint,
                @QueryParameter String username, @QueryParameter String password) {

            String msg = EMPTY_STRING; // OK

            try {
                runCommand("stratus-describe-instance", "--endpoint", endpoint,
                        "--username", username, "--password", password);
            } catch (StratusLabException e) {
                msg = e.getMessage();
            }

            return constructValidationResult(msg);
        }

        public static String isValidEndpoint(String endpoint) {
            try {
                getEndpoint(endpoint);
            } catch (StratusLabException e) {
                return e.getMessage();
            }
            return EMPTY_STRING; // OK
        }

        public static String getEndpoint(String endpoint)
                throws StratusLabException {

            if (isEmptyStringOrNull(endpoint)) {
                throw new StratusLabException("endpoint cannot be empty");
            }

            Matcher matcher = ENDPOINT_PATTERN.matcher(endpoint);
            if (!matcher.matches()) {
                throw new StratusLabException(
                        "endpoint does not match pattern: "
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
                runCommand(clientLocation, "stratus-describe-instance",
                        "--help");
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

}
