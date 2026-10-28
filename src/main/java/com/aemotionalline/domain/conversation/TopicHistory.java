package com.aemotionalline.domain.conversation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;

/**
 * The archive's "history by topic" of a paragraph (spec 1.3.4): every paragraph linked to it, so a reviewer can see how
 * a topic was dealt with, solved or stalled, not only the chronological order of the messages. It is computed on
 * request from the graph and the discussions, never stored. Like {@link Analyzer}, it reads the conversation from
 * outside and changes nothing.
 * <p>
 * "Linked" means the whole connected component of the paragraph (ancestors, descendants, siblings, merged topics).
 * The rule depends on where the chosen paragraph is:
 * <ul>
 * <li>in a specific discussion (D.S.): the component, paragraph by paragraph; it reaches the main conversation through
 * the discussion's origin;</li>
 * <li>in the main conversation (C.P.): the component's paragraphs in the main conversation, and, for each specific
 * discussion opened from one of them, every paragraph of all its messages (the messages "in their entirety"), in a chain
 * for the discussions opened from those paragraphs in turn.</li>
 * </ul>
 * The entries are in sending order, each paragraph once.
 */
public record TopicHistory(Paragraph paragraph, Rule rule, List<TopicHistoryEntry> entries)
{
	public enum Rule
	{
		MAIN_CONVERSATION,
		SPECIFIC_DISCUSSION
	}

	public TopicHistory
	{
		entries = List.copyOf(entries);
	}

	public static TopicHistory of(Conversation conversation, ParagraphId paragraphId)
	{
		if (conversation == null || paragraphId == null)
		{
			throw new DomainException("A conversation and a paragraph are required.");
		}

		Map<ParagraphId, TopicHistoryEntry> sent = entriesBySentParagraph(conversation);

		TopicHistoryEntry chosen = Optional.ofNullable(sent.get(paragraphId))
				.orElseThrow(() -> new DomainException("Paragraph " + paragraphId.value() + " was not sent in this conversation."));

		ConversationGraph graph = conversation.getConversationGraph();
		List<Paragraph> component = graph.findConversationBranchContaining(chosen.paragraph());

		// The conversation always starts with its main discussion, number 0.
		Discussion main = conversation.getDiscussions().getFirst();
		boolean inMain = chosen.discussionId().equals(main.getId());

		Set<ParagraphId> included = new HashSet<>();

		if (inMain)
		{
			component.stream()
					.filter(paragraph -> sent.get(paragraph.getId()).discussionId().equals(main.getId()))
					.forEach(paragraph -> included.add(paragraph.getId()));

			included.addAll(paragraphsOfDiscussionsExpanding(conversation, Set.copyOf(included)));
		}
		else
		{
			component.forEach(paragraph -> included.add(paragraph.getId()));
		}

		// The graph lists every paragraph in sending order, so filtering it keeps the history chronological.
		List<TopicHistoryEntry> entries = graph.getParagraphs().stream()
				.filter(paragraph -> included.contains(paragraph.getId()))
				.map(paragraph -> sent.get(paragraph.getId()))
				.toList();

		return new TopicHistory(chosen.paragraph(), inMain ? Rule.MAIN_CONVERSATION : Rule.SPECIFIC_DISCUSSION, entries);
	}

	// Every paragraph of every discussion opened from one of the given paragraphs, and, in a chain, of the discussions
	// opened from those. Each discussion is visited once.
	private static Set<ParagraphId> paragraphsOfDiscussionsExpanding(Conversation conversation, Set<ParagraphId> origins)
	{
		Set<ParagraphId> collected = new HashSet<>();
		Set<DiscussionId> visited = new HashSet<>();
		Deque<ParagraphId> toExpand = new ArrayDeque<>(origins);

		while (!toExpand.isEmpty())
		{
			ParagraphId origin = toExpand.pop();

			for (Discussion discussion : conversation.getDiscussions())
			{
				if (discussion.getOriginParagraphId().equals(Optional.of(origin)) && visited.add(discussion.getId()))
				{
					for (Message message : discussion.getMessages())
					{
						for (Paragraph paragraph : message.getParagraphs())
						{
							collected.add(paragraph.getId());
							toExpand.push(paragraph.getId());
						}
					}
				}
			}
		}

		return collected;
	}

	private static Map<ParagraphId, TopicHistoryEntry> entriesBySentParagraph(Conversation conversation)
	{
		Map<ParagraphId, TopicHistoryEntry> entries = new HashMap<>();

		for (Discussion discussion : conversation.getDiscussions())
		{
			for (Message message : discussion.getMessages())
			{
				for (Paragraph paragraph : message.getParagraphs())
				{
					entries.put(paragraph.getId(), new TopicHistoryEntry(paragraph, discussion.getId(), message.getId(),
							message.getSenderId(), message.getSentAt()));
				}
			}
		}

		return entries;
	}
}
