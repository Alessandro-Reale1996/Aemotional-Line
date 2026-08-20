package com.aemotionalline.domain.conversation;

import java.util.List;

import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.QuestionParagraph;

public class Analyzer
{
	public static List<QuestionParagraph> conversationNotAnsweredQuestions(Conversation conversation)
	{
		return	conversation.getConversationGraph().getParagraphs()
			.stream()
			.filter(paragraph -> paragraph.getType() == ParagraphType.QUESTION)
			.map(paragraph -> (QuestionParagraph) paragraph)
			.filter(question -> !question.isAnswered())
			.toList();
	}

	public static List<QuestionParagraph> discussionNotAnsweredQuestion(Discussion discussion)
	{
		return  discussion.getMessages()
				.stream()
				.flatMap(message -> message.getParagraphs().stream())
				.filter(paragraph -> paragraph.getType() == ParagraphType.QUESTION)
				.map(paragraph -> (QuestionParagraph) paragraph)
				.filter(question -> !question.isAnswered())
				.toList();
	}
}
