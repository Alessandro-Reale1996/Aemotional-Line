package com.aemotionalline.domain.message;


import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.conversation.ConversationGraph;

public class QuestionTracker
{
	private final ConversationGraph graph;

	public QuestionTracker(ConversationGraph graph)
	{
		super();
		this.graph = Objects.requireNonNull(graph, "Conversation graph can't be null.");
	}
	
	public boolean isAnswered(QuestionParagraph question)
	{
		Objects.requireNonNull(question, "Question cannot be null.");
		
		boolean isAswered = !graph.findRepliesTo(question).isEmpty();
		
		return isAswered;
	}
	
	public List<QuestionParagraph> findUnansweredQuestions()
    {
        return graph.getParagraphs().stream()
            .filter(paragraph -> paragraph instanceof QuestionParagraph)
            .map(paragraph -> (QuestionParagraph) paragraph)
            .filter(question -> !isAnswered(question))
            .toList();
    }
	
}
