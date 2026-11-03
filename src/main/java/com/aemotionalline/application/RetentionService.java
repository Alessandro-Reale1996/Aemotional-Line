package com.aemotionalline.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.CoupleDeletion.Cause;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.negotiation.NegotiationRepository;

/**
 * The retention period after a couple's end (CLAUDE.md, S3 part 2): once it is over, the couple's data are deleted with
 * the same operation a therapist uses, recorded as a deletion by retention. Called by the scheduled job, never on behalf
 * of a user; the period itself is configuration, disabled until the rule is decided with the privacy advisor.
 */
@Service
public class RetentionService
{
	private final CoupleRepository couples;
	private final ConversationRepository conversations;
	private final NegotiationRepository negotiations;
	private final CoupleErasure erasure;
	private final Clock clock;

	RetentionService(CoupleRepository couples, ConversationRepository conversations, NegotiationRepository negotiations, CoupleErasure erasure,
			Clock clock)
	{
		this.couples = couples;
		this.conversations = conversations;
		this.negotiations = negotiations;
		this.erasure = erasure;
		this.clock = clock;
	}

	@ReadOnlyTransaction
	public List<CoupleId> couplesEndedBefore(Instant cutoff)
	{
		return couples.findIdsEndedBefore(cutoff);
	}

	/** Deletes the couple's data if it is (still) a couple that ended before the cutoff; one transaction per couple. */
	@Transactional
	public boolean deleteIfEndedBefore(CoupleId id, Instant cutoff)
	{
		Optional<Couple> found = couples.findByIdForUpdate(id).filter(couple -> couple.getEndedAt().filter(ended -> ended.isBefore(cutoff)).isPresent());

		if (found.isEmpty())
		{
			return false;
		}

		Couple couple = found.get();
		CoupleLocks.lockContentOf(conversations, negotiations, id);
		erasure.erase(new CoupleDeletion(id, couple.getTherapistId(), couple.getEndedAt().orElseThrow(), clock.instant(), Cause.RETENTION,
				Optional.empty()));

		return true;
	}
}
