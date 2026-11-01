package com.aemotionalline.application;

import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.negotiation.NegotiationId;

/**
 * Where the application keeps the purpose of each negotiation in progress. Implemented by the persistence layer; a
 * purpose disappears with its negotiation when the negotiation is deleted.
 */
public interface NegotiationPurposeRepository
{
	void save(NegotiationPurpose purpose);

	Optional<NegotiationPurpose> findByNegotiation(NegotiationId negotiationId);

	/** The purposes of the couple's negotiations still in progress (neither refused nor abandoned). */
	List<NegotiationPurpose> findInProgressOf(CoupleId coupleId);
}
