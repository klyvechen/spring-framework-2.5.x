/*
 * Copyright 2002-2005 the original author or authors.
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

package org.springframework.beans.factory.access;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * <p>Keyed-singleton implementation of BeanFactoryLocator, which leverages existing
 * Spring constructs. This is normally accessed through DefaultLocatorFactory,
 * but may also be used directly.</p>
 *
 * <p>Please see the warning in BeanFactoryLocator's javadoc about appropriate usage
 * of singleton style BeanFactoryLocator implementations. It is the opinion of the 
 * Spring team that the use of this class and similar classes is unnecessary except
 * (sometimes) for a small amount of glue code. Excessive usage will lead to code
 * that is more tightly coupled, and harder to modify or test.</p>
 *
 * <p>In this implementation, a BeanFactory is built up from one or more XML
 * definition file fragments, accessed as resources. The default resource name
 * searched for is 'classpath*:beanRefFactory.xml', with the Spring-standard
 * 'classpath*:' prefix ensuring that if the classpath contains multiple copies
 * of this file (perhaps one in each component jar) they will be combined. To
 * override the default resource name, instead of using the no-arg 
 * {@link #getInstance()} method, use the {@link #getInstance(String selector)}
 * variant, which will treat the 'selector' argument as the resource name to
 * search for.</p>
 * 
 * <p>The purpose of this 'outer' BeanFactory is to create and hold a copy of one
 * or more 'inner' BeanFactory or ApplicationContext instances, and allow those
 * to be obtained either directly or via an alias. As such, this class provides
 * both singleton style access to one or more BeanFactories/ApplicationContexts,
 * and also a level of indirection, allowing multiple pieces of code, which are
 * not able to work in a Dependency Injection fashion, to refer to and use the
 * same target BeanFactory/ApplicationContext instance(s), by different names.<p>
 *
 * <p>Consider an example application scenario:
 *
 * <ul>
 * <li><code>com.mycompany.myapp.util.applicationContext.xml</code> -
 * ApplicationContext definition file which defines beans for 'util' layer.
 * <li><code>com.mycompany.myapp.dataaccess-applicationContext.xml</code> -
 * ApplicationContext definition file which defines beans for 'data access' layer.
 * Depends on the above.
 * <li><code>com.mycompany.myapp.services.applicationContext.xml</code> -
 * ApplicationContext definition file which defines beans for 'services' layer.
 * Depends on the above.
 * </ul>
 *
 * <p>In an ideal scenario, these would be combined to create one ApplicationContext,
 * or created as three hierarchical ApplicationContexts, by one piece of code
 * somewhere at application startup (perhaps a Servlet filter), from which all other
 * code in the application would flow, obtained as beans from the context(s). However
 * when third party code enters into the picture, things can get problematic. If the 
 * third party code needs to create user classes, which should normally be obtained
 * from a Spring BeanFactory/ApplicationContext, but can handle only newInstance()
 * style object creation, then some extra work is required to actually access and 
 * use object from a BeanFactory/ApplicationContext. One solutions is to make the
 * class created by the third party code be just a stub or proxy, which gets the
 * real object from a BeanFactory/ApplicationContext, and delegates to it. However,
 * it is is not normally workable for the stub to create the BeanFactory on each
 * use, as depending on what is inside it, that can be an expensive operation.
 * Additionally, there is a fairly tight coupling between the stub and the name of
 * the definition resource for the BeanFactory/ApplicationContext. This is where
 * SingletonBeanFactoryLocator comes in. The stub can obtain a
 * SingletonBeanFactoryLocator instance, which is effectively a singleton, and
 * ask it for an appropriate BeanFactory. A subsequent invocation (assuming the
 * same class loader is involved) by the stub or another piece of code, will obtain
 * the same instance. The simple aliasing mechanism allows the context to be asked
 * for by a name which is appropriate for (or describes) the user. The deployer can
 * match alias names to actual context names.
 *
 * <p>Another use of SingletonBeanFactoryLocator, is to demand-load/use one or more
 * BeanFactories/ApplicationContexts. Because the definiiton can contain one of more
 * BeanFactories/ApplicationContexts, which can be independent or in a hierarchy, if 
 * they are set to lazy-initialize, they will only be created when actually requested
 * for use.
 *
 * <p>Given the above-mentioned three ApplicationContexts, consider the simplest
 * SingletonBeanFactoryLocator usage scenario, where there is only one single
 * <code>beanRefFactory.xml</code> definition file:
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?>
 * &lt;!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
 * 
 * &lt;beans>
 * 
 *   &lt;bean id="com.mycompany.myapp"
 *         class="org.springframework.context.support.ClassPathXmlApplicationContext">
 *     &lt;constructor-arg>
 *       &lt;list>
 *         &lt;value>com/mycompany/myapp/util/applicationContext.xml&lt;/value>
 *         &lt;value>com/mycompany/myapp/dataaccess/applicationContext.xml&lt;/value>
 *         &lt;value>com/mycompany/myapp/dataaccess/services.xml&lt;/value>
 *       &lt;/list>
 *     &lt;/constructor-arg>
 *   &lt;/bean>
 * 
 * &lt;/beans>
 * </pre>
 *
 * The client code is as simple as:
 *
 * <pre>
 * BeanFactoryLocator bfl = SingletonBeanFactoryLocator.getInstance();
 * BeanFactoryReference bf = bfl.useBeanFactory("com.mycompany.myapp");
 * // now use some bean from factory 
 * MyClass zed = bf.getFactory().getBean("mybean");
 * </pre>
 *
 * Another relatively simple variation of the <code>beanRefFactory.xml</code> definition file could be:
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?>
 * &lt;!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
 * 
 * &lt;beans>
 * 
 *   &lt;bean id="com.mycompany.myapp.util" lazy-init="true"
 *         class="org.springframework.context.support.ClassPathXmlApplicationContext">
 *     &lt;constructor-arg>
 *       &lt;value>com/mycompany/myapp/util/applicationContext.xml&lt;/value>
 *     &lt;/constructor-arg>
 *   &lt;/bean>
 * 
 *   &lt;!-- child of above -->
 *   &lt;bean id="com.mycompany.myapp.dataaccess" lazy-init="true"
 *         class="org.springframework.context.support.ClassPathXmlApplicationContext">
 *     &lt;constructor-arg>
 *       &lt;list>&lt;value>com/mycompany/myapp/dataaccess/applicationContext.xml&lt;/value>&lt;/list>
 *     &lt;/constructor-arg>
 *     &lt;constructor-arg>
 *       &lt;ref bean="com.mycompany.myapp.util"/>
 *     &lt;/constructor-arg>
 *   &lt;/bean>
 * 
 *   &lt;!-- child of above -->
 *   &lt;bean id="com.mycompany.myapp.services" lazy-init="true"
 *         class="org.springframework.context.support.ClassPathXmlApplicationContext">
 *     &lt;constructor-arg>
 *       &lt;list>&lt;value>com/mycompany/myapp/dataaccess.services.xml&lt;/value>&lt;/value>
 *     &lt;/constructor-arg>
 *     &lt;constructor-arg>
 *       &lt;ref bean="com.mycompany.myapp.dataaccess"/>
 *     &lt;/constructor-arg>
 *   &lt;/bean>
 * 
 *   &lt;!-- define an alias -->
 *   &lt;bean id="com.mycompany.myapp.mypackage"
 *         class="java.lang.String">
 *     &lt;constructor-arg>
 *       &lt;value>com.mycompany.myapp.services&lt;/value>
 *     &lt;/constructor-arg>
 *   &lt;/bean>
 * 
 * &lt;/beans>
 * </pre>
 *
 * <p>In this example, there is a hierarchy of three contexts created. The (potential)
 * advantage is that if the lazy flag is set to true, a context will only be created
 * if it's actually used. If there is some code that is only needed some of the time,
 * this mechanism can save some resources. Additionally, an alias to the last context
 * has been created. Aliases allow usage of the idiom where client code asks for a
 * context with an id which represents the package or module the code is in, and the
 * actual definition file(s) for the SingletonBeanFactoryLocator maps that id to
 * a real context id.
 *
 * <p>A final example is more complex, with a <code>beanRefFactory.xml</code> for every module.
 * All the files are automatically combined to create the final definition.
 *
 * <p><code>beanRefFactory.xml</code> file inside jar for util module:
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?>
 * &lt;!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
 * 
 * &lt;beans>
 *   &lt;bean id="com.mycompany.myapp.util" lazy-init="true"
 *        class="org.springframework.context.support.ClassPathXmlApplicationContext">
 *     &lt;constructor-arg>
 *       &lt;value>com/mycompany/myapp/util/applicationContext.xml&lt;/value>
 *     &lt;/constructor-arg>
 *   &lt;/bean>
 * &lt;/beans>
 * </pre>
 * 
 * <code>beanRefFactory.xml</code> file inside jar for data-access module:<br>
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?>
 * &lt;!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
 * 
 * &lt;beans>
 *   &lt;!-- child of util -->
 *   &lt;bean id="com.mycompany.myapp.dataaccess" lazy-init="true"
 *        class="org.springframework.context.support.ClassPathXmlApplicationContext">
 *     &lt;constructor-arg>
 *       &lt;list>&lt;value>com/mycompany/myapp/dataaccess/applicationContext.xml&lt;/value>&lt;/list>
 *     &lt;/constructor-arg>
 *     &lt;constructor-arg>
 *       &lt;ref bean="com.mycompany.myapp.util"/>
 *     &lt;/constructor-arg>
 *   &lt;/bean>
 * &lt;/beans>
 * </pre>
 * 
 * <code>beanRefFactory.xml</code> file inside jar for services module:
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?>
 * &lt;!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
 * 
 * &lt;beans>
 *   &lt;!-- child of data-access -->
 *   &lt;bean id="com.mycompany.myapp.services" lazy-init="true"
 *        class="org.springframework.context.support.ClassPathXmlApplicationContext">
 *     &lt;constructor-arg>
 *       &lt;list>&lt;value>com/mycompany/myapp/dataaccess/services.xml&lt;/value>&lt;/list>
 *     &lt;/constructor-arg>
 *     &lt;constructor-arg>
 *       &lt;ref bean="com.mycompany.myapp.dataaccess"/>
 *     &lt;/constructor-arg>
 *   &lt;/bean>
 * &lt;/beans>
 * </pre>
 * 
 * <code>beanRefFactory.xml</code> file inside jar for mypackage module. This doesn't
 * create any of its own contexts, but allows the other ones to be referred to be
 * a name known to this module:
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?>
 * &lt;!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
 * 
 * &lt;beans>
 *   &lt;!-- define an alias for "com.mycompany.myapp.services" -->
 *   &lt;alias name="com.mycompany.myapp.services" alias="com.mycompany.myapp.mypackage"/&gt;
 * &lt;/beans>
 * </pre>
 *   
 * @author Colin Sampaleanu
 * @see org.springframework.context.access.DefaultLocatorFactory
 */
