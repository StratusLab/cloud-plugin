package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.createLabelList;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateExecutors;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateIdleMinutes;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateInitScript;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateInitScriptDir;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateInitScriptName;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateLabelString;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateMarketplaceId;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validatePollInterval;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateRemoteFS;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateSshPort;
import static eu.stratuslab.hudson.utils.SlaveParameterUtils.validateTimeout;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.FormValidation;

import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class SlaveTemplate implements Describable<SlaveTemplate> {

    public enum InstanceTypes {
        T1_MICRO("t1.micro", 1, 128, 512), //
        M1_SMALL("m1.small", 1, 128, 1024), //
        M1_LARGE("m1.large", 2, 512, 1024), //
        M1_XLARGE("m1.xlarge", 2, 1024, 1024), //
        C1_MEDIUM("c1.medium", 1, 256, 1024), //
        C1_XLARGE("c1.xlarge", 4, 2048, 2048);

        private final String tag;
        private final int cpu;
        private final int ramMB;
        private final int swapMB;
        private final String label;

        private InstanceTypes(String tag, int cpu, int ramMB, int swapMB) {
            this.tag = tag;
            this.cpu = cpu;
            this.ramMB = ramMB;
            this.swapMB = swapMB;
            label = createLabel();
        }

        public String tag() {
            return tag;
        }

        public String label() {
            return label;
        }

        private String createLabel() {
            StringBuilder sb = new StringBuilder();
            sb.append(tag);
            sb.append(" (");
            sb.append(cpu);
            sb.append(" CPU, ");
            sb.append(ramMB);
            sb.append("MB RAM, ");
            sb.append(swapMB);
            sb.append("MB SWAP)");
            return sb.toString();
        }

    }

    public final String marketplaceId;
    public final InstanceTypes instanceType;
    public final String description;
    public final String remoteFS;
    public final String remoteUser;
    public final String labelString;
    public final boolean initScriptFlag;
    public final String initScriptDir;
    public final String initScriptName;
    public final String initScript;
    public final int executors;
    public final String jvmOpts;
    public final int sshPort;
    public final int idleMinutes;
    public final long pollInterval;
    public final long timeout;

    public final List<String> labels;

    @DataBoundConstructor
    public SlaveTemplate(String marketplaceId, InstanceTypes instanceType,
            String description, String remoteFS, String remoteUser,
            String labelString, boolean initScriptFlag, String initScriptDir,
            String initScriptName, String initScript, int executors,
            String jvmOpts, int sshPort, int idleMinutes, long pollInterval,
            long timeout) {

        this.marketplaceId = marketplaceId;
        this.instanceType = instanceType;
        this.description = description;
        this.remoteFS = remoteFS;
        this.remoteUser = getRemoteUser(remoteUser);
        this.labelString = labelString;
        this.initScriptFlag = initScriptFlag;
        this.initScriptDir = initScriptDir;
        this.initScriptName = initScriptName;
        this.initScript = initScript;
        this.executors = executors;
        this.jvmOpts = jvmOpts;
        this.sshPort = sshPort;
        this.idleMinutes = idleMinutes;
        this.pollInterval = pollInterval;
        this.timeout = timeout;

        this.labels = createLabelList(labelString);
    }

    @SuppressWarnings("unchecked")
    public Descriptor<SlaveTemplate> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public int getExecutors() {
        return executors;
    }

    public static String getRemoteUser(String remoteUser) {
        String user = remoteUser;
        if (isEmptyStringOrNull(remoteUser)) {
            user = "root";
        }
        return user;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SlaveTemplate> {

        public String getDisplayName() {
            return "Slave Template";
        }

        public FormValidation doCheckMarketplaceId(
                @QueryParameter String marketplaceId) {
            return validateMarketplaceId(marketplaceId);
        }

        public FormValidation doCheckLabelString(
                @QueryParameter String labelString) {
            return validateLabelString(labelString);
        }

        public FormValidation doCheckInitScriptDir(
                @QueryParameter String initScriptDir) {
            return validateInitScriptDir(initScriptDir);
        }

        public FormValidation doCheckInitScriptName(
                @QueryParameter String initScriptName) {
            return validateInitScriptName(initScriptName);
        }

        public FormValidation doCheckInitScript(
                @QueryParameter String initScript) {
            return validateInitScript(initScript);
        }

        public FormValidation doCheckRemoteFS(@QueryParameter String remoteFS) {
            return validateRemoteFS(remoteFS);
        }

        public FormValidation doCheckExecutors(@QueryParameter int executors) {
            return validateExecutors(executors);
        }

        public FormValidation doCheckIdleMinutes(@QueryParameter int idleMinutes) {
            return validateIdleMinutes(idleMinutes);
        }

        public FormValidation doCheckPollInterval(
                @QueryParameter long pollInterval) {
            return validatePollInterval(pollInterval);
        }

        public FormValidation doCheckTimeout(@QueryParameter long timeout) {
            return validateTimeout(timeout);
        }

        public FormValidation doCheckSshPort(@QueryParameter int sshPort) {
            return validateSshPort(sshPort);
        }

    }
}
