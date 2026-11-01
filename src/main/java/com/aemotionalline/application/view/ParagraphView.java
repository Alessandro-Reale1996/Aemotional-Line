package com.aemotionalline.application.view;

import java.util.List;

import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;

/** A paragraph as the client sees it: its text, the paragraphs it answers and the ones it cites. */
public record ParagraphView(ParagraphId id, ParagraphType type, String title, String subtitle, String body, List<ParagraphId> references,
		List<ParagraphId> selfCitations)
{
	public ParagraphView
	{
		references = List.copyOf(references);
		selfCitations = List.copyOf(selfCitations);
	}
}
