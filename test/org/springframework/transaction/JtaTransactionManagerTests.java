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

package org.springframework.transaction;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import junit.framework.TestCase;
import org.easymock.MockControl;

import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Juergen Hoeller
 * @since 12.05.2003
 */
public class JtaTransactionManagerTests extends TestCase {
	
	public void testJtaTransactionManagerWithCommit() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCommit(false);
		synchControl.setVoidCallable(1);
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setName("txName");

		assertEquals(JtaTransactionManager.SYNCHRONIZATION_ALWAYS, ptm.getTransactionSynchronization());
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		assertNull(TransactionSynchronizationManager.getCurrentTransactionName());
		assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				// something transactional
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				assertEquals("txName", TransactionSynchronizationManager.getCurrentTransactionName());
				assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		assertNull(TransactionSynchronizationManager.getCurrentTransactionName());
		assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithCommitAndSynchronizationOnActual() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCommit(false);
		synchControl.setVoidCallable(1);
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		ptm.setTransactionSynchronization(JtaTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				// something transactional
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithCommitAndSynchronizationNever() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setVoidCallable(1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		ptm.setTransactionSynchronization(JtaTransactionManager.SYNCHRONIZATION_NEVER);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
	}

	public void testJtaTransactionManagerWithRollback() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.setTransactionTimeout(10);
		utControl.setVoidCallable(1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.rollback();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setTimeout(10);
		tt.setName("txName");

		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		assertNull(TransactionSynchronizationManager.getCurrentTransactionName());
		assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				assertEquals("txName", TransactionSynchronizationManager.getCurrentTransactionName());
				assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		assertNull(TransactionSynchronizationManager.getCurrentTransactionName());
		assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithRollbackAndSynchronizationOnActual() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.setTransactionTimeout(10);
		utControl.setVoidCallable(1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.rollback();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		ptm.setTransactionSynchronization(JtaTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
		tt.setTimeout(10);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithRollbackAndSynchronizationNever() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.setTransactionTimeout(10);
		utControl.setVoidCallable(1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.rollback();
		utControl.setVoidCallable(1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		ptm.setTransactionSynchronizationName("SYNCHRONIZATION_NEVER");
		tt.setTimeout(10);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
	}

	public void testJtaTransactionManagerWithExistingTransactionAndRollbackOnly() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithExistingTransactionAndException() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		try {
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
					TransactionSynchronizationManager.registerSynchronization(synch);
					throw new IllegalStateException("I want a rollback");
				}
			});
			fail("Should have thrown IllegalStateException");
		}
		catch (IllegalStateException ex) {
			// expected
		}
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithExistingTransactionAndRollbackOnlyAndNoGlobalRollback() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		ptm.setGlobalRollbackOnParticipationFailure(false);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithExistingTransactionAndExceptionAndNoGlobalRollback() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		ptm.setGlobalRollbackOnParticipationFailure(false);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		try {
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
					TransactionSynchronizationManager.registerSynchronization(synch);
					throw new IllegalStateException("I want a rollback");
				}
			});
			fail("Should have thrown IllegalStateException");
		}
		catch (IllegalStateException ex) {
			// expected
		}
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithExistingTransactionAndJtaSynchronization() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		MockControl tmControl = MockControl.createControl(TransactionManager.class);
		TransactionManager tm = (TransactionManager) tmControl.getMock();
		MockJtaTransaction tx = new MockJtaTransaction();

		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setVoidCallable(1);
		tm.getTransaction();
		tmControl.setReturnValue(tx, 1);

		utControl.replay();
		tmControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut, tm);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		assertNotNull(tx.getSynchronization());
		tx.getSynchronization().beforeCompletion();
		tx.getSynchronization().afterCompletion(Status.STATUS_ROLLEDBACK);

		utControl.verify();
		tmControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithExistingTransactionAndSynchronizationOnActual() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		ptm.setTransactionSynchronization(JtaTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithExistingTransactionAndSynchronizationNever() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setVoidCallable(1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		ptm.setTransactionSynchronization(JtaTransactionManager.SYNCHRONIZATION_NEVER);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
	}

	public void testJtaTransactionManagerWithExistingAndPropagationSupports() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setVoidCallable(1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithPropagationSupports() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		utControl.replay();

		MockControl synchControl = MockControl.createControl(TransactionSynchronization.class);
		final TransactionSynchronization synch = (TransactionSynchronization) synchControl.getMock();
		synch.beforeCompletion();
		synchControl.setVoidCallable(1);
		synch.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		synchControl.setVoidCallable(1);
		synchControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				TransactionSynchronizationManager.registerSynchronization(synch);
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		synchControl.verify();
	}

	public void testJtaTransactionManagerWithPropagationSupportsAndSynchronizationOnActual() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		ptm.setTransactionSynchronization(JtaTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
	}

	public void testJtaTransactionManagerWithPropagationSupportsAndSynchronizationNever() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		ptm.setTransactionSynchronization(JtaTransactionManager.SYNCHRONIZATION_NEVER);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
	}

	public void testJtaTransactionManagerWithPropagationNotSupported() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		MockControl tmControl = MockControl.createControl(TransactionManager.class);
		TransactionManager tm = (TransactionManager) tmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		tm.suspend();
		tmControl.setReturnValue(tx, 1);
		tm.resume(tx);
		tmControl.setVoidCallable(1);
		utControl.replay();
		tmControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut, tm);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				status.setRollbackOnly();
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		tmControl.verify();
	}

	public void testJtaTransactionManagerWithPropagationRequiresNew() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		MockControl tmControl = MockControl.createControl(TransactionManager.class);
		TransactionManager tm = (TransactionManager) tmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 3);
		tm.suspend();
		tmControl.setReturnValue(tx, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setVoidCallable(1);
		tm.resume(tx);
		tmControl.setVoidCallable(1);
		ut.commit();
		utControl.setVoidCallable(1);
		utControl.replay();
		tmControl.replay();

		final JtaTransactionManager ptm = new JtaTransactionManager(ut, tm);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		tt.setName("txName");

		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				assertEquals("txName", TransactionSynchronizationManager.getCurrentTransactionName());
				assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());

				TransactionTemplate tt2 = new TransactionTemplate(ptm);
				tt2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
				tt2.setReadOnly(true);
				tt2.setName("txName2");
				tt2.execute(new TransactionCallbackWithoutResult() {
					protected void doInTransactionWithoutResult(TransactionStatus status) {
						assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
						assertEquals("txName2", TransactionSynchronizationManager.getCurrentTransactionName());
						assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
					}
				});

				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
				assertEquals("txName", TransactionSynchronizationManager.getCurrentTransactionName());
				assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		tmControl.verify();
	}

	public void testJtaTransactionManagerWithPropagationRequiresNewAndExisting() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		MockControl tmControl = MockControl.createControl(TransactionManager.class);
		TransactionManager tm = (TransactionManager) tmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 2);
		tm.suspend();
		tmControl.setReturnValue(tx, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setVoidCallable(1);
		tm.resume(tx);
		tmControl.setVoidCallable(1);
		utControl.replay();
		tmControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut, tm);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
		tmControl.verify();
	}

	public void testJtaTransactionManagerWithPropagationRequiresNewAndAdapter() throws Exception {
		MockControl tmControl = MockControl.createControl(TransactionManager.class);
		TransactionManager tm = (TransactionManager) tmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		tm.getStatus();
		tmControl.setReturnValue(Status.STATUS_ACTIVE, 2);
		tm.suspend();
		tmControl.setReturnValue(tx, 1);
		tm.begin();
		tmControl.setVoidCallable(1);
		tm.commit();
		tmControl.setVoidCallable(1);
		tm.resume(tx);
		tmControl.setVoidCallable(1);
		tmControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(tm);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
			}
		});
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		tmControl.verify();
	}

	public void testJtaTransactionManagerWithPropagationRequiresNewAndSuspensionNotSupported() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		try {
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
				}
			});
			fail("Should have thrown TransactionSuspensionNotSupportedException");
		}
		catch (TransactionSuspensionNotSupportedException ex) {
			// expected
		}
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());

		utControl.verify();
	}

	public void testJtaTransactionManagerWithIsolationLevel() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
				}
			});
			fail("Should have thrown InvalidIsolationLevelException");
		}
		catch (InvalidIsolationLevelException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithSystemExceptionOnIsExisting() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setThrowable(new SystemException("system exception"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
				}
			});
			fail("Should have thrown TransactionSystemException");
		}
		catch (TransactionSystemException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithNestedBegin() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 2);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setVoidCallable(1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
		tt.execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				// something transactional
			}
		});

		utControl.verify();
	}

	public void testJtaTransactionManagerWithNotSupportedExceptionOnNestedBegin() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setThrowable(new NotSupportedException("not supported"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
				}
			});
			fail("Should have thrown NestedTransactionNotSupportedException");
		}
		catch (NestedTransactionNotSupportedException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithUnsupportedOperationExceptionOnNestedBegin() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setThrowable(new UnsupportedOperationException("not supported"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
				}
			});
			fail("Should have thrown NestedTransactionNotSupportedException");
		}
		catch (NestedTransactionNotSupportedException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithSystemExceptionOnBegin() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.begin();
		utControl.setThrowable(new SystemException("system exception"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
				}
			});
			fail("Should have thrown CannotCreateTransactionException");
		}
		catch (CannotCreateTransactionException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithRollbackExceptionOnCommit() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setThrowable(new RollbackException("unexpected rollback"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
						public void afterCompletion(int status) {
							assertTrue("Correct completion status", status == TransactionSynchronization.STATUS_ROLLED_BACK);
						}
					});
				}
			});
			fail("Should have thrown UnexpectedRollbackException");
		}
		catch (UnexpectedRollbackException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithHeuristicMixedExceptionOnCommit() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setThrowable(new HeuristicMixedException("heuristic exception"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
						public void afterCompletion(int status) {
							assertTrue("Correct completion status", status == TransactionSynchronization.STATUS_UNKNOWN);
						}
					});
				}
			});
			fail("Should have thrown HeuristicCompletionException");
		}
		catch (HeuristicCompletionException ex) {
			// expected
			assertTrue(ex.getOutcomeState() == HeuristicCompletionException.STATE_MIXED);
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithHeuristicRollbackExceptionOnCommit() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setThrowable(new HeuristicRollbackException("heuristic exception"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
						public void afterCompletion(int status) {
							assertTrue("Correct completion status", status == TransactionSynchronization.STATUS_UNKNOWN);
						}
					});
				}
			});
			fail("Should have thrown HeuristicCompletionException");
		}
		catch (HeuristicCompletionException ex) {
			// expected
			assertTrue(ex.getOutcomeState() == HeuristicCompletionException.STATE_ROLLED_BACK);
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithSystemExceptionOnCommit() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setThrowable(new SystemException("system exception"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// something transactional
					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
						public void afterCompletion(int status) {
							assertTrue("Correct completion status", status == TransactionSynchronization.STATUS_UNKNOWN);
						}
					});
				}
			});
			fail("Should have thrown TransactionSystemException");
		}
		catch (TransactionSystemException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithSystemExceptionOnRollback() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.rollback();
		utControl.setThrowable(new SystemException("system exception"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
						public void afterCompletion(int status) {
							assertTrue("Correct completion status", status == TransactionSynchronization.STATUS_UNKNOWN);
						}
					});
					status.setRollbackOnly();
				}
			});
			fail("Should have thrown TransactionSystemException");
		}
		catch (TransactionSystemException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithIllegalStateExceptionOnRollbackOnly() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setThrowable(new IllegalStateException("no existing transaction"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					status.setRollbackOnly();
				}
			});
			fail("Should have thrown NoTransactionException");
		}
		catch (NoTransactionException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithSystemExceptionOnRollbackOnly() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.setRollbackOnly();
		utControl.setThrowable(new SystemException("system exception"));
		utControl.replay();

		try {
			JtaTransactionManager ptm = new JtaTransactionManager(ut);
			TransactionTemplate tt = new TransactionTemplate(ptm);
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					status.setRollbackOnly();
					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
						public void afterCompletion(int status) {
							assertTrue("Correct completion status", status == TransactionSynchronization.STATUS_UNKNOWN);
						}
					});
				}
			});
			fail("Should have thrown TransactionSystemException");
		}
		catch (TransactionSystemException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithDoubleCommit() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_ACTIVE, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.commit();
		utControl.setVoidCallable(1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		TransactionStatus status = ptm.getTransaction(new DefaultTransactionDefinition());
		assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
		// first commit
		ptm.commit(status);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		try {
			// second commit attempt
			ptm.commit(status);
			fail("Should have thrown IllegalTransactionStateException");
		}
		catch (IllegalTransactionStateException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithDoubleRollback() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.rollback();
		utControl.setVoidCallable(1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		TransactionStatus status = ptm.getTransaction(new DefaultTransactionDefinition());
		assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
		// first rollback
		ptm.rollback(status);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		try {
			// second rollback attempt
			ptm.rollback(status);
			fail("Should have thrown IllegalTransactionStateException");
		}
		catch (IllegalTransactionStateException ex) {
			// expected
		}

		utControl.verify();
	}

	public void testJtaTransactionManagerWithRollbackAndCommit() throws Exception {
		MockControl utControl = MockControl.createControl(UserTransaction.class);
		UserTransaction ut = (UserTransaction) utControl.getMock();
		ut.getStatus();
		utControl.setReturnValue(Status.STATUS_NO_TRANSACTION, 1);
		ut.begin();
		utControl.setVoidCallable(1);
		ut.rollback();
		utControl.setVoidCallable(1);
		utControl.replay();

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		TransactionStatus status = ptm.getTransaction(new DefaultTransactionDefinition());
		assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
		// first: rollback
		ptm.rollback(status);
		assertTrue(!TransactionSynchronizationManager.isSynchronizationActive());
		try {
			// second: commit attempt
			ptm.commit(status);
			fail("Should have thrown IllegalTransactionStateException");
		}
		catch (IllegalTransactionStateException ex) {
			// expected
		}

		utControl.verify();
	}

	/**
	 * Prevent any side-effects due to this test modifying ThreadLocals that might
	 * affect subsequent tests when all tests are run in the same JVM, as with Eclipse.
	 */
	protected void tearDown() {
		assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
		assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
		assertNull(TransactionSynchronizationManager.getCurrentTransactionName());
		assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
	}

}
