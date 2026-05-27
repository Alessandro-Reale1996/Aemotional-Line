package com.aemotionalline.domain.conversation;

import java.util.List;

import com.aemotionalline.domain.message.Paragraph;

public record DiscussionAnalysis
(
	Paragraph rootParagraph,
	List<Paragraph> discussionTree,
	DiscussionStatus status
)

{
	public DiscussionAnalysis
    {
        discussionTree = List.copyOf(discussionTree);
    }
}
