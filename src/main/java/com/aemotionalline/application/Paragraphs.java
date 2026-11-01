package com.aemotionalline.application;

import java.util.Optional;

import com.aemotionalline.application.view.Views;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.Discussion;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.user.UserId;

/**
 * Finds a sent paragraph of a conversation as one user may see it. A paragraph in the other partner's message that the user
 * hasn't opened is treated as if it didn't exist: its id can't be used to answer it, cite it or expand it.
 */
final class Paragraphs
{
	record Located(Discussion discussion, Message message, Paragraph paragraph)
	{
	}

	private Paragraphs()
	{
	}

	static Optional<Located> locate(Conversation conversation, ParagraphId id)
	{
		for (Discussion discussion : conversation.getDiscussions())
		{
			for (Message message : discussion.getMessages())
			{
				for (Paragraph paragraph : message.getParagraphs())
				{
					if (paragraph.getId().equals(id))
					{
						return Optional.of(new Located(discussion, message, paragraph));
					}
				}
			}
		}

		return Optional.empty();
	}

	static Paragraph visibleTo(UserId user, Conversation conversation, ParagraphId id)
	{
		Couple couple = conversation.getCouple();

		return locate(conversation, id)
				.filter(located -> Views.canSeeContent(user, couple, located.discussion(), located.message()))
				.map(Located::paragraph)
				.orElseThrow(() -> new InvalidRequestException("Paragraph " + id.value() + " not found in the conversation."));
	}
}