public class SingletonBeanFactoryLocator implements BeanFactoryLocator {

	public static final String BEANS_REFS_XML_NAME = "classpath*:beanRefFactory.xml";

	protected static final Log logger = LogFactory.getLog(SingletonBeanFactoryLocator.class);

	// the keyed singleton instances
	private static Map instances = new HashMap();


	// We map BeanFactoryGroup objects by String keys, and by the definition object.
	private final Map bfgInstancesByKey = new HashMap();

	private final Map bfgInstancesByObj = new HashMap();

	private final String resourceName;


	/**
	 * Returns an instance which uses the default "classpath*:beanRefFactory.xml",
	 * as the name of the definition file(s). All resources returned by calling the
	 * current thread's context classloader's getResources() method with this name
	 * will be combined to create a definition, which is just a BeanFactory.
	 */
	public static BeanFactoryLocator getInstance() throws FatalBeanException {
		return getInstance(BEANS_REFS_XML_NAME);
	}

	/**
	 * Returns an instance which uses the the specified selector, as the name of the
	 * definition file(s). In the case of a name with a Spring 'classpath*:' prefix,
	 * or with no prefix, which is treated the same, the current thread's context
	 * classloader's getResources() method will be called with this value to get all
	 * resources having that name. These resources will then be combined to form a
	 * definition. In the case where the name uses a Spring 'classpath:' prefix, or
	 * a standard URL prefix, then only one resource file will be loaded as the
	 * definition.
	 * @param selector the name of the resource(s) which will be read and combine to
	 * form the definition for the SingletonBeanFactoryLocator instance. The one file
	 * or multiple fragments with this name must form a valid BeanFactory definition.
	 */
	public static BeanFactoryLocator getInstance(String selector) throws FatalBeanException {
		// For backwards compatibility, we prepend 'classpath*:' to the selector name if there
		// is no other prefix (i.e. classpath*:, classpath:, or some URL prefix.
		if (selector.indexOf(':') == -1) {
			selector = ResourcePatternResolver.CLASSPATH_URL_PREFIX + selector;
		}

		synchronized (instances) {
			if (logger.isDebugEnabled()) {
				logger.debug("SingletonBeanFactoryLocator.getInstance(): instances.hashCode=" +
						instances.hashCode() + ", instances=" + instances);
			}
			BeanFactoryLocator bfl = (BeanFactoryLocator) instances.get(selector);
			if (bfl == null) {
				bfl = new SingletonBeanFactoryLocator(selector);
				instances.put(selector, bfl);
			}
			return bfl;
		}
	}


