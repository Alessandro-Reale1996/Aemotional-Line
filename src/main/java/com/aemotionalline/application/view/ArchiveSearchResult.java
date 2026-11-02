package com.aemotionalline.application.view;

import java.util.List;

/** What a keyword search found: paragraphs in sending order (never of a message the viewer hasn't opened), then proposals (those that took effect first). */
public record ArchiveSearchResult(List<ArchiveEntry> paragraphs, List<ArchiveProposal> proposals)
{
	public ArchiveSearchResult
	{
		paragraphs = List.copyOf(paragraphs);
		proposals = List.copyOf(proposals);
	}

	public boolean isEmpty()
	{
		return paragraphs.isEmpty() && proposals.isEmpty();
	}
}
