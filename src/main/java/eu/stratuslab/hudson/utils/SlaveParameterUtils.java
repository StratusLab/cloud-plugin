package eu.stratuslab.hudson.utils;

import static eu.stratuslab.hudson.utils.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.isPositiveInteger;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.isValidPort;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlaveParameterUtils {

    private static final Pattern MARKETPLACE_IDENTIFIER = Pattern
            .compile("^[A-Za-z0-9_-]{27}$");

    private static final String BAD_IDENTIFIER = "identifier must contain 27 base64 characters";

    private SlaveParameterUtils() {

    }

    public static FormValidation validateMarketplaceId(String id) {

        if (isEmptyStringOrNull(id)) {
            return FormValidation.error(BAD_IDENTIFIER);
        } else {
            Matcher matcher = MARKETPLACE_IDENTIFIER.matcher(id);
            if (matcher.matches()) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(BAD_IDENTIFIER);
            }
        }
    }

    public static FormValidation validateExecutors(int executors) {
        if (isPositiveInteger(executors)) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("executors must be positive integer");
        }
    }

    public static FormValidation validateIdleMinutes(int idleMinutes) {
        if (isPositiveInteger(idleMinutes)) {
            return FormValidation.ok();
        } else {
            return FormValidation
                    .error("idle minutes must be positive integer");
        }
    }

    public static FormValidation validateLabelString(String labelString) {
        List<String> labels = createLabelList(labelString);
        if (labels.size() > 0) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("at least one label must be defined");
        }
    }

    public static FormValidation validateInitScriptDir(String initScriptDir) {
        if (!isEmptyStringOrNull(initScriptDir)) {
            String value = initScriptDir.trim();
            if (value.endsWith("/") || value.endsWith("\\")) {
                return FormValidation.ok();
            } else {
                return FormValidation
                        .error("init script directory must end with a directory separator (slash or backslash)");
            }
        } else {
            return FormValidation.error("remoteFS must be defined");
        }
    }

    public static FormValidation validateInitScriptName(String initScriptName) {
        if (isEmptyStringOrNull(initScriptName)) {
            return FormValidation.error("init script name cannot be empty");
        } else {
            return FormValidation.ok();
        }
    }

    public static FormValidation validateInitScript(String initScript) {
        if (isEmptyStringOrNull(initScript)) {
            return FormValidation.error("init script contents cannot be empty");
        } else {
            return FormValidation.ok();
        }
    }

    public static FormValidation validateRemoteFS(String remoteFS) {
        if (!isEmptyStringOrNull(remoteFS)) {
            String value = remoteFS.trim();
            if (value.endsWith("/") || value.endsWith("\\")) {
                return FormValidation.ok();
            } else {
                return FormValidation
                        .error("remoteFS must end with a directory separator (slash or backslash)");
            }
        } else {
            return FormValidation.error("remoteFS must be defined");
        }
    }

    public static FormValidation validateSshPort(int sshPort) {

        if (isValidPort(sshPort)) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("port must be in range [1, 65535]");
        }
    }

    public static FormValidation validatePollInterval(long pollInterval) {

        if (pollInterval > 0L) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("poll interval must be positive value");
        }
    }

    public static FormValidation validateTimeout(long timeout) {

        if (timeout > 0L) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("timeout must be positive value");
        }
    }

    public static List<String> createLabelList(String labelString) {
        ArrayList<String> list = new ArrayList<String>();
        if (labelString != null) {
            for (String label : labelString.split("\\s*,\\s*")) {
                String trimmedLabel = label.trim();
                if (!isEmptyStringOrNull(trimmedLabel)) {
                    list.add(trimmedLabel);
                }
            }
        }
        list.trimToSize();
        return Collections.unmodifiableList(list);
    }

}
