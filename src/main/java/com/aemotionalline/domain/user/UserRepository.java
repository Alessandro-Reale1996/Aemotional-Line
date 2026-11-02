package com.aemotionalline.domain.user;

import java.util.Optional;

/** Where accounts are kept. Plain Java: the persistence layer implements it. */
public interface UserRepository
{
	Optional<User> findById(UserId id);

	/** Like {@link #findById}, but locks the account until the end of the transaction (logins, password changes, disabling). */
	Optional<User> findByIdForUpdate(UserId id);

	Optional<User> findByEmail(EmailAddress email);

	void save(User user);

	/** Removes the account (and whatever only exists for it, such as its one-time links). */
	void delete(UserId id);
}
