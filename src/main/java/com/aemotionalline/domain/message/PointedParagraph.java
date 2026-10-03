package com.aemotionalline.domain.message;

import com.aemotionalline.domain.common.DomainException;

/**
 * A direct answer to a question. It may reference only {@link QuestionParagraph}s, and this is the only
 * paragraph kind allowed to do so, which makes "who answered which question" unambiguous in the graph.
 * It answers exactly one question: a second reference is rejected here, while the requirement of having
 * one at all is checked when the message is sent (a paragraph is built before its reference is added).
 */
public class PointedParagraph extends Paragraph
{
	private static final int MAX_BODY_LENGTH = 500;
	
	public PointedParagraph
	(ParagraphId id, String title, String body)
	{
		super(id, ParagraphType.POINTED, title, null, body);
		
		if(body != null && body.length() > MAX_BODY_LENGTH)
        {
            throw new DomainException(
                "Paragraph body cannot exceed "
                + MAX_BODY_LENGTH
                + " characters");
        }
	}

	@Override
	public void addReference(Paragraph reference)
	{
		validateReference(reference);
		
		if (!reference.getType().equals(ParagraphType.QUESTION))
		{
			throw new DomainException("PointedParagraphs can reference only Questions.");
		}
		
		if (!getReferences().isEmpty())
		{
			throw new DomainException("A pointed paragraph answers exactly one question.");
		}
		
		super.addReference(reference);
	}
	
	
}