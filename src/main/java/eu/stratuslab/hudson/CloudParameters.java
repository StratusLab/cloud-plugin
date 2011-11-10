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

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class CloudParameters implements Describable<CloudParameters> {

    public final String clientLocation;
    public final String endpoint;
    public final String username;
    public final String password;

    @DataBoundConstructor
    public CloudParameters(String clientLocation, String endpoint,
            String username, String password) {

        this.clientLocation = clientLocation;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
    }

    @SuppressWarnings("unchecked")
    public Descriptor<CloudParameters> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CloudParameters> {

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
                    endpoint, username, password);

            try {
                StratusLabProxy.testConnection(params);
            } catch (StratusLabException e) {
                return FormValidation.error(e.getMessage());
            }

            return FormValidation.ok();
        }

    }

}
