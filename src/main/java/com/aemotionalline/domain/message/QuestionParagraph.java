package com.aemotionalline.domain.message;

import com.aemotionalline.domain.common.DomainException;

/**
 * A question addressed to the other partner. It stays "open" until a {@link PointedParagraph} references it,
 * which is what {@code Analyzer} uses to report unanswered questions.
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
			throw new DomainException("Only PointedParagraphs can reference a Question.");
		}
		
		super.addReference(reference);
	}
	
	
}