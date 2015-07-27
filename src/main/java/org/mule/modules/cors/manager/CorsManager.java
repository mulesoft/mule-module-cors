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

package org.mule.modules.cors.manager;

import org.mule.api.MuleEvent;
import org.mule.api.source.MessageSource;
import org.mule.modules.cors.lookup.CompositeCorsPolicyLookup;
import org.mule.modules.cors.lookup.CorsPolicyLookup;
import org.mule.modules.cors.policy.CorsPolicy;

public class CorsManager implements CorsPolicyLookup
{
    public static final String OBJECT_CORS_MANAGER = "_muleCorsManager";

    private CompositeCorsPolicyLookup compositeCorsPolicyLookup = new CompositeCorsPolicyLookup();

    @Override
    public CorsPolicy lookupPolicy(MessageSource messageSource, MuleEvent muleEvent)
    {
        return compositeCorsPolicyLookup.lookupPolicy(messageSource, muleEvent);
    }

    public void removeCorsPolicyLookup(CorsPolicyLookup lookup)
    {
        compositeCorsPolicyLookup.removeCorsPolicyLookup(lookup);
    }

    public void addCorsPolicyLookup(CorsPolicyLookup lookup)
    {
        compositeCorsPolicyLookup.addCorsPolicyLookup(lookup);
    }
}
