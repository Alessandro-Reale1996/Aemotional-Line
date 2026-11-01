package com.aemotionalline.application;

import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;

/**
 * A paragraph of a message the client wants to send, before it exists: its text and the ids of the sent paragraphs it
 * answers ({@code references}), cites ({@code selfCitations}) or, for a pointed paragraph, the question it answers (a
 * pointed paragraph has no other reference and no title: its title is computed). The server creates the paragraph's id.
 */
public record ParagraphDraft(ParagraphType type, String title, String subtitle, String body, List<ParagraphId> references,
		List<ParagraphId> selfCitations, Optional<ParagraphId> question)
{
	public ParagraphDraft
	{
		references = references == null ? List.of() : List.copyOf(references);
		selfCitations = selfCitations == null ? List.of() : List.copyOf(selfCitations);
		question = question == null ? Optional.empty() : question;
	}
}
