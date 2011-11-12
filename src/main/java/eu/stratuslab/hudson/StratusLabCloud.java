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
package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.isPositiveInteger;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.validateClientLocation;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.validateEndpoint;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.validateKeyFile;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.AbstractCloudImpl;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class StratusLabCloud extends AbstractCloudImpl {

    private static final Logger LOGGER = Logger.getLogger(StratusLabCloud.class
            .getName());

    private static final String CLOUD_NAME = "StratusLab Cloud";

    public final String clientLocation;

    public final String endpoint;

    public final String username;

    public final String password;

    public final String sshPublicKey;

    public final String sshPrivateKey;

    public final String sshPrivateKeyPassword;

    public final int instanceLimit;

    public final List<SlaveTemplate> templates;

    private final CloudParameters params;

    private final Map<String, SlaveTemplate> labelToTemplateMap;

    private static final AtomicInteger serial = new AtomicInteger(0);

    @DataBoundConstructor
    public StratusLabCloud(String clientLocation, String endpoint,
            String username, String password, String sshPublicKey,
            String sshPrivateKey, String sshPrivateKeyPassword,
            int instanceLimit, List<SlaveTemplate> templates) {

        super(CLOUD_NAME, String.valueOf(instanceLimit));

        this.clientLocation = clientLocation;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.sshPublicKey = sshPublicKey;
        this.sshPrivateKey = sshPrivateKey;
        this.sshPrivateKeyPassword = sshPrivateKeyPassword;
        this.instanceLimit = instanceLimit;

        params = new CloudParameters(clientLocation, endpoint, username,
                password, sshPublicKey, sshPrivateKey, sshPrivateKeyPassword,
                instanceLimit);

        this.templates = copyToImmutableList(templates);

        labelToTemplateMap = mapLabelsToTemplates(this.templates);

        String format = "configuration updated with %s label(s) and %s slave template(s)";
        LOGGER.info(String.format(format, labelToTemplateMap.size(),
                this.templates.size()));
    }

    private List<SlaveTemplate> copyToImmutableList(
            List<SlaveTemplate> templates) {

        ArrayList<SlaveTemplate> list = new ArrayList<SlaveTemplate>();
        if (templates != null) {
            list.addAll(templates);
        }
        list.trimToSize();
        return Collections.unmodifiableList(list);
    }

    private Map<String, SlaveTemplate> mapLabelsToTemplates(
            List<SlaveTemplate> templates) {

        Map<String, SlaveTemplate> map = new HashMap<String, SlaveTemplate>();

        for (SlaveTemplate template : templates) {
            for (String label : template.labels) {
                map.put(label, template);
            }
        }

        return Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    public Descriptor<Cloud> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public boolean canProvision(Label label) {
        if (label != null) {
            return labelToTemplateMap.containsKey(label.getName());
        } else {
            return false;
        }
    }

    public Collection<PlannedNode> provision(Label label, int excessWorkload) {

        Collection<PlannedNode> nodes = new LinkedList<PlannedNode>();

        // The returned value for template should never be null because
        // only labels will be used for which the canProvision method
        // returned true. However, label can be null if Hudson is searching
        // for a node without a label. This implementation doesn't support
        // that.
        if (label != null) {
            SlaveTemplate template = labelToTemplateMap.get(label.getName());

            int numberOfInstances = StratusLabProxy
                    .getNumberOfDefinedInstances(params);

            for (int i = 0; i < excessWorkload; i += template.executors) {
                if (numberOfInstances < params.instanceLimit) {
                    String displayName = generateDisplayName(label, template);
                    SlaveCreator c = new SlaveCreator(template, params, label);
                    Future<Node> futureNode = Computer.threadPoolForRemoting
                            .submit(c);
                    nodes.add(new PlannedNode(displayName, futureNode,
                            template.executors));
                    numberOfInstances++;
                } else {
                    String fmt = "instance limit (%s) exceeded; not provisioning node";
                    LOGGER.warning(String.format(fmt, params.instanceLimit));
                }
            }
        }

        String fmt = "allocating %s node(s)";
        LOGGER.info(String.format(fmt, nodes.size()));
        return nodes;
    }

    public static String generateDisplayName(Label label, SlaveTemplate template) {

        final String fmt = "%s-%d (%s, %s)";
        return String.format(fmt, label.getName(), serial.incrementAndGet(),
                template.marketplaceId, template.instanceType.tag());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return CLOUD_NAME;
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

        public FormValidation doCheckSshPublicKey(
                @QueryParameter String sshPublicKey) {
            return validateKeyFile(sshPublicKey, ".pub");
        }

        public FormValidation doCheckSshPrivateKey(
                @QueryParameter String sshPrivateKey) {
            return validateKeyFile(sshPrivateKey, "");
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
                    endpoint, username, password, null, null, null, 1);

            try {
                StratusLabProxy.testConnection(params);
            } catch (StratusLabException e) {
                return FormValidation.error(e.getMessage());
            }

            return FormValidation.ok();
        }

    }

}
