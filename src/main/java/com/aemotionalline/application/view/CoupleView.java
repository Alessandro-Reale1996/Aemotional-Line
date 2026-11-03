package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.Optional;

import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleId;

/** One of the user's couples: who is in it, whether it is still active, and its conversation once started. */
public record CoupleView(CoupleId id, PersonView partnerOne, PersonView partnerTwo, PersonView therapist, boolean active, Optional<Instant> endedAt,
		Optional<ConversationId> conversation)
{
}
