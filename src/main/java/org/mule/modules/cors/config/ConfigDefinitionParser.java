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

package org.mule.modules.cors.config;

import org.mule.modules.cors.CorsConfig;
import org.mule.modules.cors.Origin;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

public class ConfigDefinitionParser extends AbstractDefinitionParser {

    public BeanDefinition parse(Element element, ParserContext parserContext) {
        parseConfigName(element);
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(CorsConfig.class.getName());
        builder.setScope(BeanDefinition.SCOPE_SINGLETON);
        parseProperty(builder, element, "storePrefix", "storePrefix");
        parseListAndSetProperty(element, builder, "origins", "origins", "origin", new ParseDelegate<BeanDefinition>() {
                public BeanDefinition parse(Element element) {
                    BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(Origin.class);
                    parseProperty(builder, element, "url", "url");
                    parseListAndSetProperty(element, builder, "methods", "methods", "method", new ParseDelegate<String>() {
                            public String parse(Element element) {
                                return element.getTextContent();
                            }
                        }
                    );
                    parseListAndSetProperty(element, builder, "headers", "headers", "header", new ParseDelegate<String>() {
                            public String parse(Element element) {
                                return element.getTextContent();
                            }
                        }
                    );
                    parseListAndSetProperty(element, builder, "exposeHeaders", "expose-headers", "expose-header", new ParseDelegate<String>() {
                            public String parse(Element element) {
                                return element.getTextContent();
                            }
                        }
                    );
                    parseProperty(builder, element, "accessControlMaxAge", "accessControlMaxAge");
                    return builder.getBeanDefinition();
                }

            }
        );
        if (hasAttribute(element, "originsStore-ref")) {
            if (element.getAttribute("originsStore-ref").startsWith("#")) {
                builder.addPropertyValue("originsStore", element.getAttribute("originsStore-ref"));
            } else {
                builder.addPropertyValue("originsStore", new RuntimeBeanReference(element.getAttribute("originsStore-ref")));
            }
        }
        BeanDefinition definition = builder.getBeanDefinition();
        setNoRecurseOnDefinition(definition);
        return definition;
    }
}
