/*
 * Copyright 2002-2006 the original author or authors.
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

package org.springframework.test.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.springframework.orm.jpa.ExtendedEntityManagerCreator;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.ContainerEntityManagerFactoryBean;
import org.springframework.test.annotation.AbstractAnnotationAwareTransactionalTests;
import org.springframework.beans.BeansException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.instrument.classloading.AbstractLoadTimeWeaver;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Convenient support class for JPA-related tests.
 *
 * <p>Exposes an EntityManagerFactory and a shared EntityManager.
 * Requires EntityManagerFactory to be injected, plus DataSource and
 * JpaTransactionManager from superclass.
 *
 * @author Rod Johnson
 * @since 2.0
 */
public abstract class AbstractJpaTests extends AbstractAnnotationAwareTransactionalTests {

	private static Class shadowedTestClass;

	private static Object cachedContext;

	private static ShadowingClassLoader shadowingClassLoader;

	protected EntityManagerFactory entityManagerFactory;

	private boolean shadowed = false;
	/**
	 * Subclasses can use this in test cases.
	 * It will participate in any current transaction.
	 */
	protected EntityManager sharedEntityManager;


	public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
		this.sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(
				this.entityManagerFactory, EntityManager.class);
	}

	/**
	 * Create an EntityManager that will always automatically enlist itself in current
	 * transactions, in contrast to an EntityManager returned by
	 * <code>EntityManagerFactory.createEntityManager()</code>
	 * (which requires an explicit <code>joinTransaction()</code> call).
	 */
	protected EntityManager createContainerManagedEntityManager() {
		return ExtendedEntityManagerCreator.createContainerManagedEntityManager(this.entityManagerFactory);
	}

	@Override
	public void runBare() throws Throwable {
		ClassLoader classLoader = getClass().getClassLoader();
		if (this.shadowed) {
			Thread.currentThread().setContextClassLoader(classLoader);
			super.runBare();
		}
		else {
			if(shadowingClassLoader == null) {
			 shadowingClassLoader = new ShadowingClassLoader(classLoader);
			}
			Thread.currentThread().setContextClassLoader(shadowingClassLoader);
			String[] configLocations = getConfigLocations();

			if (shadowedTestClass == null) {

				// create load time weaver
				Class shadowingLoadTimeWeaverClass = shadowingClassLoader.loadClass(ShadowingLoadTimeWeaver.class.getName());
				Class shadowedShadowingClassLoaderClass = ShadowingClassLoader.class;
				Constructor constructor = shadowingLoadTimeWeaverClass.getConstructor(ClassLoader.class);
				constructor.setAccessible(true);
				Object ltw = constructor.newInstance(shadowingClassLoader);

				// create the bean factory
				Class beanFactoryClass = shadowingClassLoader.loadClass(DefaultListableBeanFactory.class.getName());
				Object beanFactory = BeanUtils.instantiateClass(beanFactoryClass);

				// create the BeanDefinitionReader
				Class beanDefinitionReaderClass = shadowingClassLoader.loadClass(XmlBeanDefinitionReader.class.getName());
				Class beanDefinitionRegistryClass = shadowingClassLoader.loadClass(BeanDefinitionRegistry.class.getName());
				Object reader = beanDefinitionReaderClass.getConstructor(beanDefinitionRegistryClass).newInstance(beanFactory);

				// load the bean definitions into the bean factory
				Method loadBeanDefinitions = beanDefinitionReaderClass.getMethod("loadBeanDefinitions", String[].class);
				loadBeanDefinitions.invoke(reader, new Object[]{configLocations});

				// create BeanPostProcessor
				Class loadTimeWeaverInjectingBeanPostProcessorClass = shadowingClassLoader.loadClass(LoadTimeWeaverInjectingBeanPostProcessor.class.getName());
				Class loadTimeWeaverClass = shadowingClassLoader.loadClass(LoadTimeWeaver.class.getName());
				Constructor bppConstructor = loadTimeWeaverInjectingBeanPostProcessorClass.getConstructor(loadTimeWeaverClass);
				bppConstructor.setAccessible(true);
				Object beanPostProcessor = bppConstructor.newInstance(ltw);

				// add BeanPostProcessor
				Class beanPostProcessorClass = shadowingClassLoader.loadClass(BeanPostProcessor.class.getName());
				Method addBeanPostProcessor = beanFactoryClass.getMethod("addBeanPostProcessor", beanPostProcessorClass);
				addBeanPostProcessor.invoke(beanFactory, beanPostProcessor);

				// create the GenericApplicationContext
				Class genericApplicationContextClass = shadowingClassLoader.loadClass(GenericApplicationContext.class.getName());
				Class defaultListableBeanFactoryClass = shadowingClassLoader.loadClass(DefaultListableBeanFactory.class.getName());
				cachedContext = genericApplicationContextClass.getConstructor(defaultListableBeanFactoryClass).newInstance(beanFactory);

				// refresh
				genericApplicationContextClass.getMethod("refresh").invoke(cachedContext);

				// create the shadowed test
				shadowedTestClass = shadowingClassLoader.loadClass(getClass().getName());
			}
			Object testCase = BeanUtils.instantiateClass(shadowedTestClass);

			/* shadowed = true */
			Class thisShadowedClass = shadowingClassLoader.loadClass(AbstractJpaTests.class.getName());
			Field shadowed = thisShadowedClass.getDeclaredField("shadowed");
			shadowed.setAccessible(true);
			shadowed.set(testCase, true);

			/* AbstractSpringContextTests.addContext(Object, ApplicationContext) */
			Class applicationContextClass = shadowingClassLoader.loadClass(ConfigurableApplicationContext.class.getName());
			Method addContextMethod = shadowedTestClass.getMethod("addContext", Object.class, applicationContextClass);
			addContextMethod.invoke(testCase, configLocations, cachedContext);

			/* TestCase.setName(String) */
			Method setNameMethod = shadowedTestClass.getMethod("setName", String.class);
			setNameMethod.invoke(testCase, getName());

			/* TestCase.runBare() */
			Method testMethod = shadowedTestClass.getMethod("runBare");
			testMethod.invoke(testCase, null);
		}
	}

	private static class LoadTimeWeaverInjectingBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {

		private final LoadTimeWeaver ltw;

		public LoadTimeWeaverInjectingBeanPostProcessor(LoadTimeWeaver ltw) {
			this.ltw = ltw;
		}

		public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
			if (bean instanceof ContainerEntityManagerFactoryBean) {
				((ContainerEntityManagerFactoryBean) bean).setLoadTimeWeaver(ltw);
			}
			return bean;
		}
	}

	private static class ShadowingLoadTimeWeaver extends AbstractLoadTimeWeaver {

		private final ClassLoader shadowingClassLoader;

		private final Class shadowingClassLoaderClass;

		public ShadowingLoadTimeWeaver(ClassLoader shadowingClassLoader) {
			this.shadowingClassLoader = shadowingClassLoader;
			this.shadowingClassLoaderClass = shadowingClassLoader.getClass();
		}

		public ClassLoader getInstrumentableClassLoader() {
			return (ClassLoader) shadowingClassLoader;
		}

		public void addClassFileTransformer(ClassFileTransformer classFileTransformer) {
			try {
				Method addClassFileTransformer = shadowingClassLoaderClass.getMethod("addClassFileTransformer", ClassFileTransformer.class);
				addClassFileTransformer.setAccessible(true);
				addClassFileTransformer.invoke(shadowingClassLoader, classFileTransformer);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}
}
