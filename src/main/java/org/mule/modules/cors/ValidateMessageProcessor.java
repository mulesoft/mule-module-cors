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

import org.mule.DefaultMuleEvent;
import org.mule.NonBlockingVoidMuleEvent;
import org.mule.OptimizedRequestContext;
import org.mule.VoidMuleEvent;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.config.MuleProperties;
import org.mule.api.store.ObjectStoreException;
import org.mule.api.transport.PropertyScope;
import org.mule.api.transport.ReplyToHandler;
import org.mule.processor.AbstractRequestResponseMessageProcessor;

import org.apache.commons.lang.StringUtils;

public class ValidateMessageProcessor extends AbstractRequestResponseMessageProcessor {

    private static final String STOP_PROCESSING_FLAG = MuleProperties.PROPERTY_PREFIX + "__stopProcessing";

    private boolean publicResource;
    private boolean acceptsCredentials;
    private CorsConfig config;

    @Override
    protected MuleEvent processRequest(MuleEvent event) throws MuleException {
        if (publicResource && acceptsCredentials) {
            throw new IllegalArgumentException("Resource may not be public and accept credentials at the same time");
        }

        //read the origin
        String origin = event.getMessage().getInboundProperty(Constants.ORIGIN);

        //if origin is not present, then not a CORS request
        if (StringUtils.isEmpty(origin)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Request is not a CORS request.");
            }
            return event;
        }

        //read headers including those of the preflight
        String method = event.getMessage().getInboundProperty(Constants.HTTP_METHOD);

        //decide if we want to invoke the flow.
        if (shouldInvokeFlow(origin, method, publicResource)) {
            return event;
        }

        //setting the response to null.
        event.getMessage().setPayload(null);
        //Set flag to stop further processing. I need this workaround because there are some headers
        //that might be set on the response and returning null or VoidMuleEvent won't allow it.
        event.getMessage().setInvocationProperty(STOP_PROCESSING_FLAG, true);

