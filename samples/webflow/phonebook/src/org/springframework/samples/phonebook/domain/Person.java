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
package org.springframework.samples.phonebook.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Person implements Serializable {

	private String firstName;

	private String lastName;

	private UserId userId;

	private String phone;

	private List colleagues = new ArrayList();

	public Person() {
		this("", "", "", "");
	}

	public Person(String firstName, String lastName, String userId, String phone) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.userId = new UserId(userId);
		this.phone = phone;
	}

	public String getFirstName() {
		return this.firstName;
	}

	public String getLastName() {
		return this.lastName;
	}

	public UserId getUserId() {
		return this.userId;
	}

	public String getPhone() {
		return this.phone;
	}

	public List getColleagues() {
		return this.colleagues;
	}

	public int nrColleagues() {
		return this.colleagues.size();
	}

	public Person getColleague(int i) {
		return (Person)this.colleagues.get(i);
	}

	public void addColleague(Person colleague) {
		this.colleagues.add(colleague);
	}
}