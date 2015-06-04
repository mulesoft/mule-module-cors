package com.mulesoft.modules.cors;

import org.mule.tck.junit4.FunctionalTestCase;

import org.junit.Test;

public class NonBlockingTest extends FunctionalTestCase
{
    @Override
    protected String getConfigFile() {
        return "non-blocking-mule-config.xml";
    }

    @Test
    public void isNonBlocking() throws Exception {
        testFlowNonBlocking("nonBlockingFlow");
    }

}
