/*
 * Copyright 2002-2004 the original author or authors.
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

package org.springframework.orm.hibernate.support;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.orm.hibernate.LocalSessionFactoryBean;

/**
 * Hibernate UserType implementation for byte arrays that get mapped to BLOBs.
 * Retrieves the LobHandler to use from LocalSessionFactoryBean at config time.
 *
 * <p>Can also be defined in generic Hibernate mappings, as DefaultLobCreator will
 * work with most JDBC-compliant databases respectively drivers. In this case,
 * the field type does not have to be BLOB: For databases like MySQL and MS SQL
 * Server, any large enough binary type will work.
 *
 * @author Juergen Hoeller
 * @since 1.1
 * @see org.springframework.jdbc.support.lob.LobHandler
 * @see org.springframework.orm.hibernate.LocalSessionFactoryBean#setLobHandler
 */
public class BlobByteArrayType extends AbstractLobType  {

	/**
	 * Constructor used by Hibernate: fetches config-time LobHandler
	 * from LocalSessionFactoryBean.
	 * @see LocalSessionFactoryBean#getConfigTimeLobHandler
	 */
	public BlobByteArrayType() {
		super();
	}

	/**
	 * Constructor used for testing: takes an explicit LobHandler.
	 */
	protected BlobByteArrayType(LobHandler lobHandler) {
		super(lobHandler);
	}

	public int[] sqlTypes() {
		return new int[] {Types.BLOB};
	}

	public Class returnedClass() {
		return byte[].class;
	}

	public boolean equals(Object x, Object y) {
		return (x == y) ||
				(x instanceof byte[] && y instanceof byte[] && Arrays.equals((byte[]) x, (byte[]) y));
	}

	public Object deepCopy(Object value) {
		byte[] original = (byte[]) value;
		byte[] copy = new byte[original.length];
		System.arraycopy(original, 0, copy, 0, original.length);
		return copy;
	}

	public boolean isMutable() {
		return true;
	}

	protected Object nullSafeGetInternal(ResultSet rs, int index, LobHandler lobHandler)
			throws SQLException {
		return lobHandler.getBlobAsBytes(rs, index);
	}

	protected void nullSafeSetInternal(PreparedStatement ps, int index, Object value, LobCreator lobCreator)
			throws SQLException {
		lobCreator.setBlobAsBytes(ps, index, (byte[]) value);
	}

}
