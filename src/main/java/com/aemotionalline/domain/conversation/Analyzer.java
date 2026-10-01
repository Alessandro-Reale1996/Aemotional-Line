package com.aemotionalline.domain.conversation;

import java.util.List;

import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.QuestionParagraph;

/**
 * Read-only queries over a conversation. Kept outside the aggregate so that reporting needs
 * (such as finding open questions) do not enlarge the write-side API of {@link Conversation}.
 * A question counts as answered once a POINTED paragraph references it. References point from the reply
 * to the question, so the answer is looked up among the other paragraphs, never on the question itself.
 */
public class Analyzer
{
	public static List<QuestionParagraph> conversationNotAnsweredQuestions(Conversation conversation)
	{
		ConversationGraph graph = conversation.getConversationGraph();
		
		return	graph.getParagraphs()
			.stream()
			.filter(paragraph -> paragraph.getType() == ParagraphType.QUESTION)
			.map(paragraph -> (QuestionParagraph) paragraph)
			.filter(question -> !hasPointedReply(graph.findRepliesTo(question)))
			.toList();
	}

	// A discussion has no graph of its own: the replies are searched among the paragraphs of its messages.
	public static List<QuestionParagraph> discussionNotAnsweredQuestion(Discussion discussion)
	{
		List<Paragraph> paragraphs = discussion.getMessages()
				.stream()
				.flatMap(message -> message.getParagraphs().stream())
				.toList();
		
		return  paragraphs
				.stream()
				.filter(paragraph -> paragraph.getType() == ParagraphType.QUESTION)
				.map(paragraph -> (QuestionParagraph) paragraph)
				.filter(question -> !hasPointedReply(repliesTo(question, paragraphs)))
				.toList();
	}
	
	private static List<Paragraph> repliesTo(QuestionParagraph question, List<Paragraph> paragraphs)
	{
		return paragraphs
				.stream()
				.filter(paragraph -> paragraph.getReferences()
						.stream()
						.anyMatch(reference -> reference.getId().equals(question.getId())))
				.toList();
	}
	
	private static boolean hasPointedReply(List<Paragraph> replies)
	{
		return replies.stream().anyMatch(reply -> reply.getType() == ParagraphType.POINTED);
	}
}
