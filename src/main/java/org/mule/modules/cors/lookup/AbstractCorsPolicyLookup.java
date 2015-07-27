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

package org.mule.modules.cors.lookup;

import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.Disposable;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.source.MessageSource;
import org.mule.modules.cors.manager.CorsManager;
import org.mule.modules.cors.policy.CorsPolicy;

public abstract class AbstractCorsPolicyLookup implements CorsPolicyLookup, MuleContextAware, Initialisable, Disposable
{
    private MuleContext muleContext;

    @Override
    public void setMuleContext(MuleContext context)
    {
        this.muleContext = context;
    }

    protected MuleContext getMuleContext()
    {
        return muleContext;
    }

    @Override
    public final void initialise() throws InitialisationException
    {
        CorsManager corsManager = muleContext.getRegistry().get(CorsManager.OBJECT_CORS_MANAGER);
        corsManager.addCorsPolicyLookup(this);
        doInitialize();
    }

    protected void doInitialize()
    {
    }

    @Override
    public final void dispose()
    {
        doDispose();
        CorsManager corsManager = muleContext.getRegistry().get(CorsManager.OBJECT_CORS_MANAGER);
        corsManager.removeCorsPolicyLookup(this);
    }

    protected void doDispose()
    {
    }

    public abstract CorsPolicy lookupPolicy(MessageSource messageSource, MuleEvent muleEvent);

}
