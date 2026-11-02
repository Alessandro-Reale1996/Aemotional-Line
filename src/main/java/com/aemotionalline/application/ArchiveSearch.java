package com.aemotionalline.application;

import java.time.Instant;
import java.util.List;

import com.aemotionalline.application.view.ArchiveEntry;
import com.aemotionalline.application.view.ArchiveSearchResult;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.user.UserId;

/**
 * The archive's searches by time and keyword, as read-only queries that don't load the conversation; implemented by the
 * persistence layer. They apply the closed-envelope rule for the viewer: {@code seesEverything} is true for the therapist;
 * otherwise a message is visible if the viewer sent it or it has been opened.
 */
public interface ArchiveSearch
{
	/** The messages sent in {@code [from, to)}: visible ones paragraph by paragraph, unopened ones as one closed envelope each. */
	List<ArchiveEntry> messagesBetween(ConversationId conversation, UserId viewer, boolean seesEverything, Instant from, Instant to);

	/** Paragraphs containing the text (unopened messages excluded entirely) and proposals containing it. */
	ArchiveSearchResult search(ConversationId conversation, UserId viewer, boolean seesEverything, String text);
}
