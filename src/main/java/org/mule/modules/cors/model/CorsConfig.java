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

package org.mule.modules.cors.model;

import org.mule.api.MuleContext;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.Disposable;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.store.ObjectAlreadyExistsException;
import org.mule.api.store.ObjectStore;
import org.mule.api.store.ObjectStoreException;
import org.mule.modules.cors.Constants;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CorsConfig implements Initialisable, Disposable, MuleContextAware
{

    protected transient Log logger = LogFactory.getLog(getClass());

    private String storePrefix;
    private List<Origin> origins;
    private ObjectStore<Origin> originsStore;
    private MuleContext muleContext;

    public Origin findOrigin(String origin)
    {
        try
        {
            //if origin is not present then don't add headers
            if (!getOriginsStore().contains(origin))
            {
                if (!getOriginsStore().contains(Constants.DEFAULT_ORIGIN_NAME))
                {
                    return null;
                }
                else
                {
                    return getOriginsStore().retrieve(Constants.DEFAULT_ORIGIN_NAME);
                }
            }

            return getOriginsStore().retrieve(origin);
        }
        catch (ObjectStoreException ose)
        {
            logger.warn("Error searching origin " + origin + " in object store. Error: " + ose.getMessage());
            return null;
        }
    }

    private String getObjectStoreName()
    {
        StringBuilder objectStoreName = new StringBuilder(muleContext.getConfiguration().getId());
        objectStoreName.append(Constants.ORIGINS_OBJECT_STORE);
        if (storePrefix != null)
        {
            objectStoreName.append(storePrefix);
        }
        else if (origins != null)
        {
            objectStoreName.append(origins.hashCode());
        }
        return objectStoreName.toString();
    }

    @Override
    public void initialise() throws InitialisationException
    {
        boolean newObjectStore = false;

        //no object store configured.
        if (this.originsStore == null)
        {
            this.originsStore = muleContext.getObjectStoreManager().getObjectStore(getObjectStoreName());
            newObjectStore = true;
        }

        //setup all configured object stores.
        if (this.origins == null)
        {
            return;
        }

        try
        {
            for (Origin o : origins)
            {

                if (originsStore.contains(o.getUrl()))
                {
                    if (newObjectStore)
                    {
                        originsStore.remove(o.getUrl());
                    }
                    else
                    {
                        continue;
                    }
                }
                safeStore(o);
            }
        }
        catch (ObjectStoreException ose)
        {
            throw new InitialisationException(ose, this);
        }

    }

    private void safeStore(Origin o) throws ObjectStoreException {
        try
        {
            originsStore.store(o.getUrl(), o);
        }
        catch (ObjectAlreadyExistsException e)
        {

        }
    }

    @Override
    public void dispose()
    {
        if (this.originsStore != null)
        {
            try
            {
                muleContext.getObjectStoreManager().disposeStore(this.originsStore);
            }
            catch (ObjectStoreException e)
            {
                logger.warn("Unable to dispose Object Store. Error: {}" + e.getMessage());
            }
            this.originsStore = null;
        }
    }

    @Override
    public void setMuleContext(MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }

    public String getStorePrefix()
    {
        return storePrefix;
    }

    public void setStorePrefix(String storePrefix)
    {
        this.storePrefix = storePrefix;
    }

    public List<Origin> getOrigins()
    {
        return origins;
    }

    public void setOrigins(List<Origin> origins)
    {
        this.origins = origins;
    }

    public ObjectStore<Origin> getOriginsStore()
    {
        return originsStore;
    }

    public void setOriginsStore(ObjectStore<Origin> originsStore)
    {
        this.originsStore = originsStore;
    }
}
