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
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.transport.ReplyToHandler;
import org.mule.modules.cors.Constants;

public class ValidateMessageProcessor extends AbstractValidateMessageProcessor
{
    @Override
    protected MuleEvent processRequest(MuleEvent event) throws MuleException
    {
        return event;
    }

    protected MuleEvent processResponse(final String origin, final String method, final String requestMethod, final String requestHeaders, MuleEvent event) throws MuleException
    {
        return corsPolicy.addHeaders(event, origin, method, requestMethod, requestHeaders);
    }

    @Override
    protected MuleEvent processBlocking(MuleEvent event) throws MuleException
    {
        MessagingException exception = null;

        final String origin = event.getMessage().getInboundProperty(Constants.ORIGIN);
        final String method = event.getMessage().getInboundProperty(Constants.HTTP_METHOD);
        final String requestMethod = event.getMessage().getInboundProperty(Constants.REQUEST_METHOD);
        final String requestHeaders = event.getMessage().getInboundProperty(Constants.REQUEST_HEADERS);

        try
        {
            return processResponse(origin, method, requestMethod, requestHeaders, processNext(processRequest(event)));
        }
        catch (MessagingException e)
        {
            exception = e;
            throw e;
        }
        finally
        {
            processFinally(event, exception);
        }
    }

    protected MuleEvent processNonBlocking(MuleEvent event) throws MuleException
    {

        final String origin = event.getMessage().getInboundProperty(Constants.ORIGIN);
        final String method = event.getMessage().getInboundProperty(Constants.HTTP_METHOD);
        final String requestMethod = event.getMessage().getInboundProperty(Constants.REQUEST_METHOD);
        final String requestHeaders = event.getMessage().getInboundProperty(Constants.REQUEST_HEADERS);

        final ReplyToHandler corsReplyToHandler = new CorsReplyToHandler(event.getReplyToHandler(), origin, method,
                                                                         requestMethod, requestHeaders);

        event = new DefaultMuleEvent(event, corsReplyToHandler);
        // Update RequestContext ThreadLocal for backwards compatibility
        OptimizedRequestContext.unsafeSetEvent(event);
        try
        {
            MuleEvent result = processNext(processRequest(event));
            if (!(result instanceof NonBlockingVoidMuleEvent))
            {
                return processResponse(origin, method, requestMethod, requestHeaders, result);
            }
            else
            {
                return result;
            }
        }
        catch (MessagingException exception)
        {
            processFinally(event, exception);
            throw exception;
        }
    }

    class CorsReplyToHandler implements ReplyToHandler
    {
        private final String origin;
        private final String method;
        private final String requestMethod;
        private final String requestHeaders;
        private final ReplyToHandler originalReplyToHandler;

        public CorsReplyToHandler(final ReplyToHandler originalReplyToHandler, final String origin, final String method,
                                  final String requestMethod, final String requestHeaders)
        {
            this.originalReplyToHandler = originalReplyToHandler;
            this.origin = origin;
            this.method = method;
            this.requestMethod = requestMethod;
            this.requestHeaders = requestHeaders;
        }

        @Override
        public void processReplyTo(MuleEvent event, MuleMessage muleMessage, Object replyTo) throws MuleException
        {
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
        public void processExceptionReplyTo(MessagingException exception, Object replyTo)
        {
            originalReplyToHandler.processExceptionReplyTo(exception, replyTo);
            processFinally(exception.getEvent(), exception);
        }
    }

}
