package com.aemotionalline.application.view;

/**
 * The limits a text must respect (CLAUDE.md, REST point 4), from the domain's constants, so the frontend can show
 * counters without copying the numbers. Text lengths are counted in UTF-16 code units, like JavaScript's String.length;
 * the password in Unicode code points, and in UTF-8 bytes for bcrypt's limit.
 */
public record LimitsView(
		String textUnit,
		int simpleParagraphTitle,
		int simpleParagraphSubtitle,
		int simpleParagraphBody,
		int questionTitle,
		int questionBody,
		int pointedAnswerBody,
		int discussionTitle,
		int discussionSubtitle,
		int outcomeReason,
		int therapistRequestJustification,
		int displayName,
		int email,
		String passwordUnit,
		int passwordMinLength,
		int passwordMaxLength,
		int passwordMaxBytes)
{
}
