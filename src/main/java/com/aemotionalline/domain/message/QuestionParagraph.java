package com.aemotionalline.domain.message;

import com.aemotionalline.domain.common.DomainException;

public class QuestionParagraph extends Paragraph
{
	
	private static final int MAX_TITLE_LENGTH = 250;
	private static final int MAX_BODY_LENGTH = 350;
	
	public QuestionParagraph
	(ParagraphId id, ParagraphType type, String title, String body)
	{
		super(id, type, title, null, body);
		
		if(title != null && title.length() > MAX_BODY_LENGTH)
        {
            throw new DomainException(
                "Paragraph body cannot exceed "
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

	@Override
    public ParagraphType getType() 
	{
        return ParagraphType.QUESTION;
    }
	
	public boolean isAnswered()
	{
		boolean retvalue = false;
				
	    if(!getReferences().isEmpty())
	    {
	    	retvalue = true;
	    }
	    
	    return retvalue;
	}
	
	@Override
	public void addReference(Paragraph reference)
	{
		validateReference(reference);
		
		if (reference.getType().equals(ParagraphType.QUESTION))
		{
			throw new DomainException("Only PointedPAragraph can reference a Question.");
		}
		
		super.addReference(reference);
	}
	
	
}