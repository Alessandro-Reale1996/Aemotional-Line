package com.aemotionalline.domain.message;

import com.aemotionalline.domain.common.DomainException;

/**
 * A question addressed to the other partner. It stays "open" until a {@link PointedParagraph} references it.
 * The question itself can't tell: references point from the reply to the question, and the reply doesn't exist
 * yet when the question is sent. So {@code Analyzer} decides it from the replies found in the conversation.
 */
public class QuestionParagraph extends Paragraph
{
	
	private static final int MAX_TITLE_LENGTH = 250;
	private static final int MAX_BODY_LENGTH = 350;
	
	public QuestionParagraph
	(ParagraphId id, String title, String body)
	{
		super(id, ParagraphType.QUESTION, title, null, body);

		if(title != null && title.length() > MAX_TITLE_LENGTH)
        {
            throw new DomainException(
                "Paragraph title cannot exceed "
                + MAX_TITLE_LENGTH
                + " characters");
        }
		
		if(body != null && body.length() > MAX_BODY_LENGTH)
        {
            throw new DomainException(
                "Paragraph body cannot exceed "
                + MAX_BODY_LENGTH
                + " characters");
        }
	}
}
