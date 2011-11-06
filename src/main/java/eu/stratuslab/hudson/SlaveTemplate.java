package eu.stratuslab.hudson;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

public class SlaveTemplate implements Describable<SlaveTemplate> {

    public final String marketplaceId;
    public final String instanceType;
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
    public SlaveTemplate(String marketplaceId, String instanceType,
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

    List<String> createLabelList(String labelString) {
        ArrayList<String> list = new ArrayList<String>();
        for (String label : labelString.split("\\s*,\\s*")) {
            list.add(label);
        }
        list.trimToSize();
        return Collections.unmodifiableList(list);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SlaveTemplate> {

        public String getDisplayName() {
            return "Slave Template";
        }

    }
}
