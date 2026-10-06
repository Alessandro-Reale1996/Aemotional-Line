package com.aemotionalline.domain.conversation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.QuestionParagraph;

/**
 * Read-only queries over a conversation. Kept outside the aggregate so that reporting needs
 * (such as finding open questions) do not enlarge the write-side API of {@link Conversation}.
 * A question counts as answered once a POINTED paragraph references it, or once a discussion opened to expand it
 * has its first message. References point from the reply to the question, so the answer is looked up among the
 * other paragraphs, never on the question itself.
 */
public class Analyzer
{
	public static List<QuestionParagraph> conversationNotAnsweredQuestions(Conversation conversation)
	{
		return	conversation.getConversationGraph().getParagraphs()
			.stream()
			.filter(paragraph -> paragraph.getType() == ParagraphType.QUESTION)
			.map(paragraph -> (QuestionParagraph) paragraph)
			.filter(question -> !isAnswered(question, conversation))
			.toList();
	}

	// The questions written in this discussion that are still open. The answer may be in another discussion
	// (the one opened to expand the question), so the whole conversation is needed to decide.
	public static List<QuestionParagraph> discussionNotAnsweredQuestion(Conversation conversation, Discussion discussion)
	{
		return  discussion.getMessages()
				.stream()
				.flatMap(message -> message.getParagraphs().stream())
				.filter(paragraph -> paragraph.getType() == ParagraphType.QUESTION)
				.map(paragraph -> (QuestionParagraph) paragraph)
				.filter(question -> !isAnswered(question, conversation))
				.toList();
	}

	/**
	 * The questions a partner chose not to answer: they were in the message being answered, and the reply skipped them.
	 * Since a question can only be answered by the very next message, these stay unanswered for good, unlike the
	 * questions of the last message, which are still waiting for a reply (see conversationNotAnsweredQuestions).
	 */
	public static List<QuestionParagraph> questionsLeftUnanswered(Conversation conversation)
	{
		Set<ParagraphId> skipped = new HashSet<>();
		
		for (Discussion discussion : conversation.getDiscussions())
		{
			for (Message message : discussion.getMessages())
			{
				skipped.addAll(message.getQuestionsLeftUnanswered());
			}
		}
		
		return conversation.getConversationGraph().getParagraphs()
				.stream()
				.filter(paragraph -> skipped.contains(paragraph.getId()))
				.map(paragraph -> (QuestionParagraph) paragraph)
				.toList();
	}

	// Package-private: Conversation uses the same rule to preview which questions a reply would leave unanswered.
	static boolean isAnswered(QuestionParagraph question, Conversation conversation)
	{
		return hasPointedReply(conversation.getConversationGraph().findRepliesTo(question))
				|| hasDiscussionAnswering(question, conversation);
	}

	private static boolean hasPointedReply(List<Paragraph> replies)
	{
		return replies.stream().anyMatch(reply -> reply.getType() == ParagraphType.POINTED);
	}

	// A discussion without messages yet has not answered anything.
	private static boolean hasDiscussionAnswering(QuestionParagraph question, Conversation conversation)
	{
		return conversation.getDiscussions()
				.stream()
				.anyMatch(discussion -> discussion.getOriginParagraphId().filter(origin -> origin.equals(question.getId())).isPresent()
						&& !discussion.getMessages().isEmpty());
	}
}
