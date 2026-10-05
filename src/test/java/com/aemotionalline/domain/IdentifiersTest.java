package com.aemotionalline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

/** Every id is a UUID wrapped in a type of its own: it can't be missing, and the domain can create it itself. */
public class IdentifiersTest
{
	@Test
	void shouldRejectAMissingUuid()
	{
		assertThrows(NullPointerException.class, () -> new UserId(null));
		assertThrows(NullPointerException.class, () -> new CoupleId(null));
		assertThrows(NullPointerException.class, () -> new ConversationId(null));
		assertThrows(NullPointerException.class, () -> new NegotiationId(null));
		assertThrows(NullPointerException.class, () -> new ProposalId(null));
		assertThrows(NullPointerException.class, () -> new MessageId(null));
		assertThrows(NullPointerException.class, () -> new ParagraphId(null));
	}

	@Test
	void shouldCreateANewDifferentIdEachTime()
	{
		assertNotEquals(UserId.random(), UserId.random());
		assertNotEquals(CoupleId.random(), CoupleId.random());
		assertNotEquals(ConversationId.random(), ConversationId.random());
		assertNotEquals(NegotiationId.random(), NegotiationId.random());
		assertNotEquals(ProposalId.random(), ProposalId.random());
		assertNotEquals(MessageId.random(), MessageId.random());
		assertNotEquals(ParagraphId.random(), ParagraphId.random());
	}

	@Test
	void shouldNotConfuseIdsOfDifferentTypesWithTheSameUuid()
	{
		// The type is part of the identity: a user and a couple built from the same UUID are not interchangeable.
		assertNotEquals((Object) Ids.user(1L), (Object) Ids.couple(1L));
	}

	@Test
	void shouldIdentifyADiscussionByConversationAndNumber()
	{
		assertEquals(new DiscussionId(Ids.conversation(1L), 2), new DiscussionId(Ids.conversation(1L), 2));
		assertNotEquals(new DiscussionId(Ids.conversation(1L), 2), new DiscussionId(Ids.conversation(1L), 3));
		assertNotEquals(new DiscussionId(Ids.conversation(1L), 2), new DiscussionId(Ids.conversation(2L), 2));
	}

	@Test
	void shouldRejectAnInvalidDiscussionId()
	{
		assertThrows(NullPointerException.class, () -> new DiscussionId(null, 0));
		assertThrows(IllegalArgumentException.class, () -> new DiscussionId(Ids.conversation(1L), -1));
	}
}
