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
import org.mule.module.http.api.HttpHeaders;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
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

    public static final String CORS_CONFIGURED_ENDPOINT_PATH = "/test";

    public static final String CORS_DEFAULT_ENDPOINT_PATH = "/default";

    public static final String CORS_PUBLIC_ENDPOINT_PATH = "/public";

    public static final String CORS_HEADERS_ENDPOINT_PATH = "/headers";

    public static final String CORS_REQUEST_HEADERS_ENDPOINT_PATH = "/requestHeaders";

    public static final String CORS_PUBLIC_EMPTY_ENDPOINT_PATH = "/publicEmpty";

    public static final String CORS_HEADERS_CONFIG_ORIGIN = "http://localhost:8081/headers";

    public static final String CORS_TEST_ORIGIN = "http://localhost:8081";

    public static final String CORS_DEFAULT_ORIGIN = "http://somehost";

    public static final String CORS_MAIN_FLOW_ENDPOINT_PATH = "/mainFlowException";

    /**
     * The endpoint to test Exceptions
     */
    public static final String CORS_EXCEPTION_ENDPOINT_PATH = "/exception";


    @Rule
    public SystemProperty systemProperty;

    @Rule
    public DynamicPort httpPort = new DynamicPort("http.port");


    private String configFile;

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
                {"mule-config.xml", false},
                {"listeners-mule-config.xml", true},
                {"listeners-mule-config.xml", false}
        });
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
        final Response response = Request.Get("http://localhost:" + httpPort.getValue() + CORS_CONFIGURED_ENDPOINT_PATH).execute();
        assertNotNull("The response should not be null", response);
        assertEquals("The response should be the expected value", EXPECTED_RETURN, response.returnContent().asString());
    }

    @Test
    public void testConfiguredOriginMethod() throws Exception {
        final HttpResponse response = Request.Get("http://localhost:" + httpPort.getValue() + CORS_CONFIGURED_ENDPOINT_PATH).addHeader("Origin", CORS_TEST_ORIGIN).execute().returnResponse();
        assertNotNull("Response should not be null", response);
        assertNotNull("Allowed origin should be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(EXPECTED_RETURN));
    }

    @Test
    public void testMethodNotAllowed() throws Exception {
        final HttpResponse response = Request.Post("http://localhost:" + httpPort.getValue() + CORS_CONFIGURED_ENDPOINT_PATH).addHeader("Origin", CORS_TEST_ORIGIN).execute().returnResponse();
        //a well behaved client should have sent a preflight
        //but we don't want to be well behaved
        assertNull("Allowed origin should NOT be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
        //the payload should NOT be the expected response
        assertThat(IOUtils.toString(response.getEntity().getContent()), not(equalTo(EXPECTED_RETURN)));
    }

    @Test
    public void testPreflight() throws Exception {
        //test the preflight is working
        final HttpResponse response = Request.Options("http://localhost:" + httpPort.getValue() + CORS_CONFIGURED_ENDPOINT_PATH)
                .addHeader("Origin", CORS_TEST_ORIGIN)
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS, "X-Allow-Origin")
                .execute().returnResponse();

        Header allowOrigin = response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN);
        Header allowMethods = response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS);

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
        final HttpResponse response = Request.Post("http://localhost:" + httpPort.getValue() + CORS_PUBLIC_ENDPOINT_PATH).addHeader("Origin", "null").execute().returnResponse();

        assertNotNull("Response should not be null", response);

        //allow origin should be *
        Header allowOrigin = response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN);

        assertNotNull(allowOrigin);
        assertThat(allowOrigin.getValue(), equalTo("*"));

        assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(EXPECTED_RETURN));
    }

    @Test
    public void testDefaultOriginMethod() throws Exception {
        //send a request to get (no preflight)
        final HttpResponse response = Request.Get("http://localhost:" + httpPort.getValue() + CORS_DEFAULT_ENDPOINT_PATH).addHeader("Origin", CORS_DEFAULT_ORIGIN).execute().returnResponse();

        assertNotNull("Response should not be null", response);

        //we should have an access control allow origin
        assertNotNull("Allowed origin should be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(EXPECTED_RETURN));

    }

    @Test
    public void testDefaultOriginMethodNotAllowed() throws Exception {
        //send a method not allowed and verify the module is filtering the request
        //send a request to get (no preflight)
        final HttpResponse response = Request.Post("http://localhost:" + httpPort.getValue() + CORS_DEFAULT_ENDPOINT_PATH).addHeader("Origin", CORS_DEFAULT_ORIGIN).execute().returnResponse();

        //a well behaved client should have sent a preflight
        //but we don't want to be well behaved
        assertNull("Allowed origin should NOT be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));

        //the payload should NOT be the expected response
        assertThat(IOUtils.toString(response.getEntity().getContent()), not(equalTo(EXPECTED_RETURN)));

    }

    @Test
    public void testResponseHeaders() throws Exception {
        final HttpResponse response = Request.Post("http://localhost:" + httpPort.getValue() + CORS_HEADERS_ENDPOINT_PATH).addHeader("Origin", CORS_DEFAULT_ORIGIN).execute().returnResponse();
        assertNull("header MULE_ROOT_MESSAGE_ID should not be present", response.getFirstHeader("MULE_ROOT_MESSAGE_ID"));
        assertNotNull("Allowed origin should be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(EXPECTED_RETURN));
    }

    @Test
    public void testRequestHeaders() throws Exception {
        final HttpResponse response = Request.Options("http://localhost:" + httpPort.getValue() + CORS_REQUEST_HEADERS_ENDPOINT_PATH)
                .addHeader("Origin", CORS_HEADERS_CONFIG_ORIGIN)
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, id")
                .execute().returnResponse();
        assertNotNull("Allowed origin should be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void testInvalidRequestHeaders() throws Exception {
        final HttpResponse response = Request.Options("http://localhost:" + httpPort.getValue() + CORS_REQUEST_HEADERS_ENDPOINT_PATH)
                .addHeader("Origin", CORS_HEADERS_CONFIG_ORIGIN)
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, id, invalid")
                .execute().returnResponse();
        assertNull("Allowed origin should not be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
    }


    @Test
    public void testEmptyConfigPublicResource() throws Exception {
        //this is a valid scenario but it seems it produces some exceptions.
        final HttpResponse response = Request.Options("http://localhost:" + httpPort.getValue() + CORS_PUBLIC_EMPTY_ENDPOINT_PATH).addHeader("Origin", CORS_TEST_ORIGIN)
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD, "GET").execute().returnResponse();

        Header allowOrigin = response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN);
        Header allowMethods = response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS);

        assertNotNull(allowOrigin);
        assertThat(allowOrigin.getValue(), equalTo("*"));
    }

    @Test
    public void testInvalidPreflightAuthorRequestHeaders() throws Exception {
        final HttpResponse response = Request.Options("http://localhost:" + httpPort.getValue() + CORS_CONFIGURED_ENDPOINT_PATH)
                .addHeader("Origin", CORS_TEST_ORIGIN)
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .addHeader(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS, "X-Allow-Origin, X-Invalid-Header")
                .execute().returnResponse();

        assertNull(response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void testExceptionThrown() throws Exception {
        //send a request to get (no preflight)
        final HttpResponse response = Request.Get("http://localhost:" + httpPort.getValue() + CORS_EXCEPTION_ENDPOINT_PATH).addHeader("Origin", CORS_DEFAULT_ORIGIN).execute().returnResponse();

        assertNotNull("Response should not be null", response);

        //we should have an access control allow origin
        assertNotNull("Allowed origin should be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void testExceptionThrownInFlowRef() throws Exception {
        //send a request to get (no preflight)
        final HttpResponse response =
                Request.Get("http://localhost:" + httpPort.getValue() + CORS_MAIN_FLOW_ENDPOINT_PATH)
                        .addHeader("Origin", CORS_DEFAULT_ORIGIN).execute().returnResponse();

        assertNotNull("Response should not be null", response);

        //we should have an access control allow origin
        assertNotNull("Allowed origin should be present", response.getFirstHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
