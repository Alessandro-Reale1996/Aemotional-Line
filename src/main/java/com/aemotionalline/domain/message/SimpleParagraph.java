package com.aemotionalline.domain.message;

import com.aemotionalline.domain.common.DomainException;

/**
 * A free-form paragraph with the most generous limits. It can refer to any paragraph. Whether it may refer to a
 * question depends on the message it is sent in (only a {@link PointedParagraph} answers a question, except in the
 * first message of a discussion that expands it), so that is checked when the message is sent.
 */
public class SimpleParagraph extends Paragraph
{
	
	private static final int MAX_TITLE_LENGTH = 100;
	private static final int MAX_SUBTITLE_LENGTH = 200;
	private static final int MAX_BODY_LENGTH = 800;
	
	public SimpleParagraph
	(ParagraphId id, String title, String subtitle, String body)
	{
		super(id, ParagraphType.SIMPLE, title, subtitle, body);
		
		
		if(title != null && title.length() > MAX_TITLE_LENGTH)
        {
            throw new DomainException(
                "Paragraph title cannot exceed "
                + MAX_TITLE_LENGTH
                + " characters");
        }
		
		if(subtitle != null && subtitle.length() > MAX_SUBTITLE_LENGTH)
        {
            throw new DomainException(
                "Paragraph subtitle cannot exceed "
                + MAX_SUBTITLE_LENGTH
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
