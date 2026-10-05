package com.aemotionalline.domain;

import java.util.UUID;

import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

/**
 * Readable, deterministic ids for tests: the same number always gives the same id, so tests can name
 * "paragraph 10" or "user 20" instead of carrying random UUIDs around. Production code never uses this.
 */
public final class Ids
{
	private Ids()
	{
	}

	private static UUID of(long number)
	{
		return new UUID(0L, number);
	}

	public static long numberOf(UUID id)
	{
		return id.getLeastSignificantBits();
	}

	public static UserId user(long number)
	{
		return new UserId(of(number));
	}

	public static CoupleId couple(long number)
	{
		return new CoupleId(of(number));
	}

	public static ConversationId conversation(long number)
	{
		return new ConversationId(of(number));
	}

	public static NegotiationId negotiation(long number)
	{
		return new NegotiationId(of(number));
	}

	public static ProposalId proposal(long number)
	{
		return new ProposalId(of(number));
	}

	public static MessageId message(long number)
	{
		return new MessageId(of(number));
	}

	public static ParagraphId paragraph(long number)
	{
		return new ParagraphId(of(number));
	}
}
