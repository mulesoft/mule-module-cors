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
import org.mule.api.MuleException;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.lifecycle.Stoppable;
import org.mule.api.store.ObjectStore;
import org.mule.api.store.ObjectStoreException;

import java.util.List;

public class CorsConfig implements Initialisable, Stoppable, MuleContextAware
{
    private String storePrefix;
    private List<Origin> origins;
    private ObjectStore<Origin> originsStore;
    private MuleContext muleContext;

    @Override
    public void initialise() throws InitialisationException
    {
        boolean newObjectStore = false;

        //no object store configured.
        if (this.originsStore == null) {

            //if (logger.isDebugEnabled()) logger.debug("No object store configured, defaulting to " + Constants.ORIGINS_OBJECT_STORE);

            String appName = muleContext.getConfiguration().getId();
            this.originsStore = muleContext.getObjectStoreManager().getObjectStore(appName + Constants.ORIGINS_OBJECT_STORE + storePrefix);
            newObjectStore = true;
        }

        //setup all configured object stores.
        if (this.origins == null) {
            //if (logger.isDebugEnabled()) logger.debug("No initial set of origins configured.");
            return;
        }

        try {
            for(Origin o : origins) {

                //if (logger.isDebugEnabled()) {
                //    logger.debug("Configuring origin: " + o.getUrl());
                //}

                if (originsStore.contains(o.getUrl()))
                {
                    if (newObjectStore) {
                        originsStore.remove(o.getUrl());
                    } else {
                        //logger.warn("Object Store already contains " + o.getUrl());
                        continue;
                    }
                }
                originsStore.store(o.getUrl(), o);
            }
        } catch(ObjectStoreException ose) {
            throw new InitialisationException(ose, this);
        }

    }

    @Override
    public void setMuleContext(MuleContext muleContext) {
        this.muleContext = muleContext;
    }

    @Override
    public void stop() throws MuleException {
        this.originsStore = null;
    }

    public String getStorePrefix() {
        return storePrefix;
    }

    public void setStorePrefix(String storePrefix) {
        this.storePrefix = storePrefix;
    }

    public List<Origin> getOrigins() {
        return origins;
    }

    public void setOrigins(List<Origin> origins) {
        this.origins = origins;
    }

    public ObjectStore<Origin> getOriginsStore() {
        return originsStore;
    }

    public void setOriginsStore(ObjectStore<Origin> originsStore) {
        this.originsStore = originsStore;
    }
}
