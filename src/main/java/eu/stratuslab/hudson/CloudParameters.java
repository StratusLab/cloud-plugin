package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.isPositiveInteger;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.validateClientLocation;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.validateEndpoint;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.FormValidation;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import eu.stratuslab.hudson.utils.ProcessUtils;

public class CloudParameters implements Describable<CloudParameters> {

    public final String clientLocation;
    public final String endpoint;
    public final String username;
    public final String password;
    public final String sshPrivateKey;
    public final String sshPrivateKeyPassword;
    public final int instanceLimit;

    private final char[] sshPrivateKeyData;

    @DataBoundConstructor
    public CloudParameters(String clientLocation, String endpoint,
            String username, String password, String sshPrivateKey,
            String sshPrivateKeyPassword, int instanceLimit) {

        this.clientLocation = clientLocation;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.sshPrivateKey = sshPrivateKey;
        this.sshPrivateKeyPassword = sshPrivateKeyPassword;
        this.instanceLimit = instanceLimit;

        sshPrivateKeyData = getSshPrivateKeyData(sshPrivateKey);
    }

    @SuppressWarnings("unchecked")
    public Descriptor<CloudParameters> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public char[] getSshPrivateKeyData() {
        return Arrays.copyOf(sshPrivateKeyData, sshPrivateKeyData.length);
    }

    private static char[] getSshPrivateKeyData(String sshPrivateKey) {

        if (sshPrivateKey != null) {
            sshPrivateKey = sshPrivateKey.trim();
        }

        if (sshPrivateKey == null || "".equals(sshPrivateKey)) {
            File home = new File(System.getProperty("user.home"));
            File sshDir = new File(home, ".ssh");
            sshPrivateKey = (new File(sshDir, "id_rsa")).getAbsolutePath();
        }

        return fileToCharArray(new File(sshPrivateKey));
    }

    private static char[] fileToCharArray(File file) {

        char[] data = new char[0];

        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] buffer = new char[2048];
            CharArrayWriter writer = null;
            try {
                writer = new CharArrayWriter();
                for (int n = reader.read(buffer); n >= 0; n = reader
                        .read(buffer)) {
                    for (int i = 0; i < n; i++) {
                        writer.append(buffer[i]);
                    }
                }
                data = writer.toCharArray();
            } catch (IOException consumed) {
                ProcessUtils.closeReliably(writer);
            }
        } catch (IOException consumed) {
            ProcessUtils.closeReliably(reader);
            return new char[0];
        }

        return data;

    }

    @Extension
    public static final class DescriptorImpl extends
            Descriptor<CloudParameters> {

        @Override
        public String getDisplayName() {
            return "Cloud Parameters";
        }

        public FormValidation doCheckClientLocation(
                @QueryParameter String clientLocation) {
            return validateClientLocation(clientLocation);
        }

        public FormValidation doCheckEndpoint(@QueryParameter String endpoint) {
            return validateEndpoint(endpoint);
        }

        public FormValidation doCheckUsername(@QueryParameter String username) {
            if (isEmptyStringOrNull(username)) {
                return FormValidation.error("username must be defined");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckPassword(@QueryParameter String password) {
            if (isEmptyStringOrNull(password)) {
                return FormValidation.error("password must be defined");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckSshPrivateKey(
                @QueryParameter String sshPrivateKey) {

            char[] data = getSshPrivateKeyData(sshPrivateKey);

            if (data.length == 0) {
                return FormValidation
                        .error("SSH private key cannot be read or is empty");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckInstanceLimit(
                @QueryParameter int instanceLimit) {

            if (!isPositiveInteger(instanceLimit)) {
                return FormValidation
                        .error("instance limit must be a positive integer");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doTestConnection(
                @QueryParameter String clientLocation,
                @QueryParameter String endpoint,
                @QueryParameter String username, @QueryParameter String password) {

            CloudParameters params = new CloudParameters(clientLocation,
                    endpoint, username, password, null, null, 1);

            try {
                StratusLabProxy.testConnection(params);
            } catch (StratusLabException e) {
                return FormValidation.error(e.getMessage());
            }

            return FormValidation.ok();
        }

    }

}
