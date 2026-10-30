package com.aemotionalline.domain.negotiation;

import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.couple.CoupleId;

/**
 * Where negotiations are kept while they are in progress (a stipula lasts days). Once a negotiation's history has been
 * copied into a conversation (it took effect, or it failed and was recorded there) it is deleted. Plain Java: the
 * persistence layer implements it.
 */
public interface NegotiationRepository
{
	Optional<Negotiation> findById(NegotiationId id);

	/**
	 * Like {@link #findById}, but locks the negotiation until the end of the transaction, so that two operations on it
	 * can't overlap: a concurrent one waits and then sees the updated negotiation. Used by every operation that changes
	 * it. When a conversation is locked too, the conversation is locked first.
	 */
	Optional<Negotiation> findByIdForUpdate(NegotiationId id);

	/**
	 * The couple's failed negotiations still kept at couple level: the attempts to start its conversation, which have no
	 * conversation to be recorded in until it starts (then they are recorded there and deleted).
	 */
	List<Negotiation> findFailedStartsOf(CoupleId coupleId);

	void save(Negotiation negotiation);

	void delete(NegotiationId id);
}
