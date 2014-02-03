/*
 * Copyright 2014 juancavallotti.
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

import org.apache.commons.lang.StringUtils;
import org.mule.api.MuleEvent;
import org.mule.api.annotations.lifecycle.Start;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.Optional;
import org.mule.api.store.ObjectStore;
import org.mule.api.store.ObjectStoreException;
import org.mule.api.store.ObjectStoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mule.api.MuleMessage;
import org.mule.api.annotations.*;
import org.mule.api.callback.SourceCallback;

import javax.inject.Inject;
import java.util.List;

/**
 * Cloud Connector
 *
 * @author MuleSoft, Inc.
 */
@Module(name="cors", schemaVersion="1.0-SNAPSHOT")
public class CORSModule
{

    private static final Logger logger = LoggerFactory.getLogger(CORSModule.class);

    @Inject
    private ObjectStoreManager objectStoreManager;

    /**
     * The initial list of supported origins that will be introduced into the origins object store.
     */
    @Configurable
    @Optional
    private List<Origin> origins;


    /**
     * The object store used for storing the origins.
     */
    @Configurable
    @Optional
    private ObjectStore<Origin> originsStore;


    @Start
    public void initializeModule() throws ObjectStoreException{

        //no object store configured.
        if (this.originsStore == null) {

            if (logger.isDebugEnabled()) logger.debug("No object store configured, defaulting to " + Constants.ORIGINS_OBJECT_STORE);

            this.originsStore = objectStoreManager.getObjectStore(Constants.ORIGINS_OBJECT_STORE);
        }

        //setup all configured object stores.
        if (this.origins == null) {
            if (logger.isDebugEnabled()) logger.debug("No initial set of origins configured.");
            return;
        }

        for(Origin o : origins) {

            if (logger.isDebugEnabled()) {
                logger.debug("Configuring origin: " + o.getUrl());
            }

            originsStore.store(o.getUrl(), o);

        }

    }


    /**
     * Perform CORS validation. This operation will add the necessary CORS headers to the response. If the request method
     * is OPTIONS it will not perform further processing of the message.
     *
     * If this request is not a CORS request, then the processing will continue without altering the message.
     *
     * {@sample.xml ../../../doc/CORSModule-connector.xml.sample cors:validate}
     *
     * @param callback the source callback for continuing the execution.
     * @param event the mule event.
     * @param publicResource specifies if this resource should be publicly available regardless the origin.
     * @param acceptsCredentials specifies whether the resource accepts credentials or not.
     * @return the resulting event
     * @throws Exception propagate any exception thrown by next message processors.
     */
    @Processor(intercepting = true)
    @Inject
    public MuleMessage validate(SourceCallback callback, MuleEvent event, @Optional @Default("false")
        boolean publicResource, @Optional @Default("false") boolean acceptsCredentials) throws Exception {

        if (publicResource && acceptsCredentials) {
            throw new IllegalArgumentException("Resource may not be public and accept credentials at the same time");
        }


        MuleMessage message = event.getMessage();

        //read the origin
        String origin = message.getInboundProperty(Constants.ORIGIN);

        //if origin is not present, then not a CORS request
        if (StringUtils.isEmpty(origin)) {

            if (logger.isDebugEnabled()) logger.debug("Request is not a CORS request.");
            return callback.processEvent(event).getMessage();
        }

        //read headers including those of the preflight
        String method = message.getInboundProperty(Constants.HTTP_METHOD);
        String requestMethod = message.getInboundProperty(Constants.REQUEST_METHOD);
        String requestHeaders = message.getInboundProperty(Constants.REQUEST_HEADERS);


        MuleEvent result = event;

        //decide if we want to invoke the flow.
        if (shouldInvokeFlow(origin, method, publicResource)) {
            result = callback.processEvent(result);
        } else {
            //setting the response to null.
            event.getMessage().setPayload(null);
        }


        //finally configure the CORS headers
        configureCorsHeaders(event.getMessage(), method, origin, requestMethod, requestHeaders, publicResource, acceptsCredentials);

        return result.getMessage();
    }

    private void configureCorsHeaders(MuleMessage message, String method, String origin, String requestMethod,
                                      String requestHeaders, boolean publicResource, boolean acceptsCredentials) throws ObjectStoreException {

        boolean isPreflight = StringUtils.equals(Constants.PREFLIGHT_METHOD, method);

        //if the resource is public then we don't check
        if (publicResource) {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            //and if it is a preflight call
            if (isPreflight) {
                message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_METHODS, requestMethod);
                message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_HEADERS, requestHeaders);
            }
            //no further processing
            return;
        }


        //if origin is not present then don't add headers
        if (!originsStore.contains(origin)) {
            return;
        }

        Origin configuredOrigin = originsStore.retrieve(origin);

        String checkMethod = isPreflight ? requestMethod : method;

        //if the method is not present, then we don't allow.
        if (configuredOrigin.getMethods() == null || !configuredOrigin.getMethods().contains(checkMethod)) {
            return;
        }


        //add the allow origin
        message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_ORIGIN, origin);

        //if the resource accepts credentials
        if (acceptsCredentials) {
            message.setOutboundProperty(Constants.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }


        //if this is not a preflight, then we don't want to add the other headers
        if (!isPreflight) {
            return;
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

        if (!originsStore.contains(origin)) {
            if (logger.isDebugEnabled()) logger.debug("Origin not configured: " + origin);
            return false;
        }

        //verify the allowed methods.
        Origin configuredOrigin = originsStore.retrieve(origin);

        if (configuredOrigin.getMethods() != null) {
            return configuredOrigin.getMethods().contains(method);
        } else {
            logger.warn("Configured origin has no methods. Not allowing the execution of the flow");
            return false;
        }
    }


    //GETTERS AND SETTERS
    public ObjectStore<Origin> getOriginsStore() {
        return originsStore;
    }

    public void setOriginsStore(ObjectStore<Origin> originsStore) {
        this.originsStore = originsStore;
    }

    public List<Origin> getOrigins() {
        return origins;
    }

    public void setOrigins(List<Origin> origins) {
        this.origins = origins;
    }

    public ObjectStoreManager getObjectStoreManager() {
        return objectStoreManager;
    }

    public void setObjectStoreManager(ObjectStoreManager objectStoreManager) {
        this.objectStoreManager = objectStoreManager;
    }
}
