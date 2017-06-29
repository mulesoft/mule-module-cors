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

package org.mule.modules.cors.processors;

import org.mule.DefaultMuleEvent;
import org.mule.NonBlockingVoidMuleEvent;
import org.mule.OptimizedRequestContext;
import org.mule.VoidMuleEvent;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transport.ReplyToHandler;
import org.mule.module.http.api.HttpConstants;
import org.mule.module.http.api.HttpHeaders;
import org.mule.modules.cors.Constants;
import org.mule.modules.cors.CorsFilter;
import org.mule.modules.cors.MuleCorsFilter;
import org.mule.modules.cors.model.CorsConfig;
import org.mule.processor.AbstractRequestResponseMessageProcessor;


public class ValidateMessageProcessor extends AbstractRequestResponseMessageProcessor implements Initialisable {

    protected boolean publicResource;
    protected boolean acceptsCredentials;
    protected CorsConfig config;
    protected CorsFilter corsFilter;

    @Override
    protected MuleEvent processRequest(MuleEvent event) throws MuleException {
        return corsFilter.filter(event);
    }

    @Override
    protected MuleEvent processNext(MuleEvent event) throws MuleException
    {
        if(event != null && !VoidMuleEvent.getInstance().equals(event)
                && Boolean.TRUE.equals(event.getMessage().getInvocationProperty(Constants.CORS_STOP_PROCESSING_FLAG))) {
            return event;
        }

        return super.processNext(event);
    }

    protected MuleEvent processResponse(final String origin, final String method, final String requestMethod, final String requestHeaders, MuleEvent event) throws MuleException
    {
        corsFilter.addHeaders(event, origin, method, requestMethod, requestHeaders);
        return event;
    }

    @Override
    protected MuleEvent processBlocking(MuleEvent event) throws MuleException {
        MessagingException exception = null;

        final String origin = event.getMessage().getInboundProperty(HttpHeaders.Names.ORIGIN);
        final String method = event.getMessage().getInboundProperty(HttpConstants.RequestProperties.HTTP_METHOD_PROPERTY);
        final String requestMethod = event.getMessage().getInboundProperty(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD);
        final String requestHeaders = event.getMessage().getInboundProperty(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS);

        try {
            return processResponse(origin, method, requestMethod, requestHeaders, processNext(processRequest(event)));
        } catch (MessagingException e) {
            exception = e;
            throw e;
        } finally {
            processFinally(event, exception, origin, method, requestMethod, requestHeaders);
        }
    }

    protected void processFinally(MuleEvent event, MessagingException exception, final String origin, final String method, final String requestMethod, final String requestHeaders)
    {
        super.processFinally(event, exception);
        corsFilter.addHeaders(exception == null ? event : exception.getEvent(), origin, method, requestMethod, requestHeaders);
    }

    protected MuleEvent processNonBlocking(MuleEvent event) throws MuleException {

        final String origin = event.getMessage().getInboundProperty(HttpHeaders.Names.ORIGIN);
        final String method = event.getMessage().getInboundProperty(HttpConstants.RequestProperties.HTTP_METHOD_PROPERTY);
        final String requestMethod = event.getMessage().getInboundProperty(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD);
        final String requestHeaders = event.getMessage().getInboundProperty(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS);

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
            processFinally(event, exception, origin, method, requestMethod, requestHeaders);
            throw exception;
        }
    }

    @Override
    public void initialise() throws InitialisationException
    {
        if (publicResource && acceptsCredentials)
        {
            throw new IllegalArgumentException("Resource may not be public and accept credentials at the same time");
        }

        corsFilter = new MuleCorsFilter(config, publicResource, acceptsCredentials);
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
            processFinally(exception.getEvent(), exception, origin, method, requestMethod, requestHeaders);
        }
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
