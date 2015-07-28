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

import org.mule.api.MuleException;
import org.mule.api.construct.FlowConstruct;
import org.mule.api.construct.FlowConstructAware;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.Disposable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.lifecycle.Lifecycle;
import org.mule.api.lifecycle.Startable;
import org.mule.api.lifecycle.Stoppable;
import org.mule.modules.cors.lookup.CorsPolicyLookup;
import org.mule.modules.cors.lookup.FlowBasedPolicyLookup;
import org.mule.modules.cors.manager.CorsManager;
import org.mule.modules.cors.model.CorsConfig;
import org.mule.modules.cors.policy.CorsPolicy;
import org.mule.modules.cors.policy.MuleCorsPolicy;
import org.mule.processor.AbstractRequestResponseMessageProcessor;

public abstract class AbstractValidateMessageProcessor extends AbstractRequestResponseMessageProcessor implements FlowConstructAware, MuleContextAware, Lifecycle
{
    protected boolean publicResource;
    protected boolean acceptsCredentials;
    protected CorsConfig config;
    protected FlowConstruct flowConstruct;
    protected CorsPolicyLookup lookup;
    protected CorsPolicy corsPolicy;

    @Override
    public void dispose()
    {
        if (lookup != null)
        {
            CorsManager corsManager = muleContext.getRegistry().get(CorsManager.OBJECT_CORS_MANAGER);
            if (corsManager != null)
            {
                corsManager.removeCorsPolicyLookup(lookup);
                if (lookup instanceof Disposable)
                {
                    ((Disposable) lookup).dispose();
                }
            }
        }
    }

    @Override
    public void setFlowConstruct(FlowConstruct flowConstruct)
    {
        this.flowConstruct = flowConstruct;
    }

    @Override
    public void initialise() throws InitialisationException
    {
        if (publicResource && acceptsCredentials)
        {
            throw new IllegalArgumentException("Resource may not be public and accept credentials at the same time");
        }

        corsPolicy = new MuleCorsPolicy(config, publicResource, acceptsCredentials);

        if (lookup == null && flowConstruct != null)
        {
            lookup = new FlowBasedPolicyLookup(flowConstruct.getName(), config, publicResource, acceptsCredentials);
        }

        CorsManager corsManager = muleContext.getRegistry().get(CorsManager.OBJECT_CORS_MANAGER);
        corsManager.addCorsPolicyLookup(lookup);
    }

    @Override
    public void start() throws MuleException
    {
        if (lookup != null && lookup instanceof Startable)
        {
            ((Startable) lookup).start();
        }
    }

    @Override
    public void stop() throws MuleException
    {
        if (lookup != null && lookup instanceof Stoppable)
        {
            ((Stoppable) lookup).stop();
        }
    }

    public void setPublicResource(boolean publicResource)
    {
        this.publicResource = publicResource;
    }

    public void setAcceptsCredentials(boolean acceptsCredentials)
    {
        this.acceptsCredentials = acceptsCredentials;
    }

    public void setConfig(CorsConfig config)
    {
        this.config = config;
    }

}
