package com.aemotionalline.application.view;

import java.util.List;

import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.Discussion;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.user.UserId;

/**
 * Builds the views out of domain objects, inside the service's transaction, so that no domain object leaves the
 * application layer. It also applies the closed-envelope rule.
 */
public final class Views
{
	private Views()
	{
	}

	/**
	 * Whether the viewer may see a message's content: the therapist always, a partner their own messages, and the other
	 * partner's only once they have opened it (the read operation checked the read constraints). After that first opening
	 * the message is part of the freely consultable archive.
	 */
	public static boolean canSeeContent(UserId viewer, Couple couple, Discussion discussion, Message message)
	{
		return couple.isTherapist(viewer) || message.getSenderId().equals(viewer) || discussion.getReadAt(message.getId()).isPresent();
	}

	public static ConversationView conversation(Conversation conversation, UserId viewer)
	{
		String agreement = ((AgreementText) conversation.getAgreement().getContent()).text();

		List<DiscussionView> discussions = conversation.getDiscussions().stream()
				.map(discussion -> discussion(discussion, conversation.getCouple(), viewer))
				.toList();

		return new ConversationView(conversation.getId(), conversation.getCouple().getId(), agreement, discussions);
	}

	public static DiscussionView discussion(Discussion discussion, Couple couple, UserId viewer)
	{
		List<MessageView> messages = discussion.getMessages().stream()
				.map(message -> message(message, canSeeContent(viewer, couple, discussion, message), discussion))
				.toList();

		return new DiscussionView(discussion.getId(), discussion.getTitle().map(title -> title.title()),
				discussion.getTitle().map(title -> title.subtitle()), discussion.getOriginParagraphId(), discussion.isClosed(),
				java.util.Optional.ofNullable(discussion.getLastSender()), messages);
	}

	private static MessageView message(Message message, boolean visible, Discussion discussion)
	{
		List<ParagraphView> paragraphs = visible ? message.getParagraphs().stream().map(Views::paragraph).toList() : List.of();

		return new MessageView(message.getId(), message.getSenderId(), message.getSentAt(), !visible, discussion.getReadAt(message.getId()),
				paragraphs, visible ? message.getQuestionsLeftUnanswered() : List.of());
	}

	public static ParagraphView paragraph(Paragraph paragraph)
	{
		return new ParagraphView(paragraph.getId(), paragraph.getType(), paragraph.getTitle(), paragraph.getSubtitle(), paragraph.getBody(),
				paragraph.getReferences().stream().map(Paragraph::getId).toList(),
				paragraph.getSelfCitations().stream().map(Paragraph::getId).toList());
	}
}