        return event;
    }

    @Override
    protected MuleEvent processNext(MuleEvent event) throws MuleException
    {
        if(event != null && !VoidMuleEvent.getInstance().equals(event)
                && Boolean.TRUE.equals(event.getMessage().getInvocationProperty(STOP_PROCESSING_FLAG))) {
            event.getMessage().removeProperty(STOP_PROCESSING_FLAG, PropertyScope.INVOCATION);
            return event;
        }

        return super.processNext(event);
    }

    protected MuleEvent processResponse(final String origin, final String method, final String requestMethod, final String requestHeaders, MuleEvent event) throws MuleException
    {
        MuleMessage message = event.getMessage();

        if(StringUtils.isEmpty(origin)) {
            return event;
        }

        boolean isPreflight = StringUtils.equals(Constants.PREFLIGHT_METHOD, method);

        //if the resource is public then we don't check
        if (publicResource) {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            //and if it is a preflight call
            if (isPreflight) {
                //protect ourselves against 'non-standard' requests
                if (requestMethod != null) {
                    message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_METHODS, requestMethod);
                }

                //protect ourselves against 'non-standard' requests
                if (requestHeaders != null) {
                    message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_HEADERS, requestHeaders);
                }
            }
            //no further processing
            return event;
        }
        Origin configuredOrigin = findOrigin(origin);

        //no matching origin has been found.
        if (configuredOrigin == null) {
            return event;
        }

        String checkMethod = isPreflight ? requestMethod : method;

        //if the method is not present, then we don't allow.
        if (configuredOrigin.getMethods() == null || !configuredOrigin.getMethods().contains(checkMethod)) {
            return event;
        }

        //add the allow origin
        message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN, origin);

        //if the resource accepts credentials
        if (acceptsCredentials) {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        //if this is not a preflight, then we don't want to add the other headers
        if (!isPreflight) {
            return event;
        }

        //serialize the list of allowed methods
        if (configuredOrigin.getMethods() != null) {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_METHODS, StringUtils.join(configuredOrigin.getMethods(), ", "));
        }

        //serialize the list of allowed headers
        if (configuredOrigin.getHeaders() != null) {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_HEADERS, StringUtils.join(configuredOrigin.getHeaders(), ", "));
        }

        //serialize the list of allowed headers
        if (configuredOrigin.getExposeHeaders() != null) {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_EXPOSE_HEADERS, StringUtils.join(configuredOrigin.getExposeHeaders(), ", "));
        }

        //set the configured max age for this origin
        if (configuredOrigin.getAccessControlMaxAge() != null) {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_MAX_AGE, configuredOrigin.getAccessControlMaxAge());
        }

        return event;
    }

    @Override
    protected MuleEvent processBlocking(MuleEvent event) throws MuleException {
        MessagingException exception = null;

        final String origin = event.getMessage().getInboundProperty(Constants.ORIGIN);
        final String method = event.getMessage().getInboundProperty(Constants.HTTP_METHOD);
        final String requestMethod = event.getMessage().getInboundProperty(Constants.REQUEST_METHOD);
        final String requestHeaders = event.getMessage().getInboundProperty(Constants.REQUEST_HEADERS);

        try {
            return processResponse(origin, method, requestMethod, requestHeaders, processNext(processRequest(event)));
        } catch (MessagingException e) {
            exception = e;
            throw e;
        } finally {
            processFinally(event, exception);
        }
    }

    protected MuleEvent processNonBlocking(MuleEvent event) throws MuleException {

        final String origin = event.getMessage().getInboundProperty(Constants.ORIGIN);
        final String method = event.getMessage().getInboundProperty(Constants.HTTP_METHOD);
        final String requestMethod = event.getMessage().getInboundProperty(Constants.REQUEST_METHOD);
        final String requestHeaders = event.getMessage().getInboundProperty(Constants.REQUEST_HEADERS);

        final ReplyToHandler corsReplyToHandler = new CorsReplyToHandler(event.getReplyToHandler(), origin, method,
                                                                         requestMethod, requestHeaders);

        event = new DefaultMuleEvent(event, corsReplyToHandler);
        // Update RequestContext ThreadLocal for backwards compatibility
        OptimizedRequestContext.unsafeSetEvent(event);
        try {
            MuleEvent result = processNext(processRequest(event));
            if (!(result instanceof NonBlockingVoidMuleEvent)) {
                return processResponse(origin, method, requestMethod, requestHeaders, result);
            } else {
                return result;
            }
        } catch (MessagingException exception) {
            processFinally(event, exception);
            throw exception;
        }
    }

    class CorsReplyToHandler implements ReplyToHandler {

        private final String origin;
        private final String method;
        private final String requestMethod;
        private final String requestHeaders;
        private final ReplyToHandler originalReplyToHandler;

        public CorsReplyToHandler(final ReplyToHandler originalReplyToHandler, final String origin, final String method,
                                  final String requestMethod, final String requestHeaders) {
            this.originalReplyToHandler = originalReplyToHandler;
            this.origin = origin;
            this.method = method;
            this.requestMethod = requestMethod;
            this.requestHeaders = requestHeaders;
        }

        @Override
        public void processReplyTo(MuleEvent event, MuleMessage muleMessage, Object replyTo) throws MuleException {
            MuleEvent response = processResponse(origin, method, requestMethod, requestHeaders,
                                                 new DefaultMuleEvent(event, originalReplyToHandler));
            // Update RequestContext ThreadLocal for backwards compatibility
            OptimizedRequestContext.unsafeSetEvent(response);
            if (!NonBlockingVoidMuleEvent.getInstance().equals(response))
            {
                originalReplyToHandler.processReplyTo(response, null, null);
            }
            processFinally(event, null);
        }

        @Override
        public void processExceptionReplyTo(MessagingException exception, Object replyTo) {
            originalReplyToHandler.processExceptionReplyTo(exception, replyTo);
            processFinally(exception.getEvent(), exception);
        }
    }


    private boolean shouldInvokeFlow(String origin, String method, boolean publicResource) throws ObjectStoreException {

        //if it is the preflight request, then logic wont be invoked.
        if (StringUtils.equals(Constants.PREFLIGHT_METHOD, method)) {
            if (logger.isDebugEnabled()) logger.debug("OPTIONS header, will not continue processing.");
            return false;
        }

        //if it is a public resource and not preflight, then let's do it :)
        if (publicResource) {
            return true;
        }

        Origin configuredOrigin = findOrigin(origin);

        if (configuredOrigin == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Could not find configuration for origin: " + origin);
            }
            return false;
        }

        //verify the allowed methods.
        if (configuredOrigin.getMethods() != null) {
            return configuredOrigin.getMethods().contains(method);
        } else {
            logger.warn("Configured origin has no methods. Not allowing the execution of the flow");
            return false;
        }
    }

    private Origin findOrigin(String origin) throws ObjectStoreException{
        //if origin is not present then don't add headers
        if (!config.getOriginsStore().contains(origin)) {
            if (!config.getOriginsStore().contains(Constants.DEFAULT_ORIGIN_NAME)) {
                return null;
            } else {
                return config.getOriginsStore().retrieve(Constants.DEFAULT_ORIGIN_NAME);
            }
        }

        return config.getOriginsStore().retrieve(origin);
    }

    public void setPublicResource(boolean publicResource) {
        this.publicResource = publicResource;
    }

    public void setAcceptsCredentials(boolean acceptsCredentials) {
        this.acceptsCredentials = acceptsCredentials;
    }

    public void setConfig(CorsConfig config) {
        this.config = config;
    }
}
