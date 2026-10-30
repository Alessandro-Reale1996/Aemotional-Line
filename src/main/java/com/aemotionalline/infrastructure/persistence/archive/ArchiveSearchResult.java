package com.aemotionalline.infrastructure.persistence.archive;

import java.util.List;

/** What a keyword search found: paragraphs in sending order, then proposals (those that took effect first). */
public record ArchiveSearchResult(List<ArchiveParagraph> paragraphs, List<ArchiveProposal> proposals)
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