	/**
	 * Constructor which uses the default "beanRefFactory.xml", as the name of the
	 * definition file(s). All resources returned by the definition classloader's
	 * getResources() method with this name will be combined to create a definition.
	 */
	protected SingletonBeanFactoryLocator() {
		this.resourceName = BEANS_REFS_XML_NAME;
	}

	/**
	 * Constructor which uses the the specified name as the name of the
	 * definition file(s). All resources returned by the definition classloader's
	 * getResources() method with this name will be combined to create a definition
	 * definition.
	 */
	protected SingletonBeanFactoryLocator(String resourceName) {
		this.resourceName = resourceName;
	}

	public BeanFactoryReference useBeanFactory(String factoryKey) throws BeansException {
		synchronized (this.bfgInstancesByKey) {
			BeanFactoryGroup bfg = (BeanFactoryGroup) this.bfgInstancesByKey.get(this.resourceName);

			if (bfg != null) {
				bfg.refCount++;
			}
			else {
				// This group definition doesn't exist, we need to try to load it.
				if (logger.isDebugEnabled()) {
					logger.debug("Factory group with resource name [" + this.resourceName +
							"] requested. Creating new instance.");
				}
				
				// Create the BeanFactory but don't initialize it.
				BeanFactory groupContext = createDefinition(this.resourceName, factoryKey);

				// Record its existence now, before instantiating any singletons.
				bfg = new BeanFactoryGroup();
				bfg.definition = groupContext;
				bfg.refCount = 1;
				this.bfgInstancesByKey.put(this.resourceName, bfg);
				this.bfgInstancesByObj.put(groupContext, bfg);

				// Now initialize the BeanFactory. This may cause a re-entrant invocation
				// of this method, but since we've already added the BeanFactory to our
				// mappings, the next time it will be found and simply have its
				// reference count incremented.
				try {
					initializeDefinition(groupContext);
				}
				catch (BeansException ex) {
					throw new BootstrapException("Unable to initialize group definition. " +
						"Group resource name [" + this.resourceName + "], factory key [" + factoryKey + "]", ex);
				}
			}

			final BeanFactory groupContext = bfg.definition;

			String beanName = factoryKey;
			Object bean;
			try {
				bean = groupContext.getBean(beanName);
				if (bean instanceof String) {
					logger.warn("You're using the deprecated alias-through-String-bean feature, " +
							"which will be removed as of Spring 1.3. It is recommended to replace this " +
							"with an <alias> tag (see SingletonBeanFactoryLocator javadoc).");
					beanName = (String) bean;
					bean = groupContext.getBean(beanName);
				}
			}
			catch (BeansException ex) {
				throw new BootstrapException("Unable to return specified BeanFactory instance: factory key [" +
						factoryKey + "], from group with resource name [" + this.resourceName + "]", ex);
			}

			if (!(bean instanceof BeanFactory)) {
				throw new BootstrapException("Bean '" + beanName + "' is not a BeanFactory: factory key [" +
						factoryKey + "], from group with resource name [" + this.resourceName + "]");
			}

			final BeanFactory beanFactory = (BeanFactory) bean;

			return new BeanFactoryReference() {
				
				BeanFactory groupContextRef;
				
				// constructor
				{
					this.groupContextRef = groupContext;
				}
				
				public BeanFactory getFactory() {
					return beanFactory;
				}

				// Note that it's legal to call release more than once!
				public void release() throws FatalBeanException {
					synchronized (bfgInstancesByKey) {
						BeanFactory savedRef = this.groupContextRef;
						if (savedRef != null) {
							this.groupContextRef = null;
							BeanFactoryGroup bfg = (BeanFactoryGroup) bfgInstancesByObj.get(savedRef);
							if (bfg != null) {
								bfg.refCount--;
								if (bfg.refCount == 0) {
									destroyDefinition(savedRef, resourceName);
									bfgInstancesByKey.remove(resourceName);
									bfgInstancesByObj.remove(savedRef);
								}
							}
							else {
								// This should be impossible.
								logger.warn("Tried to release a SingletonBeanFactoryLocator group definition " +
										"more times than it has actually been used. Resource name [" + resourceName + "]");
							}
						}
					}
				}
			};
		}
	}

