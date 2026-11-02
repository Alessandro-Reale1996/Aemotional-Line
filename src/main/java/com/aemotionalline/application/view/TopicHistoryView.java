package com.aemotionalline.application.view;

import java.util.List;

import com.aemotionalline.domain.conversation.TopicHistory;
import com.aemotionalline.domain.message.ParagraphId;

/**
 * The history by topic of a paragraph (spec 1.3.4) as one viewer sees it: the domain's TopicHistory, with the paragraphs of
 * messages the viewer hasn't opened shown as closed envelopes in their place.
 */
public record TopicHistoryView(ParagraphId paragraphId, TopicHistory.Rule rule, List<ArchiveEntry> entries)
{
	public TopicHistoryView
	{
		entries = List.copyOf(entries);
	}
}
