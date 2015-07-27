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

package org.mule.modules.cors.policy;

import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.modules.cors.Constants;
import org.mule.modules.cors.model.CorsConfig;
import org.mule.modules.cors.model.Origin;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MuleCorsPolicy implements CorsPolicy
{
    protected transient Log logger = LogFactory.getLog(getClass());

    private final CorsConfig config;
    private final boolean publicResource;
    private final boolean acceptsCredentials;

    public MuleCorsPolicy(final CorsConfig corsConfig, final boolean publicResource, final boolean acceptsCredentials)
    {
        this.config = corsConfig;
        this.publicResource = publicResource;
        this.acceptsCredentials = acceptsCredentials;
    }

    @Override
    public MuleEvent validate(MuleEvent event)
    {
        //read the origin
        String origin = event.getMessage().getInboundProperty(Constants.ORIGIN);

        //if origin is not present, then not a CORS request
        if (StringUtils.isEmpty(origin))
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Request is not a CORS request.");
            }
            return event;
        }

        //read headers including those of the preflight
        String method = event.getMessage().getInboundProperty(Constants.HTTP_METHOD);

        //decide if we want to invoke the flow.
        if (shouldInvokeFlow(origin, method, publicResource))
        {
            return event;
        }

        //setting the response to null.
        event.getMessage().setPayload(null);
        // Add CORS Headers now since we are stopping further execution
        final String requestMethod = event.getMessage().getInboundProperty(Constants.REQUEST_METHOD);
        final String requestHeaders = event.getMessage().getInboundProperty(Constants.REQUEST_HEADERS);
        addHeaders(event, origin, method, requestMethod, requestHeaders);
        //Set flag to stop further processing.
        event.getMessage().setInvocationProperty(CorsPolicy.CORS_STOP_PROCESSING_FLAG, true);

        return event;
    }

    @Override
    public MuleEvent addHeaders(MuleEvent event, String origin, String method, String requestMethod, String requestHeaders)
    {
        MuleMessage message = event.getMessage();

        if(StringUtils.isEmpty(origin))
        {
            return event;
        }

        boolean isPreflight = StringUtils.equals(Constants.PREFLIGHT_METHOD, method);

        //if the resource is public then we don't check
        if (publicResource)
        {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            //and if it is a preflight call
            if (isPreflight)
            {
                //protect ourselves against 'non-standard' requests
                if (requestMethod != null)
                {
                    message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_METHODS, requestMethod);
                }

                //protect ourselves against 'non-standard' requests
                if (requestHeaders != null)
                {
                    message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_HEADERS, requestHeaders);
                }
            }
            //no further processing
            return event;
        }
        Origin configuredOrigin = config.findOrigin(origin);

        //no matching origin has been found.
        if (configuredOrigin == null)
        {
            return event;
        }

        String checkMethod = isPreflight ? requestMethod : method;

        //if the method is not present, then we don't allow.
        if (configuredOrigin.getMethods() == null || !configuredOrigin.getMethods().contains(checkMethod))
        {
            return event;
        }

        //add the allow origin
        message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN, origin);

        //if the resource accepts credentials
        if (acceptsCredentials)
        {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        //if this is not a preflight, then we don't want to add the other headers
        if (!isPreflight)
        {
            return event;
        }

        //serialize the list of allowed methods
        if (configuredOrigin.getMethods() != null)
        {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_METHODS, StringUtils.join(configuredOrigin.getMethods(), ", "));
        }

        //serialize the list of allowed headers
        if (configuredOrigin.getHeaders() != null)
        {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_HEADERS, StringUtils.join(configuredOrigin.getHeaders(), ", "));
        }

        //serialize the list of allowed headers
        if (configuredOrigin.getExposeHeaders() != null)
        {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_EXPOSE_HEADERS, StringUtils.join(configuredOrigin.getExposeHeaders(), ", "));
        }

        //set the configured max age for this origin
        if (configuredOrigin.getAccessControlMaxAge() != null)
        {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_MAX_AGE, configuredOrigin.getAccessControlMaxAge());
        }

        return event;
    }

    private boolean shouldInvokeFlow(String origin, String method, boolean publicResource)
    {

        //if it is the preflight request, then logic wont be invoked.
        if (StringUtils.equals(Constants.PREFLIGHT_METHOD, method))
        {
            if (logger.isDebugEnabled()) logger.debug("OPTIONS header, will not continue processing.");
            return false;
        }

        //if it is a public resource and not preflight, then let's do it :)
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

        //verify the allowed methods.
        if (configuredOrigin.getMethods() != null)
        {
            return configuredOrigin.getMethods().contains(method);
        }
        else
        {
            logger.warn("Configured origin has no methods. Not allowing the execution of the flow");
            return false;
        }
    }
}
