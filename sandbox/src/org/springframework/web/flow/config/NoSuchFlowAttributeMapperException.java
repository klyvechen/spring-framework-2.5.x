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
package org.springframework.web.flow.config;

import org.springframework.web.flow.ServiceLookupException;

/**
 * Thrown when a flow model mapper service cannot be found.
 * 
 * @author Keith Donald
 * @author Erwin Vervaet
 */
public class NoSuchFlowAttributeMapperException extends ServiceLookupException {

	/**
	 * Create a new flow model mapper lookup exception.
	 * @param serviceImplementationClass the required implementation class of
	 *        the service that cannot be found
	 */
	public NoSuchFlowAttributeMapperException(Class serviceImplementationClass) {
		super(serviceImplementationClass);
	}

	/**
	 * Create a new flow model mapper lookup exception.
	 * @param serviceImplementationClass the required implementation class of
	 *        the service that cannot be found
	 * @param cause the underlying cause of this exception
	 */
	public NoSuchFlowAttributeMapperException(Class serviceImplementationClass, Throwable cause) {
		super(serviceImplementationClass, cause);
	}

	/**
	 * Create a new flow model mapper lookup exception.
	 * @param serviceId the id of the service that cannot be found
	 */
	public NoSuchFlowAttributeMapperException(String serviceId) {
		super(serviceId);
	}

	/**
	 * Create a new flow model mapper lookup exception.
	 * @param serviceId the id of the service that cannot be found
	 * @param cause the underlying cause of this exception
	 */
	public NoSuchFlowAttributeMapperException(String serviceId, Throwable cause) {
		super(serviceId, cause);
	}

	public String getMessage() {
		if (isServiceIdLookupFailure()) {
			return "No flow model mapper was found with id '"
					+ getServiceId()
					+ "' -- make sure there is a single FlowModelMapper implementation exported in the context with this id";
		}
		else {
			return "No flow model mapper was found of implementation '"
					+ getServiceImplementationClass()
					+ "'; make sure there is a single FlowModelMapper implementation of this type exported in the context";
		}
	}
}