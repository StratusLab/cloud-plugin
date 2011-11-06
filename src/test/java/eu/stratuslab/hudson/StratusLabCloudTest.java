package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.StratusLabCloud.DescriptorImpl.getEndpoint;

import org.junit.Test;

public class StratusLabCloudTest {

    @Test(expected = StratusLabException.class)
    public void testEmptyEndpoint() throws StratusLabException {
        getEndpoint("");
    }

    @Test(expected = StratusLabException.class)
    public void testNullEndpoint() throws StratusLabException {
        getEndpoint(null);
    }

}
