package com.aemotionalline.application;

import com.aemotionalline.domain.user.PasswordHash;

/** Turns a password into its stored form and checks one against it; implemented by the security infrastructure. */
public interface PasswordHasher
{
	PasswordHash hash(String password);

	boolean matches(String password, PasswordHash hash);
}
