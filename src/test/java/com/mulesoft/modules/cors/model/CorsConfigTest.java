package com.mulesoft.modules.cors.model;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.MuleConfiguration;
import org.mule.api.store.ObjectStore;
import org.mule.api.store.ObjectStoreManager;
import org.mule.module.http.api.HttpConstants;
import org.mule.modules.cors.Constants;
import org.mule.modules.cors.model.CorsConfig;
import org.mule.modules.cors.model.Origin;
import org.mule.util.store.InMemoryObjectStore;

import java.util.Arrays;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CorsConfigTest
{
    private static final String APP_NAME = "app";
    private static final String DOMAIN = "http://example.com";
    private static final String PREFIX = "prefix";


    private MuleContext muleContext;
    private ObjectStoreManager objectStoreManager;
    private CorsConfig corsConfig;
    private MuleConfiguration muleConfiguration;

    @Before
    public void setUp()
    {
        muleContext = Mockito.mock(MuleContext.class);
        objectStoreManager = Mockito.mock(ObjectStoreManager.class);
        muleConfiguration = Mockito.mock(MuleConfiguration.class);
        Mockito.when(muleContext.getConfiguration()).thenReturn(muleConfiguration);
        Mockito.when(muleConfiguration.getId()).thenReturn(APP_NAME);
        Mockito.when(muleContext.getObjectStoreManager()).thenReturn(objectStoreManager);
        Mockito.when(objectStoreManager.getObjectStore(any(String.class))).thenReturn(new InMemoryObjectStore());
        corsConfig = new CorsConfig();
        corsConfig.setMuleContext(muleContext);
        Origin origin = new Origin();
        origin.setUrl(DOMAIN);
        origin.setMethods(Arrays.asList(HttpConstants.Methods.POST.toString()));
        corsConfig.setOrigins(Arrays.asList(origin));
    }

    @Test
    public void objectStoreNameWithoutPrefix() throws Exception
    {
        corsConfig.initialise();
        ArgumentCaptor<String> objectStoreCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(objectStoreManager).getObjectStore(objectStoreCaptor.capture());
        Assert.assertEquals(APP_NAME + Constants.ORIGINS_OBJECT_STORE + corsConfig.getOrigins().hashCode(),objectStoreCaptor.getValue());
    }

    @Test
    public void objectStoreNameWithPrefix() throws Exception
    {
        corsConfig.setStorePrefix(PREFIX);
        corsConfig.initialise();
        ArgumentCaptor<String> objectStoreCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(objectStoreManager).getObjectStore(objectStoreCaptor.capture());
        Assert.assertEquals(APP_NAME + Constants.ORIGINS_OBJECT_STORE + PREFIX,objectStoreCaptor.getValue());
    }

    @Test
    public void stopTwice() throws MuleException
    {
        corsConfig.initialise();
        corsConfig.stop();
        corsConfig.stop();

        verify(objectStoreManager,times(1)).disposeStore(any(ObjectStore.class));
    }
}
