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

import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleRuntimeException;
import org.mule.api.ThreadSafeAccess;
import org.mule.api.context.MuleContextAware;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.lifecycle.Startable;
import org.mule.api.lifecycle.Stoppable;
import org.mule.api.source.MessageSource;
import org.mule.config.i18n.CoreMessages;
import org.mule.execution.FlowProcessingPhase;
import org.mule.execution.MessageProcessContext;
import org.mule.execution.MessageProcessPhase;
import org.mule.execution.MessageProcessTemplate;
import org.mule.execution.PhaseResultNotifier;
import org.mule.execution.ResponseCompletionCallback;
import org.mule.execution.ValidationPhase;
import org.mule.module.http.internal.listener.HttpMessageProcessorTemplate;
import org.mule.modules.cors.manager.CorsManager;
import org.mule.modules.cors.policy.CorsPolicy;
import org.mule.util.concurrent.Latch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CorsPhase implements MuleContextAware, MessageProcessPhase<HttpMessageProcessorTemplate>, Comparable<MessageProcessPhase>, Startable, Stoppable
{
    protected static transient Log logger = LogFactory.getLog(CorsPhase.class);

    private CorsManager corsManager;
    private Latch corsPhaseStarted = new Latch();
    private MuleContext muleContext;


    @Override
    public boolean supportsTemplate(MessageProcessTemplate messageProcessTemplate)
    {
        return messageProcessTemplate instanceof HttpMessageProcessorTemplate;
    }

    @Override
    public void runPhase(HttpMessageProcessorTemplate messageProcessTemplate, MessageProcessContext messageProcessContext, final PhaseResultNotifier phaseResultNotifier)
    {
        final MessageSource messageSource = messageProcessContext.getMessageSource();
        final MuleEvent lazyCreationMuleEvent = createLazyCreationMuleEvent(messageProcessTemplate);

        try
        {
            if (corsManager == null && !corsPhaseStarted.await(10, TimeUnit.SECONDS))
            {
                throw new MuleRuntimeException(CoreMessages.createStaticMessage("CorsManager not started yet"));
            }

            final CorsPolicy corsPolicy = corsManager.lookupPolicy(messageSource, lazyCreationMuleEvent);

            if(corsPolicy != null)
            {
                MuleEvent event = corsPolicy.validate(lazyCreationMuleEvent);
                if(event.getMessage().getInvocationProperty(CorsPolicy.CORS_STOP_PROCESSING_FLAG) != null)
                {
                    messageProcessTemplate.sendResponseToClient(event, new ResponseCompletionCallback()
                    {
                        @Override
                        public void responseSentSuccessfully()
                        {
                            phaseResultNotifier.phaseSuccessfully();
                        }

                        @Override
                        public void responseSentWithFailure(Exception e, MuleEvent event)
                        {
                            phaseResultNotifier.phaseFailure(e);
                        }
                    });
                    phaseResultNotifier.phaseConsumedMessage();
                    return;
                }
                else
                {
                    phaseResultNotifier.phaseSuccessfully();
                    return;
                }
            }
            else
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("No Cors policy found for endpoint class: " + messageSource.getClass().getName());
                    if (messageSource instanceof InboundEndpoint)
                    {
                        logger.debug("MessageSource is receiver with endpoint uri: " + ((InboundEndpoint) messageSource).getEndpointURI());
                    }
                }
                phaseResultNotifier.phaseSuccessfully();
                return;
            }

        }
        catch(Exception e)
        {
            logger.warn("Failure processing Cors phase " + e.getMessage());
            if (logger.isDebugEnabled())
            {
                logger.debug(e);
            }
            phaseResultNotifier.phaseFailure(e);
        }
    }

    private MuleEvent createLazyCreationMuleEvent(final HttpMessageProcessorTemplate processorTemplate)
    {
        InvocationHandler muleEventInvocationHandler = new InvocationHandler()
        {
            private MuleEvent event;

            @Override
            public Object invoke(Object o, Method method, Object[] objects) throws Throwable
            {
                if (event == null)
                {
                    event = processorTemplate.getMuleEvent();
                }
                return method.invoke(event, objects);
            }
        };
        return  (MuleEvent) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {MuleEvent.class, ThreadSafeAccess.class}, muleEventInvocationHandler);
    }


    // This phase has to be executed after the ValidationPhase and before any other phase.
    @Override
    public int compareTo(MessageProcessPhase messageProcessPhase)
    {
        if (messageProcessPhase instanceof ValidationPhase)
        {
            return 1;
        }
        return -1;
    }

    @Override
    public void start() throws MuleException
    {
        //Lookup manager at start Cors phase is created during initialisation it might be created before the the Cors manager
        corsManager = muleContext.getRegistry().get(CorsManager.OBJECT_CORS_MANAGER);
        corsPhaseStarted.release();
    }

    @Override
    public void stop() throws MuleException
    {
        corsPhaseStarted = new Latch();
    }

    @Override
    public void setMuleContext(MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }
}
