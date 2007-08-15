/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.beans.factory.config;

import java.util.Iterator;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

/**
 * {@link BeanFactoryPostProcessor} implementation that allows for convenient
 * registration of custom autowire qualifier types. It also allows for setting a
 * custom implementation of {@link AutowireCandidateResolver}.
 * 
 * <pre class="code">
 * &lt;bean id="customAutowireConfigurer" class="org.springframework.beans.factory.config.CustomAutowireConfigurer"&gt;
 *   &lt;property name="autowireCandidateResolver"&gt;
 *     &lt;bean class="mypackage.MyCustomAutowireCandidateResolver"/&gt;
 *   &lt;/property&gt;
 *   &lt;property name="customQualifierTypes"&gt;
 *     &lt;set&gt;
 *       &lt;value&gt;mypackage.MyQualifier&lt;/value&gt;
 *     &lt;/set&gt;
 *   &lt;/property&gt;
 * &lt;/bean&gt;</pre>
 * 
 * @author Mark Fisher
 * @since 2.1
 * @see AutowireCandidateResolver
 * @see org.springframework.beans.factory.annotation.Qualifier
 */
public class CustomAutowireConfigurer implements BeanFactoryPostProcessor, BeanClassLoaderAware, Ordered {

	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered

	private AutowireCandidateResolver autowireCandidateResolver;

	private Set customQualifierTypes;

	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();


	public void setOrder(int order) {
		this.order = order;
	}

	public int getOrder() {
		return this.order;
	}

	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	public void setAutowireCandidateResolver(AutowireCandidateResolver autowireCandidateResolver) {
		this.autowireCandidateResolver = autowireCandidateResolver;
	}

	public void setCustomQualifierTypes(Set customQualifierTypes) {
		this.customQualifierTypes = customQualifierTypes;
	}

	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (this.autowireCandidateResolver != null) {
			beanFactory.setAutowireCandidateResolver(autowireCandidateResolver);
		}
		if (this.customQualifierTypes != null) {
			for (Iterator it = customQualifierTypes.iterator(); it.hasNext();) {
				Class customType = null;
				Object value = it.next();
				if (value instanceof Class) {
					customType = (Class) value;
				}
				else if (value instanceof String) {
					String className = (String) value;
					customType = ClassUtils.resolveClassName(className, this.beanClassLoader);
				}
				else {
					throw new IllegalArgumentException(
							"Invalid value [" + value + "] for custom qualifier type: needs to be Class or String.");
				}
				beanFactory.registerQualifierType(customType);
			}
		}
	}

}
