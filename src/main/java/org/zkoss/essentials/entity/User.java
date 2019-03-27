/*
	Description:
		ZK Essentials
	History:
		Created by dennis
Copyright (C) 2012 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.essentials.entity;

import java.io.Serializable;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
/**
 * User entity
 */
public class User implements Serializable,Cloneable {
	private static final long serialVersionUID = 1L;

	@Value("${account}")
	String account;

	@Value("${fullname}")
	String fullName;

	@Value("${password}")
	String password;

	@Value("${email}")
	String email;

	Date birthday;
	String country;
	String bio;

	public String getAccount() {
		return account;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(final String fullName) {
		this.fullName = fullName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(final String email) {
		this.email = email;
	}

	public Date getBirthday() {
		return birthday;
	}

	public void setBirthday(final Date birthday) {
		this.birthday = birthday;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(final String country) {
		this.country = country;
	}

	public String getBio() {
		return bio;
	}

	public void setBio(final String bio) {
		this.bio = bio;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(final String password) {
		this.password = password;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((account == null) ? 0 : account.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final User other = (User) obj;
		if (account == null) {
			if (other.account != null)
				return false;
		} else if (!account.equals(other.account))
			return false;
		return true;
	}

	public static User clone(final User user){
		try {
			return (User)user.clone();
		} catch (final CloneNotSupportedException e) {
			//not possible
		}
		return null;
	}
}