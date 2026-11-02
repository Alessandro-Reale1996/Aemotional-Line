package com.aemotionalline.application.view;

import java.util.List;

/**
 * The questions of the conversation the viewer can see that have no answer: {@code stillOpen} also holds those still waiting
 * for a reply, {@code leftUnanswered} only those a reply skipped for good.
 */
public record UnansweredQuestionsView(List<ArchiveEntry> stillOpen, List<ArchiveEntry> leftUnanswered)
{
	public UnansweredQuestionsView
	{
		stillOpen = List.copyOf(stillOpen);
		leftUnanswered = List.copyOf(leftUnanswered);
	}
}
