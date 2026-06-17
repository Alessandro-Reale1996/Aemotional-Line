package com.aemotionalline.domain.message;

import com.aemotionalline.domain.common.DomainException;

public class PointedParagraph extends Paragraph
{
	private static final int MAX_BODY_LENGTH = 500;
	
	public PointedParagraph
	(ParagraphId id, ParagraphType type, String title, String body)
	{
		super(id, type, title, null, body);
		
		if(body != null && body.length() > MAX_BODY_LENGTH)
        {
            throw new DomainException(
                "Paragraph body cannot exceed "
                + MAX_BODY_LENGTH
                + " characters");
        }
	}

	@Override
	public ParagraphType getType() 
	{
	    return ParagraphType.POINTED;
	}
	
	@Override
	public void addReference(Paragraph reference)
	{
		validateReference(reference);
		
		if (!reference.getType().equals(ParagraphType.QUESTION))
		{
			throw new DomainException("PointedPAragraph can reference only Question.");
		}
		
		super.addReference(reference);
	}
	
	
}