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
import org.mule.modules.cors.model.CorsConfig;
import org.mule.modules.cors.policy.CorsPolicy;
import org.mule.modules.cors.policy.MuleCorsPolicy;

public class FlowBasedPolicyLookup implements CorsPolicyLookup
{
    private final String flowName;
    private final CorsConfig corsConfig;
    private final boolean publicResource;
    private final boolean acceptsCredentials;

    public FlowBasedPolicyLookup(final String flowName, final CorsConfig corsConfig, final boolean publicResource, final boolean acceptsCredentials)
    {
        this.flowName = flowName;
        this.corsConfig = corsConfig;
        this.publicResource = publicResource;
        this.acceptsCredentials = acceptsCredentials;
    }

    @Override
    public CorsPolicy lookupPolicy(MessageSource messageSource, MuleEvent muleEvent)
    {
        if(muleEvent.getFlowConstruct().getName().equals(flowName))
        {
            return new MuleCorsPolicy(corsConfig, publicResource, acceptsCredentials);
        }

        return null;

    }
}
