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

import org.mule.config.spring.MuleHierarchicalBeanDefinitionParserDelegate;
import org.mule.config.spring.parsers.generic.AutoIdUtils;
import org.mule.util.TemplateParser;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

public abstract class AbstractDefinitionParser implements BeanDefinitionParser {

    private TemplateParser.PatternInfo patternInfo;

    public AbstractDefinitionParser() {
        patternInfo = TemplateParser.createMuleStyleParser().getStyle();
    }

    protected boolean hasAttribute(Element element, String attributeName) {
        String value = element.getAttribute(attributeName);
        if ((value!= null)&&(!StringUtils.isBlank(value))) {
            return true;
        }
        return false;
    }

    protected void setRef(BeanDefinitionBuilder builder, String propertyName, String ref) {
        if (!isMuleExpression(ref)) {
            builder.addPropertyValue(propertyName, new RuntimeBeanReference(ref));
        } else {
            builder.addPropertyValue(propertyName, ref);
        }
    }

    protected boolean isMuleExpression(String value) {
        if ((!value.startsWith(patternInfo.getPrefix()))&&(!value.endsWith(patternInfo.getSuffix()))) {
            return false;
        } else {
            return true;
        }
    }

    protected ManagedList parseList(Element element, String childElementName, AbstractDefinitionParser.ParseDelegate parserDelegate) {
        ManagedList managedList = new ManagedList();
        List<Element> childDomElements = DomUtils.getChildElementsByTagName(element, childElementName);
        for (Element childDomElement: childDomElements) {
            if (hasAttribute(childDomElement, "value-ref")) {
                if (!isMuleExpression(childDomElement.getAttribute("value-ref"))) {
                    managedList.add(new RuntimeBeanReference(childDomElement.getAttribute("value-ref")));
                } else {
                    managedList.add(childDomElement.getAttribute("value-ref"));
                }
            } else {
                managedList.add(parserDelegate.parse(childDomElement));
            }
        }
        return managedList;
    }

    protected void parseListAndSetProperty(Element element, BeanDefinitionBuilder builder, String fieldName, String parentElementName, String childElementName, AbstractDefinitionParser.ParseDelegate parserDelegate) {
        Element domElement = DomUtils.getChildElementByTagName(element, parentElementName);
        if (domElement!= null) {
            if (hasAttribute(domElement, "ref")) {
                setRef(builder, fieldName, domElement.getAttribute("ref"));
            } else {
                ManagedList managedList = parseList(domElement, childElementName, parserDelegate);
                builder.addPropertyValue(fieldName, managedList);
            }
        }
    }

    protected void parseConfigName(Element element) {
        if (hasAttribute(element, "name")) {
            element.setAttribute("name", AutoIdUtils.getUniqueName(element, "mule-bean"));
        }
    }

    protected void parseProperty(BeanDefinitionBuilder builder, Element element, String attributeName, String propertyName) {
        if (hasAttribute(element, attributeName)) {
            builder.addPropertyValue(propertyName, element.getAttribute(attributeName));
        }
    }

    protected void setNoRecurseOnDefinition(BeanDefinition definition) {
        definition.setAttribute(MuleHierarchicalBeanDefinitionParserDelegate.MULE_NO_RECURSE, Boolean.TRUE);
    }

    public interface ParseDelegate<T >{
        public T parse(Element element);
    }

}
