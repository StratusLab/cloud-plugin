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

import static eu.stratuslab.hudson.utils.ProcessUtils.runCommand;
import hudson.util.FormValidation;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.stratuslab.hudson.CloudParameters;
import eu.stratuslab.hudson.StratusLabException;

public final class CloudParameterUtils {

    private static final Pattern ENDPOINT_PATTERN = Pattern
            .compile("^(?:(\\w+)://)?([\\w\\d\\-\\.]+)(?::(\\d+))?/?$");

    private static final String EMPTY_STRING = "";

    public static final int DEFAULT_PORT = 2634;

    public static final String DEFAULT_SCHEME = "https";

    private CloudParameterUtils() {

    }

    public static FormValidation validateEndpoint(String endpoint) {
        try {
            getEndpoint(endpoint);
            return FormValidation.ok();
        } catch (StratusLabException e) {
            return FormValidation.error(e.getMessage());
        }
    }

    public static String getEndpoint(String endpoint)
            throws StratusLabException {

        if (isEmptyStringOrNull(endpoint)) {
            throw new StratusLabException("endpoint cannot be empty");
        }

        Matcher matcher = ENDPOINT_PATTERN.matcher(endpoint);
        if (!matcher.matches()) {
            throw new StratusLabException("endpoint does not match pattern: "
                    + ENDPOINT_PATTERN.toString());
        }

        String scheme = matcher.group(1);
        String authority = matcher.group(2);
        String portString = matcher.group(3);

        if (isEmptyStringOrNull(scheme)) {
            scheme = DEFAULT_SCHEME;
        }

        int port = 0;

        if (isEmptyStringOrNull(portString)) {
            port = DEFAULT_PORT;
        } else {
            try {
                port = Integer.parseInt(portString);
            } catch (IllegalArgumentException e) {
                throw new StratusLabException(e.getMessage());
            }
        }

        if (!isValidPort(port)) {
            throw new StratusLabException("invalid port: " + port);
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
        return (s == null || EMPTY_STRING.equals(s.trim()));
    }

    public static boolean isValidPort(int port) {
        return (port > 0 && port <= 65535);
    }

    public static boolean isPositiveInteger(String s) {
        try {
            int value = Integer.parseInt(s);
            return (value > 0);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isPositiveInteger(int i) {
        return (i > 0);
    }

    public static FormValidation validateKeyFile(String keyFilename,
            String suffix) {
        File keyFile = CloudParameters.getKeyFile(keyFilename, suffix);
        if (keyFile.canRead()) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("cannot read key file "
                    + keyFile.getAbsolutePath());
        }
    }

    public static FormValidation validateClientLocation(String clientLocation) {

        try {
            runCommand(clientLocation, "stratus-describe-instance", "--help");
            return FormValidation.ok();
        } catch (StratusLabException e) {
            return FormValidation.error(e.getMessage());
        }

    }

}
