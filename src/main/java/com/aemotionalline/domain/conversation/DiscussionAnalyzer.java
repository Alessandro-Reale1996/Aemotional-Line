package com.aemotionalline.domain.conversation;

import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.message.Paragraph;

public class DiscussionAnalyzer
{
    private final ConversationGraph graph;

    public DiscussionAnalyzer(ConversationGraph graph)
    {
        this.graph = Objects.requireNonNull(graph, "Conversation graph cannot be null");
    }

    public DiscussionAnalysis analyze(Paragraph paragraph)
    {
    	Objects.requireNonNull(paragraph, "Paragraph cannot be null");

	    List<Paragraph> branch = graph.findConversationBranchContaining(paragraph);

	    DiscussionStatus status = determineStatus(branch);

	    return new DiscussionAnalysis(paragraph, branch, status);
    }

    private DiscussionStatus determineStatus(List<Paragraph> tree)
    {
        if (tree.isEmpty())
        {
            return DiscussionStatus.UNANSWERED;
        }

        return DiscussionStatus.ACTIVE;
    }
}