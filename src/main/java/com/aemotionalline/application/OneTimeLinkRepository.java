package com.aemotionalline.application;

import java.time.Instant;
import java.util.Optional;

import com.aemotionalline.domain.user.UserId;

/** Where one-time links are kept (by the hash of their code). Implemented by the persistence layer. */
public interface OneTimeLinkRepository
{
	void save(OneTimeLink link);

	Optional<OneTimeLink> findByCodeHash(String codeHash);

	/** Makes every unused link of the user for that purpose unusable (a new link replaces the old ones). */
	void invalidateUnused(UserId user, OneTimeLink.Purpose purpose, Instant at);
}
