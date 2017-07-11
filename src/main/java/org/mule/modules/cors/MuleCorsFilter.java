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

package org.mule.modules.cors;

import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.module.http.api.HttpConstants;
import org.mule.module.http.api.HttpHeaders;
import org.mule.modules.cors.model.CorsConfig;
import org.mule.modules.cors.model.Origin;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

public class MuleCorsFilter implements CorsFilter
{

    public static final String SEPARATOR = ", ";
    protected transient Log logger = LogFactory.getLog(getClass());

    private final CorsConfig config;
    private final boolean publicResource;
    private final boolean acceptsCredentials;

    public MuleCorsFilter(final CorsConfig config, final boolean publicResource, final boolean acceptsCredentials)
    {
        this.config = config;
        this.publicResource = publicResource;
        this.acceptsCredentials = acceptsCredentials;
    }

    @Override
    public MuleEvent filter(MuleEvent event)
    {
        String origin = event.getMessage().getInboundProperty(HttpHeaders.Names.ORIGIN);
        if (StringUtils.isEmpty(origin))
        {
            logger.debug("Request is not a CORS request.");
            return event;
        }
        String method = event.getMessage().getInboundProperty(HttpConstants.RequestProperties.HTTP_METHOD_PROPERTY);
        if (shouldInvokeFlow(origin, method, publicResource))
        {
            return event;
        }
        event.getMessage().setPayload(null);
        event.getMessage().setInvocationProperty(Constants.CORS_STOP_PROCESSING_FLAG, true);
        return event;
    }

    @Override
    public void addHeaders(MuleEvent event)
    {
        final String origin = event.getMessage().getInboundProperty(HttpHeaders.Names.ORIGIN);
        final String method = event.getMessage().getInboundProperty(HttpConstants.RequestProperties.HTTP_METHOD_PROPERTY);
        final String requestMethod = event.getMessage().getInboundProperty(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD);
        final String requestHeaders = event.getMessage().getInboundProperty(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS);

        addHeaders(event, origin, method, requestMethod, requestHeaders);
    }

    @Override
    public void addHeaders(MuleEvent event, String origin, String method, String requestMethod, String requestHeaders)
    {
        final MuleMessage message = event.getMessage();
        if(StringUtils.isEmpty(origin))
        {
            return;
        }

        boolean isPreflight = StringUtils.equals(Constants.PREFLIGHT_METHOD, method);

        if (publicResource)
        {
            handlePublicResource(message, isPreflight, requestMethod, requestHeaders);
        }

        Origin configuredOrigin = null;
        if (config != null)
        {
            configuredOrigin = config.findOrigin(origin);
        }

        if (configuredOrigin == null)
        {
            return;
        }

        if(isPreflight)
        {
            handlePreflightRequest(event.getMessage(), configuredOrigin, requestMethod, requestHeaders);
        }
        else
        {
            handleActualRequest(event.getMessage(), configuredOrigin, method);
        }
    }

    private void handlePublicResource(final MuleMessage message, final boolean isPreflight, final String requestMethod, final String requestHeaders)
    {
        message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        if (isPreflight)
        {
            if (requestMethod != null)
            {
                message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS, requestMethod);
            }
            if (requestHeaders != null)
            {
                message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS, requestHeaders);
            }
        }
        return;
    }

    private void handlePreflightRequest(final MuleMessage message, final Origin origin, final String method, final String requestHeaders)
    {
        if(!isSupportedMethod(origin, method)
           || !isSupportedRequestHeaders(origin, requestHeaders))
        {
            return;
        }

        message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, origin.getUrl());
        setAllowCredentials(message);

        if (!origin.getMethods().isEmpty())
        {
            message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS, StringUtils.join(origin.getMethods(), SEPARATOR));
        }
        if (!origin.getHeaders().isEmpty())
        {
            message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS, StringUtils.join(origin.getHeaders(), SEPARATOR));
        }
        if (!origin.getExposeHeaders().isEmpty())
        {
            message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_EXPOSE_HEADERS, StringUtils.join(origin.getExposeHeaders(), SEPARATOR));
        }

        if (origin.getAccessControlMaxAge() != null)
        {
            message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_MAX_AGE, origin.getAccessControlMaxAge());
        }
    }

    private void handleActualRequest(final MuleMessage message, final Origin origin, final String method)
    {
        if (!isSupportedMethod(origin, method))
        {
            return;
        }

        message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, origin.getUrl());
        setAllowCredentials(message);
        if (!origin.getExposeHeaders().isEmpty())
        {
            message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_EXPOSE_HEADERS, StringUtils.join(origin.getExposeHeaders(), SEPARATOR));
        }
    }

    private boolean isSupportedMethod(final Origin origin, final String method)
    {
        if(!origin.getMethods().contains(method))
        {
            logger.debug("Unsupported HTTP method: " + method);
            return false;
        }
        return true;
    }

    private boolean isSupportedRequestHeaders(final Origin origin, final String requestHeaders)
    {
        List<String> supportedHeaders = origin.getHeaders();
        final String[] headers = parseMultipleHeaderValues(requestHeaders);
        for(String header : headers)
        {
            if(!containsCaseInsensitive(header,supportedHeaders))
            {
                logger.debug("Unsupported HTTP request header: " + header);
                return false;
            }
        }

        return true;
    }

    public boolean containsCaseInsensitive(String header, List<String> supportedHeaders){
        for (String supportedHeader : supportedHeaders){
            if (supportedHeader.equalsIgnoreCase(header)){
                return true;
            }
        }
        return false;
    }

    private void setAllowCredentials(final MuleMessage message)
    {
        if (acceptsCredentials)
        {
            message.setOutboundProperty(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
    }

    private boolean shouldInvokeFlow(String origin, String method, boolean publicResource)
    {
        if (StringUtils.equals(Constants.PREFLIGHT_METHOD, method))
        {
            logger.debug("OPTIONS header, will not continue processing.");
            return false;
        }

        if (publicResource)
        {
            return true;
        }

        Origin configuredOrigin = config.findOrigin(origin);

        if (configuredOrigin == null)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Could not find configuration for origin: " + origin);
            }
            return false;
        }

        if (!configuredOrigin.getMethods().isEmpty())
        {
            return configuredOrigin.getMethods().contains(method);
        }
        else
        {
            logger.warn("Configured origin has no methods. Not allowing the execution of the flow");
            return false;
        }
    }

    /**
     * Parses a header value consisting of zero or more space, comma or
     * space+comma separated strings. The input string is trimmed before
     * splitting.
     */
    private String[] parseMultipleHeaderValues(final String headerValue)
    {
        if(StringUtils.isEmpty(headerValue))
        {
            return new String[0];
        }

        String trimmedHeaderValue = headerValue.trim();

        if(StringUtils.isEmpty(trimmedHeaderValue))
        {
            return new String[0];
        }

        return trimmedHeaderValue.split("\\s*,\\s*|\\s+");
    }
}
