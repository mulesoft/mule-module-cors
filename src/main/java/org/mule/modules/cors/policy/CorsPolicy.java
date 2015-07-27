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

package org.mule.modules.cors.policy;

import org.mule.api.MuleEvent;
import org.mule.api.config.MuleProperties;

public interface CorsPolicy
{
    String CORS_STOP_PROCESSING_FLAG = MuleProperties.PROPERTY_PREFIX + "__corsStopProcessing";

    MuleEvent validate(MuleEvent muleEvent);

    MuleEvent addHeaders(MuleEvent muleEvent, final String origin, final String method, final String requestMethod, final String requestHeaders);

}
