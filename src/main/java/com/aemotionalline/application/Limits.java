package com.aemotionalline.application;

import com.aemotionalline.application.view.LimitsView;
import com.aemotionalline.domain.conversation.OutcomeDeclaration;
import com.aemotionalline.domain.conversation.TherapistClosingRequestRecord;
import com.aemotionalline.domain.message.PointedParagraph;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.User;

/** The text limits in one place, read from the domain and the password policy: one source of truth. */
public final class Limits
{
	private static final LimitsView CURRENT = new LimitsView(
			"UTF_16_CODE_UNITS",
			SimpleParagraph.MAX_TITLE_LENGTH,
			SimpleParagraph.MAX_SUBTITLE_LENGTH,
			SimpleParagraph.MAX_BODY_LENGTH,
			QuestionParagraph.MAX_TITLE_LENGTH,
			QuestionParagraph.MAX_BODY_LENGTH,
			PointedParagraph.MAX_BODY_LENGTH,
			DiscussionTitle.MAX_TITLE_LENGTH,
			DiscussionTitle.MAX_SUBTITLE_LENGTH,
			OutcomeDeclaration.MAX_REASON_LENGTH,
			TherapistClosingRequestRecord.MAX_JUSTIFICATION_LENGTH,
			User.MAX_DISPLAY_NAME_LENGTH,
			EmailAddress.MAX_LENGTH,
			"CODE_POINTS",
			PasswordPolicy.MIN_LENGTH,
			PasswordPolicy.MAX_LENGTH,
			PasswordPolicy.MAX_BYTES);

	private Limits()
	{
	}

	public static LimitsView current()
	{
		return CURRENT;
	}
}
