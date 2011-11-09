package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.CloudParameterUtils.DEFAULT_PORT;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.DEFAULT_SCHEME;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.getEndpoint;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.isPositiveInteger;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.isValidPort;
import static eu.stratuslab.hudson.utils.CloudParameterUtils.validateEndpoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import hudson.util.FormValidation;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;

public class CloudParameterUtilsTest {

    @Test(expected = StratusLabException.class)
    public void testEmptyEndpoint() throws StratusLabException {
        getEndpoint("");
    }

    @Test(expected = StratusLabException.class)
    public void testNullEndpoint() throws StratusLabException {
        getEndpoint(null);
    }

    @Test
    public void testInvalidEndpoints() {
        String[] invalidEndpoints = { //
        "", // empty string is not valid
                null, // null isn't either
                " example.org", // space at beginning
                "example.org ", // space at end
                "mailto:example.org", // not hierarchical URL
                "http://example.org:badport", // bad port
                "example+bad.org", // invalid character
                "example.org:0", // port too small
                "example.org:65536", // port too large
        };

        for (String endpoint : invalidEndpoints) {
            try {
                getEndpoint(endpoint);
                fail("invalid endpoint (" + endpoint
                        + ") did not throw exception");
            } catch (StratusLabException e) {
                // OK
            }
        }
    }

    @Test
    public void testValidEndpoints() throws StratusLabException {
        String[] validEndpoints = { //
        "example.org", //
                "example.org:1234", //
                "http://example.org", //
                "https://example.org", //
                "http://example.org/", //
                "https://example.org/", //
                "http://example.org:1234", //
                "https://example.org:1234", //
                "http://example.org:1234/", //
                "https://example.org:1234/", //
                "http://example.org:1", //
                "https://example.org:65535", //
        };

        for (String endpoint : validEndpoints) {
            try {
                getEndpoint(endpoint);
            } catch (StratusLabException e) {
                fail("valid endpoint (" + endpoint
                        + ") was not considered valid\n" + e.getMessage());
            }
        }
    }

    @Test
    public void validateGoodEndpoint() {
        FormValidation formValidation = validateEndpoint("https://example.org:1234/");
        assertEquals(FormValidation.Kind.OK, formValidation.kind);
    }

    @Test
    public void validateBadEndpoint() {
        FormValidation formValidation = validateEndpoint("mailto:example.org");
        assertEquals(FormValidation.Kind.ERROR, formValidation.kind);
    }

    @Test
    public void checkDefaultScheme() throws URISyntaxException,
            StratusLabException {

        String[] validEndpoints = { //
        "example.org", //
                "example.org:1234", //
        };

        for (String endpoint : validEndpoints) {
            String fullEndpoint = getEndpoint(endpoint);
            URI uri = new URI(fullEndpoint);
            assertEquals(DEFAULT_SCHEME, uri.getScheme());
        }
    }

    @Test
    public void checkDefaultPort() throws URISyntaxException,
            StratusLabException {

        String[] validEndpoints = { //
        "example.org", //
                "http://example.org", //
                "https://example.org", //
                "http://example.org/", //
                "https://example.org/", //
        };

        for (String endpoint : validEndpoints) {
            String fullEndpoint = getEndpoint(endpoint);
            URI uri = new URI(fullEndpoint);
            assertEquals(DEFAULT_PORT, uri.getPort());
        }
    }

    @Test
    public void checkNonDefaultPort() throws URISyntaxException,
            StratusLabException {

        String[] validEndpoints = { //
        "example.org:1234", //
                "http://example.org:1234", //
                "https://example.org:1234", //
                "http://example.org:1234/", //
                "https://example.org:1234/", //
        };

        for (String endpoint : validEndpoints) {
            String fullEndpoint = getEndpoint(endpoint);
            URI uri = new URI(fullEndpoint);
            assertEquals(1234, uri.getPort());
        }
    }

    @Test
    public void checkValidPorts() {
        assertTrue("valid port (1) marked as invalid", isValidPort(1));
        assertTrue("valid port (65535) marked as invalid", isValidPort(65535));
    }

    @Test
    public void checkInvalidPorts() {
        assertFalse("invalid port (0) marked as valid", isValidPort(0));
        assertFalse("invalid port (65536) marked as valid", isValidPort(65536));
    }

    @Test
    public void checkPositiveInteger() {
        assertTrue("integer (1) not positive", isPositiveInteger("1"));
        assertTrue("integer (100000) not positive", isPositiveInteger("100000"));
    }

    @Test
    public void checkNonPositiveInteger() {
        assertFalse("integer (-1) marked as positive", isPositiveInteger("-1"));
        assertFalse("integer (0) marked as positive", isPositiveInteger("0"));
    }

    @Test
    public void testCheckForNullString() {
        assertTrue("null didn't test as null", isEmptyStringOrNull(null));
    }

    @Test
    public void testCheckForEmptyString() {
        assertTrue("empty string didn't test as empty string",
                isEmptyStringOrNull(""));
    }

    @Test
    public void testCheckForNonEmptyString() {
        assertFalse("non-empty string tested as empty",
                isEmptyStringOrNull("not empty"));
    }
}
