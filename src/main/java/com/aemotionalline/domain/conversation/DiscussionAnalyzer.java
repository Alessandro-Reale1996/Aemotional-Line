package com.aemotionalline.domain.conversation;

import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.QuestionParagraph;

public class DiscussionAnalyzer
{
	private final ConversationGraph graph;

	public DiscussionAnalyzer(ConversationGraph graph)
	{
		this.graph = graph;
	}
	
	public DiscussionAnalysis analyze(Paragraph root)
	{
		Objects.nonNull(root);
		
		List<Paragraph> tree = graph.findConversationBranchContaining(root);
		
		DiscussionStatus status = determineStatus(root, tree);
		
		DiscussionAnalysis retvalue = new DiscussionAnalysis(root, tree, status)
		
		return retvalue;
		
	}
	
	  private DiscussionStatus determineStatus(Paragraph root, List<Paragraph> tree)
	    {
	        if (tree.isEmpty())
	        {
	            return DiscussionStatus.UNANSWERED;
	        }

	        if (root instanceof QuestionParagraph)
	        {
	            return DiscussionStatus.OPEN;
	        }

	        return DiscussionStatus.RESOLVED;
	    }
}
