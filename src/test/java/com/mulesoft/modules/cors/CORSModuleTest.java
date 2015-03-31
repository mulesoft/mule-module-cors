/*
 * Copyright 2014 juancavallotti.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mulesoft.modules.cors;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mule.module.http.api.HttpConstants.Methods.OPTIONS;
import static org.mule.module.http.api.HttpConstants.Methods.POST;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.module.http.api.client.HttpRequestOptions;
import org.mule.module.http.api.client.HttpRequestOptionsBuilder;
import org.mule.modules.cors.Constants;
import org.mule.modules.cors.adapters.CORSModuleLifecycleInjectionAdapter;
import org.mule.tck.junit4.FunctionalTestCase;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

public class CORSModuleTest extends FunctionalTestCase
{
    @Override
    protected String getConfigFile()
    {
        return "mule-config.xml";
    }
    
    /**
     * The expected value for most of the test cases.
     */
    public static final String EXPECTED_RETURN = "Test String";
    
    /**
     * The endpoint that has a valid filter configuration
     */
    public static final String CORS_CONFIGURED_ENDPOINT_URL = "http://localhost:9081/test";

    /**
     * Endpoint to test default configuration.
     */
    public static final String CORS_DEFAULT_ENDPOINT_URL = "http://localhost:9081/default";

    /**
     * An endpoint for a public resource
     */
    public static final String CORS_PUBLIC_ENDPOINT_URL = "http://localhost:9081/public";
    
    /**
     * An endpoint to test response headers
     */
    public static final String CORS_HEADERS_ENDPOINT_URL = "http://localhost:9081/headers";

    /**
     * The origin we have configured on the test case
     */
    public static final String CORS_TEST_ORIGIN = "http://localhost:8081";

    /**
     * One origin not configured for the test case but to test default behavior.
     */
    public static final String CORS_DEFAULT_ORIGIN = "http://somehost";

    /**
     * Mule client to send messages in each test
     */
    private MuleClient client;
    
    
    private HashMap<String, Object> headers;

    private HttpRequestOptions getRequest = HttpRequestOptionsBuilder.newOptions().build();
    private HttpRequestOptions postRequest = HttpRequestOptionsBuilder.newOptions().method(POST.name()).build();
    private HttpRequestOptions optionsRequest = HttpRequestOptionsBuilder.newOptions().method(OPTIONS.name()).build();

    @Before
    public void initTestClient() {
        
        //initialize the standard client.
        client = muleContext.getClient();
        
        //initialize the standard headers
        headers = new HashMap<String, Object>();
                
    }

    private MuleMessage performRequest(String url) throws MuleException {
        return performRequest(url, getRequest);
    }

    private MuleMessage performRequest(String url, HttpRequestOptions requestOptions) throws MuleException {
        MuleMessage request = new DefaultMuleMessage("", headers, muleContext);

        //send a request to get (no preflight)
        return client.send(url, request, requestOptions);
    }

    @Test
    public void testNoOriginMethod() throws Exception {
        
        MuleMessage response = performRequest(CORS_CONFIGURED_ENDPOINT_URL);

        //the response should not be null
        assertNotNull("The response should not be null", response);
        //the response payload should not be null
        assertNotNull("The payload should not be null", response.getPayload());
        
        String payload = response.getPayloadAsString();
        
        //the payload should be the expected value
        assertEquals("The response should be the expected value", EXPECTED_RETURN, payload);
        
    }
    
    @Test
    public void testConfiguredOriginMethod() throws Exception {
        
        //add the origin header
        headers.put("Origin", CORS_TEST_ORIGIN);
        
        //send a request to get (no preflight)
        MuleMessage response = performRequest(CORS_CONFIGURED_ENDPOINT_URL);
        
        assertNotNull("Response should not be null", response);
        
        //we should have an access control allow origin
        String allowedOrigin = response.getInboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN);
        
        assertNotNull("Allowed origin should be present", allowedOrigin);
        
        
        //check the payload
        //the payload should be the expected value
        assertThat(response.getPayloadAsString(), equalTo(EXPECTED_RETURN));
        
    }
    
    
    @Test
    public void testMethodNotAllowed() throws Exception {
        
        //send a method not allowed and verify the module is filtering the request
        //add the origin header
        headers.put("Origin", CORS_TEST_ORIGIN);

        MuleMessage response = performRequest(CORS_CONFIGURED_ENDPOINT_URL, postRequest);
        
        //a well behaved client should have sent a preflight
        //but we don't want to be well behaved
        
        String allowedOrigin = response.getInboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN);
        
        assertNull("Allowed origin should NOT be present", allowedOrigin);
        
        //the payload should NOT be the expected response
        assertThat(response.getPayloadAsString(), not(equalTo(EXPECTED_RETURN)));
        
    }

    @Test
    public void testPreflight() throws Exception {
        
        //test the preflight is working
        headers.put("Origin", CORS_TEST_ORIGIN);
        //put some preflight headers
        headers.put(Constants.REQUEST_METHOD, "GET");
        
        MuleMessage response = performRequest(CORS_CONFIGURED_ENDPOINT_URL, optionsRequest);

        assertNotNull("Response should not be null", response);
        
        String allowOrigin = response.getInboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN);
        String allowMethods = response.getInboundProperty(Constants.ACCESS_CONTROL_ALLOW_METHODS);
        
        //then assert
        assertThat(allowOrigin, equalTo(CORS_TEST_ORIGIN));
        assertThat(allowMethods, allOf(containsString("GET"), containsString("PUT")));
    }
    
    @Test
    public void testPublicResource() throws Exception {
        
        //configure any origin
        headers.put("Origin", "null");

        //even without preflight we should be allowed to access the resource
        MuleMessage response = performRequest(CORS_PUBLIC_ENDPOINT_URL, postRequest);

        assertNotNull("Response should not be null", response);
        
        //allow origin should be *
        String allowOrigin = response.getInboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN);
        
        assertThat(allowOrigin, equalTo("*"));
        
        //the payload should be the expected
        assertThat(response.getPayloadAsString(), equalTo(EXPECTED_RETURN));
        
    }

    @Test
    public void testDefaultOriginMethod() throws Exception {

        //add the origin header
        headers.put("Origin", CORS_DEFAULT_ORIGIN);

        //send a request to get (no preflight)
        MuleMessage response = performRequest(CORS_DEFAULT_ENDPOINT_URL);

        assertNotNull("Response should not be null", response);

        //we should have an access control allow origin
        String allowedOrigin = response.getInboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN);

        assertNotNull("Allowed origin should be present", allowedOrigin);


        //check the payload
        //the payload should be the expected value
        assertThat(response.getPayloadAsString(), equalTo(EXPECTED_RETURN));

    }

    @Test
    public void testDefaultOriginMethodNotAllowed() throws Exception {

        //send a method not allowed and verify the module is filtering the request
        //add the origin header
        headers.put("Origin", CORS_DEFAULT_ORIGIN);

        //send a request to get (no preflight)
        MuleMessage response = performRequest(CORS_DEFAULT_ENDPOINT_URL, postRequest);

        //a well behaved client should have sent a preflight
        //but we don't want to be well behaved
        String allowedOrigin = response.getInboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN);

        assertNull("Allowed origin should NOT be present", allowedOrigin);

        //the payload should NOT be the expected response
        assertThat(response.getPayloadAsString(), not(equalTo(EXPECTED_RETURN)));

    }


    @Test
    public void testLifecycle() throws Exception{

        CORSModuleLifecycleInjectionAdapter module = muleContext.getRegistry().lookupObject("defaultConfig");

        module.stop();

        module.start();
    }

    @Test
    public void testResponseHeaders() throws Exception {

        MuleMessage response = performRequest(CORS_HEADERS_ENDPOINT_URL, postRequest);

        assertNotNull("Response should not be null", response);

        String muleMessageId = response.getInboundProperty("MULE_ROOT_MESSAGE_ID");
        assertNull("header MULE_ROOT_MESSAGE_ID should not be present", muleMessageId);

        //the payload should be the expected
        assertThat(response.getPayloadAsString(), equalTo(EXPECTED_RETURN));

    }

}
