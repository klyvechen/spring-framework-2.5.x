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

package org.springframework.jms.listener;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.jms.connection.ConnectionFactoryUtils;
import org.springframework.jms.connection.JmsResourceHolder;
import org.springframework.jms.support.JmsUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Base class for listener container implementations which are based on polling.
 * Provides support for listener handling based on {@link javax.jms.MessageConsumer},
 * optionally participating in externally managed transactions.
 *
 * <p>This listener container variant is built for repeated polling attempts,
 * each invoking the {@link #receiveAndExecute} method. The MessageConsumer used
 * may be reobtained fo reach attempt or cached inbetween attempts; this is up
 * to the concrete implementation. The receive timeout for each attempt can be
 * configured through the {@link #setReceiveTimeout "receiveTimeout"} property.
 *
 * <p>The underlying mechanism is based on standard JMS MessageConsumer handling,
 * which is perfectly compatible with both native JMS and JMS in a J2EE environment.
 * Neither the JMS <code>MessageConsumer.setMessageListener</code> facility
 * nor the JMS ServerSessionPool facility is required. A further advantage
 * of this approach is full control over the listening process, allowing for
 * custom scaling and throttling and of concurrent message processing
 * (which is up to concrete subclasses).
 *
 * <p>Message reception and listener execution can automatically be wrapped
 * in transactions through passing a Spring
 * {@link org.springframework.transaction.PlatformTransactionManager} into the
 * {@link #setTransactionManager "transactionManager"} property. This will usually
 * be a {@link org.springframework.transaction.jta.JtaTransactionManager} in a
 * J2EE enviroment, in combination with a JTA-aware JMS ConnectionFactory obtained
 * from JNDI (check your J2EE server's documentation).
 *
 * <p>This base class does not assume any specific mechanism for asynchronous
 * execution of polling invokers. Check out {@link DefaultMessageListenerContainer}
 * for a concrete implementation which is based on Spring's
 * {@link org.springframework.core.task.TaskExecutor} abstraction,
 * including dynamic scaling of concurrent consumers and automatic self recovery.
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see #createListenerConsumer(javax.jms.Session)
 * @see #receiveAndExecute(javax.jms.Session, javax.jms.MessageConsumer)
 * @see #setTransactionManager
 */
public abstract class AbstractPollingMessageListenerContainer extends AbstractMessageListenerContainer
		implements BeanNameAware {

	/**
	 * The default receive timeout: 1000 ms = 1 second.
	 */
	public static final long DEFAULT_RECEIVE_TIMEOUT = 1000;


	private final MessageListenerContainerResourceFactory transactionalResourceFactory =
			new MessageListenerContainerResourceFactory();

	private boolean pubSubNoLocal = false;

	private PlatformTransactionManager transactionManager;

	private DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();

	private long receiveTimeout = DEFAULT_RECEIVE_TIMEOUT;


	/**
	 * Set whether to inhibit the delivery of messages published by its own connection.
	 * Default is "false".
	 * @see javax.jms.TopicSession#createSubscriber(javax.jms.Topic, String, boolean)
	 */
	public void setPubSubNoLocal(boolean pubSubNoLocal) {
		this.pubSubNoLocal = pubSubNoLocal;
	}

	/**
	 * Return whether to inhibit the delivery of messages published by its own connection.
	 */
	protected boolean isPubSubNoLocal() {
		return this.pubSubNoLocal;
	}

	/**
	 * Specify the Spring {@link org.springframework.transaction.PlatformTransactionManager}
	 * to use for transactional wrapping of message reception plus listener execution.
	 * <p>Default is none, not performing any transactional wrapping.
	 * If specified, this will usually be a Spring
	 * {@link org.springframework.transaction.jta.JtaTransactionManager} or one
	 * of its subclasses, in combination with a JTA-aware ConnectionFactory that
	 * this message listener container obtains its Connections from.
	 */
	public void setTransactionManager(PlatformTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	/**
	 * Return the Spring PlatformTransactionManager to use for transactional
	 * wrapping of message reception plus listener execution.
	 */
	protected final PlatformTransactionManager getTransactionManager() {
		return this.transactionManager;
	}

	/**
	 * Specify the transaction name to use for transactional wrapping.
	 * Default is the bean name of this listener container, if any.
	 * @see org.springframework.transaction.TransactionDefinition#getName()
	 */
	public void setTransactionName(String transactionName) {
		this.transactionDefinition.setName(transactionName);
	}

	/**
	 * Specify the transaction timeout to use for transactional wrapping, in <b>seconds</b>.
	 * Default is none, using the transaction manager's default timeout.
	 * @see org.springframework.transaction.TransactionDefinition#getTimeout()
	 * @see #setReceiveTimeout
	 */
	public void setTransactionTimeout(int transactionTimeout) {
		this.transactionDefinition.setTimeout(transactionTimeout);
	}

	/**
	 * Set the timeout to use for receive calls, in <b>milliseconds</b>.
	 * The default is 1000 ms, that is, 1 second.
	 * <p><b>NOTE:</b> This value needs to be smaller than the transaction
	 * timeout used by the transaction manager (in the appropriate unit,
	 * of course). -1 indicates no timeout at all; however, this is only
	 * feasible if not running within a transaction manager.
	 * @see javax.jms.MessageConsumer#receive(long)
	 * @see javax.jms.MessageConsumer#receive
	 * @see #setTransactionTimeout
	 */
	public void setReceiveTimeout(long receiveTimeout) {
		this.receiveTimeout = receiveTimeout;
	}


	public void initialize() {
		// Use bean name as default transaction name.
		if (this.transactionDefinition.getName() == null) {
			this.transactionDefinition.setName(getBeanName());
		}

		// Proceed with superclass initialization.
		super.initialize();
	}


	/**
	 * Create a MessageConsumer for the given JMS Session,
	 * registering a MessageListener for the specified listener.
	 * @param session the JMS Session to work on
	 * @return the MessageConsumer
	 * @throws javax.jms.JMSException if thrown by JMS methods
	 * @see #receiveAndExecute
	 */
	protected MessageConsumer createListenerConsumer(Session session) throws JMSException {
		Destination destination = getDestination();
		if (destination == null) {
			destination = resolveDestinationName(session, getDestinationName());
		}
		return createConsumer(session, destination);
	}

	/**
	 * Execute the listener for a message received from the given consumer,
	 * wrapping the entire operation in an external transaction if demanded.
	 * @param session the JMS Session to work on
	 * @param consumer the MessageConsumer to work on
	 * @throws JMSException if thrown by JMS methods
	 * @see #doReceiveAndExecute
	 */
	protected boolean receiveAndExecute(Session session, MessageConsumer consumer) throws JMSException {
		if (this.transactionManager != null) {
			// Execute receive within transaction.
			TransactionStatus status = this.transactionManager.getTransaction(this.transactionDefinition);
			boolean messageReceived = true;
			try {
				messageReceived = doReceiveAndExecute(session, consumer, status);
			}
			catch (JMSException ex) {
				rollbackOnException(status, ex);
				throw ex;
			}
			catch (RuntimeException ex) {
				rollbackOnException(status, ex);
				throw ex;
			}
			catch (Error err) {
				rollbackOnException(status, err);
				throw err;
			}
			this.transactionManager.commit(status);
			return messageReceived;
		}

		else {
			// Execute receive outside of transaction.
			return doReceiveAndExecute(session, consumer, null);
		}
	}

	/**
	 * Actually execute the listener for a message received from the given consumer,
	 * fetching all requires resources and invoking the listener.
	 * @param session the JMS Session to work on
	 * @param consumer the MessageConsumer to work on
	 * @param status the TransactionStatus (may be <code>null</code>)
	 * @throws JMSException if thrown by JMS methods
	 * @see #doExecuteListener(javax.jms.Session, javax.jms.Message)
	 */
	protected boolean doReceiveAndExecute(Session session, MessageConsumer consumer, TransactionStatus status)
			throws JMSException {

		Connection conToClose = null;
		Session sessionToClose = null;
		MessageConsumer consumerToClose = null;
		try {
			Session sessionToUse = session;
			boolean transactional = false;
			if (sessionToUse == null) {
				sessionToUse = ConnectionFactoryUtils.doGetTransactionalSession(
						getConnectionFactory(), this.transactionalResourceFactory);
				transactional = (sessionToUse != null);
			}
			if (sessionToUse == null) {
				Connection conToUse = null;
				if (sharedConnectionEnabled()) {
					conToUse = getSharedConnection();
				}
				else {
					conToUse = createConnection();
					conToClose = conToUse;
					conToUse.start();
				}
				sessionToUse = createSession(conToUse);
				sessionToClose = sessionToUse;
			}
			MessageConsumer consumerToUse = consumer;
			if (consumerToUse == null) {
				consumerToUse = createListenerConsumer(sessionToUse);
				consumerToClose = consumerToUse;
			}
			Message message = receiveMessage(consumerToUse);
			if (message != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Received message of type [" + message.getClass() + "] from consumer [" +
							consumerToUse + "] of " + (transactional ? "transactional " : "") + "session [" +
							sessionToUse + "]");
				}
				messageReceived(message, session);
				try {
					doExecuteListener(sessionToUse, message);
				}
				catch (Throwable ex) {
					if (status != null) {
						if (logger.isDebugEnabled()) {
							logger.debug("Rolling back transaction because of listener exception thrown: " + ex);
						}
						status.setRollbackOnly();
					}
					handleListenerException(ex);
				}
				return true;
			}
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("Consumer [" + consumerToUse + "] of " + (transactional ? "transactional " : "") +
							"session [" + sessionToUse + "] did not receive a message");
				}
				return false;
			}
		}
		finally {
			JmsUtils.closeMessageConsumer(consumerToClose);
			JmsUtils.closeSession(sessionToClose);
			ConnectionFactoryUtils.releaseConnection(conToClose, getConnectionFactory(), true);
		}
	}

	/**
	 * Perform a rollback, handling rollback exceptions properly.
	 * @param status object representing the transaction
	 * @param ex the thrown application exception or error
	 */
	private void rollbackOnException(TransactionStatus status, Throwable ex) {
		logger.debug("Initiating transaction rollback on application exception", ex);
		try {
			this.transactionManager.rollback(status);
		}
		catch (RuntimeException ex2) {
			logger.error("Application exception overridden by rollback exception", ex);
			throw ex2;
		}
		catch (Error err) {
			logger.error("Application exception overridden by rollback error", ex);
			throw err;
		}
	}

	/**
	 * Receive a message from the given consumer.
	 * @param consumer the MessageConsumer to use
	 * @return the Message, or <code>null</code> if none
	 * @throws JMSException if thrown by JMS methods
	 */
	protected Message receiveMessage(MessageConsumer consumer) throws JMSException {
		return (this.receiveTimeout < 0 ? consumer.receive() : consumer.receive(this.receiveTimeout));
	}

	/**
	 * Template method that gets called right when a new message has been received,
	 * before attempting to process it. Allows subclasses to react to the event
	 * of an actual incoming message, for example adapting their consumer count.
	 * @param message the received message
	 * @param session the receiving JMS Session
	 */
	protected void messageReceived(Message message, Session session) {
	}


	//-------------------------------------------------------------------------
	// JMS 1.1 factory methods, potentially overridden for JMS 1.0.2
	//-------------------------------------------------------------------------

	/**
	 * Fetch an appropriate Connection from the given JmsResourceHolder.
	 * <p>This implementation accepts any JMS 1.1 Connection.
	 * @param holder the JmsResourceHolder
	 * @return an appropriate Connection fetched from the holder,
	 * or <code>null</code> if none found
	 */
	protected Connection getConnection(JmsResourceHolder holder) {
		return holder.getConnection();
	}

	/**
	 * Fetch an appropriate Session from the given JmsResourceHolder.
	 * <p>This implementation accepts any JMS 1.1 Session.
	 * @param holder the JmsResourceHolder
	 * @return an appropriate Session fetched from the holder,
	 * or <code>null</code> if none found
	 */
	protected Session getSession(JmsResourceHolder holder) {
		return holder.getSession();
	}

	/**
	 * Create a JMS MessageConsumer for the given Session and Destination.
	 * <p>This implementation uses JMS 1.1 API.
	 * @param session the JMS Session to create a MessageConsumer for
	 * @param destination the JMS Destination to create a MessageConsumer for
	 * @return the new JMS MessageConsumer
	 * @throws javax.jms.JMSException if thrown by JMS API methods
	 */
	protected MessageConsumer createConsumer(Session session, Destination destination) throws JMSException {
		// Only pass in the NoLocal flag in case of a Topic:
		// Some JMS providers, such as WebSphere MQ 6.0, throw IllegalStateException
		// in case of the NoLocal flag being specified for a Queue.
		if (isPubSubDomain()) {
			if (isSubscriptionDurable() && destination instanceof Topic) {
				return session.createDurableSubscriber(
						(Topic) destination, getDurableSubscriptionName(), getMessageSelector(), isPubSubNoLocal());
			}
			else {
				return session.createConsumer(destination, getMessageSelector(), isPubSubNoLocal());
			}
		}
		else {
			return session.createConsumer(destination, getMessageSelector());
		}
	}


	/**
	 * ResourceFactory implementation that delegates to this listener container's protected callback methods.
	 */
	private class MessageListenerContainerResourceFactory implements ConnectionFactoryUtils.ResourceFactory {

		public Connection getConnection(JmsResourceHolder holder) {
			return AbstractPollingMessageListenerContainer.this.getConnection(holder);
		}

		public Session getSession(JmsResourceHolder holder) {
			return AbstractPollingMessageListenerContainer.this.getSession(holder);
		}

		public Connection createConnection() throws JMSException {
			if (AbstractPollingMessageListenerContainer.this.sharedConnectionEnabled()) {
				return AbstractPollingMessageListenerContainer.this.getSharedConnection();
			}
			else {
				return AbstractPollingMessageListenerContainer.this.createConnection();
			}
		}

		public Session createSession(Connection con) throws JMSException {
			return AbstractPollingMessageListenerContainer.this.createSession(con);
		}

		public boolean isSynchedLocalTransactionAllowed() {
			return AbstractPollingMessageListenerContainer.this.isSessionTransacted();
		}
	}

}