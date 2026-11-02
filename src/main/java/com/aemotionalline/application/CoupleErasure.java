package com.aemotionalline.application;

import java.util.Optional;

import com.aemotionalline.domain.couple.CoupleId;

/**
 * Deletes every piece of data of a couple, irreversibly, keeping only the trace of the deletion. Implemented by the
 * persistence layer; the caller decides who may do it and already holds the couple's locks.
 */
public interface CoupleErasure
{
	/**
	 * Deletes the couple, its conversation with all its content, its negotiations (in progress or failed attempts to start
	 * the conversation) and the invitation that created it, and records the trace, in the caller's transaction.
	 */
	void erase(CoupleDeletion deletion);

	Optional<CoupleDeletion> findDeletion(CoupleId couple);
}