	/**
	 * Actually creates definition in the form of a BeanFactory, given a resource name
	 * which supports standard Spring Resource prefixes ('classpath:', 'classpath*:', etc.)
	 * This is split out as a separate method so that subclasses can override the actual
	 * type used (to be an ApplicationContext, for example).
	 * <p>This method should not instantiate any singletons. That function is performed
	 * by {@link #initializeDefinition initializeDefinition()}, which should also be
	 * overridden if this method is.
	 */
	protected BeanFactory createDefinition(String resourceName, String factoryKey) throws BeansException {
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
		ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

		try {
			Resource[] configResources = resourcePatternResolver.getResources(resourceName);
			if (configResources.length == 0) {
				throw new FatalBeanException("Unable to find resource for specified definition. " +
						"Group resource name [" + this.resourceName + "], factory key [" + factoryKey + "]");
			}
			reader.loadBeanDefinitions(configResources);
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(
					"Error accessing bean definition resource [" + this.resourceName + "]", ex);
		}
		catch (BeanDefinitionStoreException ex) {
			throw new FatalBeanException("Unable to load group definition: " +
					"group resource name [" + this.resourceName + "], factory key [" + factoryKey + "]", ex);
		}

		return factory;
	}
	
	/**
	 * Instantiate singletons and do any other normal initialization of the factory.
	 * Subclasses that override {@link #createDefinition createDefinition()} should
	 * also override this method.
	 * @param groupDef the factory returned by {@link #createDefinition createDefinition()}
	 */
	protected void initializeDefinition(BeanFactory groupDef) throws BeansException {
		if (groupDef instanceof ConfigurableListableBeanFactory) {
			((ConfigurableListableBeanFactory) groupDef).preInstantiateSingletons();
		}
	}

	/**
	 * Destroy definition in separate method so subclass may work with other definition types.
	 */
	protected void destroyDefinition(BeanFactory groupDef, String resourceName) throws BeansException {
		if (groupDef instanceof ConfigurableBeanFactory) {
			// debugging trace only
			if (logger.isDebugEnabled()) {
				logger.debug("Factory group with resource name '" + resourceName +
						"' being released, as there are no more references to it.");
			}
			((ConfigurableBeanFactory) groupDef).destroySingletons();
		}
	}


	// We track BeanFactory instances with this class.
	private static class BeanFactoryGroup {

		private BeanFactory definition;

		private int refCount = 0;
	}

}
