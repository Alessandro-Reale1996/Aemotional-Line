package com.aemotionalline.application;

import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.negotiation.NegotiationRepository;

/**
 * Ending a couple or deleting its data first waits for the operations in progress on its conversation and negotiations:
 * after the couple's own lock it takes theirs, without loading them (lock order: user, couple, conversation, negotiation).
 * An operation that comes later waits for the end and then finds the couple ended (or gone).
 */
final class CoupleLocks
{
	private CoupleLocks()
	{
	}

	static void lockContentOf(ConversationRepository conversations, NegotiationRepository negotiations, CoupleId couple)
	{
		conversations.lockByCouple(couple);
		negotiations.lockAllOf(couple);
	}
}
