/*
 * Copyright 2014
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.mule.api.config.MuleProperties;
import org.mule.config.spring.util.ProcessingStrategyUtils;
import org.mule.modules.cors.Constants;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.SystemProperty;

import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CORSModuleTest extends FunctionalTestCase {

    /**
     * The expected value for most of the test cases.
     */
    public static final String EXPECTED_RETURN = "Test String";

    public static final String ENDPOINT_HOST = "localhost";

    public static final String ENDPOINT_PORT = "9081";

    private static final String ENDPOINT_URI = "http://" + ENDPOINT_HOST + ":" + ENDPOINT_PORT;

    public static final String CORS_CONFIGURED_ENDPOINT_PATH = "/test";

    /**
     * The endpoint that has a valid filter configuration
     */
    public static final String CORS_CONFIGURED_ENDPOINT_URL = ENDPOINT_URI + CORS_CONFIGURED_ENDPOINT_PATH;
    public static final String CORS_DEFAULT_ENDPOINT_PATH = "/default";


    /**
     * Endpoint to test default configuration.
     */
    public static final String CORS_DEFAULT_ENDPOINT_URL = ENDPOINT_URI + CORS_DEFAULT_ENDPOINT_PATH;
    public static final String CORS_PUBLIC_ENDPOINT_PATH = "/public";

    /**
     * An endpoint for a public resource
     */
    public static final String CORS_PUBLIC_ENDPOINT_URL = ENDPOINT_URI + CORS_PUBLIC_ENDPOINT_PATH;
    public static final String CORS_HEADERS_ENDPOINT_PATH = "/headers";

    /**
     * An endpoint to test response headers
     */
    public static final String CORS_HEADERS_ENDPOINT_URL = ENDPOINT_URI + CORS_HEADERS_ENDPOINT_PATH;
    /**
     * The origin we have configured on the test case
     */
    public static final String CORS_TEST_ORIGIN = "http://localhost:8081";

    /**
     * One origin not configured for the test case but to test default behavior.
     */
    public static final String CORS_DEFAULT_ORIGIN = "http://somehost";

    @Rule
    public SystemProperty systemProperty;

    private String configFile;

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {{"mule-config.xml", false},
                {"listeners-mule-config.xml", true},
                {"listeners-mule-config.xml", false}});
    }

    public CORSModuleTest(String configFile, boolean nonBlocking) {
        this.configFile = configFile;
        if (nonBlocking) {
            systemProperty = new SystemProperty(MuleProperties.MULE_DEFAULT_PROCESSING_STRATEGY,
                                                ProcessingStrategyUtils.NON_BLOCKING_PROCESSING_STRATEGY);
        }
    }

    @Override
    protected String getConfigFile() {
        return configFile;
    }

    @Test
    public void testNoOriginMethod() throws Exception {
        final Response response = Request.Get(CORS_CONFIGURED_ENDPOINT_URL).execute();
        assertNotNull("The response should not be null", response);
        assertEquals("The response should be the expected value", EXPECTED_RETURN, response.returnContent().asString());
    }

    @Test
    public void testConfiguredOriginMethod() throws Exception {
        final HttpResponse response = Request.Get(CORS_CONFIGURED_ENDPOINT_URL).addHeader("Origin", CORS_TEST_ORIGIN).execute().returnResponse();
        assertNotNull("Response should not be null", response);
        assertNotNull("Allowed origin should be present", response.getFirstHeader(Constants.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(EXPECTED_RETURN));
    }

    @Test
    public void testMethodNotAllowed() throws Exception {
        final HttpResponse response = Request.Post(CORS_CONFIGURED_ENDPOINT_URL).addHeader("Origin", CORS_TEST_ORIGIN).execute().returnResponse();
        //a well behaved client should have sent a preflight
        //but we don't want to be well behaved
        assertNull("Allowed origin should NOT be present", response.getFirstHeader(Constants.ACCESS_CONTROL_ALLOW_ORIGIN));
        //the payload should NOT be the expected response
        assertThat(IOUtils.toString(response.getEntity().getContent()), not(equalTo(EXPECTED_RETURN)));
    }

    @Test
    public void testPreflight() throws Exception {
        //test the preflight is working
        //put some preflight headers
        final HttpResponse response = Request.Options(CORS_CONFIGURED_ENDPOINT_URL).addHeader("Origin", CORS_TEST_ORIGIN)
                .addHeader(Constants.REQUEST_METHOD, "GET").execute().returnResponse();

        Header allowOrigin = response.getFirstHeader(Constants.ACCESS_CONTROL_ALLOW_ORIGIN);
        Header allowMethods = response.getFirstHeader(Constants.ACCESS_CONTROL_ALLOW_METHODS);

        assertNotNull(allowOrigin);
        assertNotNull(allowMethods);
        assertThat(allowOrigin.getValue(), equalTo(CORS_TEST_ORIGIN));
        assertThat(allowMethods.getValue(), containsString("GET"));
        assertThat(allowMethods.getValue(), containsString("PUT"));
    }

    @Test
    public void testPublicResource() throws Exception {
        //configure any origin
        //some not allowed method
        //even without preflight we should be allowed to access the resource
        final HttpResponse response = Request.Post(CORS_PUBLIC_ENDPOINT_URL).addHeader("Origin", "null").execute().returnResponse();

        assertNotNull("Response should not be null", response);

        //allow origin should be *
        Header allowOrigin = response.getFirstHeader(Constants.ACCESS_CONTROL_ALLOW_ORIGIN);

        assertNotNull(allowOrigin);
        assertThat(allowOrigin.getValue(), equalTo("*"));

        assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(EXPECTED_RETURN));
    }

    @Test
    public void testDefaultOriginMethod() throws Exception {
        //send a request to get (no preflight)
        final HttpResponse response = Request.Get(CORS_DEFAULT_ENDPOINT_URL).addHeader("Origin", CORS_DEFAULT_ORIGIN).execute().returnResponse();

        assertNotNull("Response should not be null", response);

        //we should have an access control allow origin
        assertNotNull("Allowed origin should be present", response.getFirstHeader(Constants.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(EXPECTED_RETURN));

    }

    @Test
    public void testDefaultOriginMethodNotAllowed() throws Exception {
        //send a method not allowed and verify the module is filtering the request
        //send a request to get (no preflight)
        final HttpResponse response = Request.Post(CORS_DEFAULT_ENDPOINT_URL).addHeader("Origin", CORS_DEFAULT_ORIGIN).execute().returnResponse();

        //a well behaved client should have sent a preflight
        //but we don't want to be well behaved
        assertNull("Allowed origin should NOT be present", response.getFirstHeader(Constants.ACCESS_CONTROL_ALLOW_ORIGIN));

        //the payload should NOT be the expected response
        assertThat(IOUtils.toString(response.getEntity().getContent()), not(equalTo(EXPECTED_RETURN)));

    }

    @Test
    public void testResponseHeaders() throws Exception {
        final HttpResponse response = Request.Post(CORS_HEADERS_ENDPOINT_URL).addHeader("Origin", CORS_DEFAULT_ORIGIN).execute().returnResponse();
        assertNull("header MULE_ROOT_MESSAGE_ID should not be present", response.getFirstHeader("MULE_ROOT_MESSAGE_ID"));
        assertNotNull("Allowed origin should be present", response.getFirstHeader(Constants.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(EXPECTED_RETURN));
    }

}
