package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.SlaveParameterUtils.createLabelList;
import static eu.stratuslab.hudson.SlaveParameterUtils.validateExecutors;
import static eu.stratuslab.hudson.SlaveParameterUtils.validateLabelString;
import static eu.stratuslab.hudson.SlaveParameterUtils.validateMarketplaceId;
import static eu.stratuslab.hudson.SlaveParameterUtils.validateSshPort;
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

        private final String name;
        private final int cpu;
        private final int ramMB;
        private final int swapMB;
        private final String label;

        private InstanceTypes(String name, int cpu, int ramMB, int swapMB) {
            this.name = name;
            this.cpu = cpu;
            this.ramMB = ramMB;
            this.swapMB = swapMB;
            label = createLabel();
        }

        public String label() {
            return label;
        }

        private String createLabel() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
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
    public final String initScript;
    public final String context;
    public final String executors;
    public final String rootCommandPrefix;
    public final String jvmOpts;
    public final String sshPort;

    public final List<String> labels;

    @DataBoundConstructor
    public SlaveTemplate(String marketplaceId, InstanceTypes instanceType,
            String description, String remoteFS, String remoteUser,
            String labelString, String initScript, String context,
            String executors, String rootCommandPrefix, String jvmOpts,
            String sshPort) {

        this.marketplaceId = marketplaceId;
        this.instanceType = instanceType;
        this.description = description;
        this.remoteFS = remoteFS;
        this.remoteUser = remoteUser;
        this.labelString = labelString;
        this.initScript = initScript;
        this.context = context;
        this.executors = executors;
        this.rootCommandPrefix = rootCommandPrefix;
        this.jvmOpts = jvmOpts;
        this.sshPort = sshPort;

        this.labels = createLabelList(labelString);
    }

    @SuppressWarnings("unchecked")
    public Descriptor<SlaveTemplate> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
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

        public FormValidation doCheckExecutors(@QueryParameter String executors) {
            return validateExecutors(executors);
        }

        public FormValidation doCheckSshPort(@QueryParameter String sshPort) {
            return validateSshPort(sshPort);
        }

    }
}
