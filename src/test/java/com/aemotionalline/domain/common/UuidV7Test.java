package com.aemotionalline.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

public class UuidV7Test
{
	@Test
	void shouldBeAVersion7UuidWithTheStandardVariant()
	{
		UUID uuid = UuidV7.generate();

		assertEquals(7, uuid.version());
		assertEquals(2, uuid.variant());
	}

	@Test
	void shouldStartWithTheUnixTimeInMilliseconds()
	{
		long millis = 1_793_000_000_000L;

		UUID uuid = UuidV7.generate(millis);

		assertEquals(millis, uuid.getMostSignificantBits() >>> 16);
	}

	@Test
	void shouldSortLaterIdsAfterEarlierOnes()
	{
		UUID earlier = UuidV7.generate(1_793_000_000_000L);
		UUID later = UuidV7.generate(1_793_000_000_001L);

		assertTrue(earlier.compareTo(later) < 0);
	}

	@Test
	void shouldNotRepeatItself()
	{
		Set<UUID> ids = new HashSet<>();

		// Many ids in the same millisecond differ in their 74 random bits.
		for (int i = 0; i < 100_000; i++)
		{
			ids.add(UuidV7.generate(1_793_000_000_000L));
		}

		assertEquals(100_000, ids.size());
	}

	@Test
	void shouldBeUsedByEveryIdOfTheDomain()
	{
		List<UUID> ids = Stream.of(
				UserId.random().value(), CoupleId.random().value(), ConversationId.random().value(),
				NegotiationId.random().value(), ProposalId.random().value(), MessageId.random().value(),
				ParagraphId.random().value(), ConstraintChangeRequestId.random().value()).toList();

		ids.forEach(id -> assertEquals(7, id.version()));
	}
}
