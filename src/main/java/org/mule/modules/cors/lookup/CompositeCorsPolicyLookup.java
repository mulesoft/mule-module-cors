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

import org.mule.api.MuleEvent;
import org.mule.api.source.MessageSource;
import org.mule.modules.cors.policy.CorsPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CompositeCorsPolicyLookup implements CorsPolicyLookup
{
    protected transient Log logger = LogFactory.getLog(getClass());
    private List<CorsPolicyLookup> lookups = new ArrayList<CorsPolicyLookup>();
    private ReadWriteLock lookupsLock = new ReentrantReadWriteLock();


    @Override
    public CorsPolicy lookupPolicy(MessageSource messageSource, MuleEvent muleEvent)
    {
        CorsPolicy corsPolicy = null;
        Lock lock = lookupsLock.readLock();
        lock.lock();
        try
        {
            for (CorsPolicyLookup corsPolicyLookup : lookups)
            {
                try
                {
                    corsPolicy = corsPolicyLookup.lookupPolicy(messageSource, muleEvent);
                }
                catch (Exception e)
                {
                    logger.warn("Failed looking for cors policy " + e.getMessage());
                    if (logger.isDebugEnabled())
                    {
                        logger.debug(e);
                    }
                }
                if (corsPolicy != null)
                {
                    break;
                }
            }
        }
        finally
        {
            lock.unlock();
        }
        return corsPolicy;
    }

    public void addCorsPolicyLookup(CorsPolicyLookup lookup)
    {
        Lock lock = lookupsLock.writeLock();
        lock.lock();
        try
        {
            lookups.add(lookup);
        }
        finally
        {
            lock.unlock();
        }
    }

    public void removeCorsPolicyLookup(CorsPolicyLookup lookup)
    {
        Lock lock = lookupsLock.writeLock();
        lock.lock();
        try
        {
            lookups.remove(lookup);
        }
        finally
        {
            lock.unlock();
        }
    }
}
