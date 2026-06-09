package com.aemotionalline.domain.message;

import java.util.List;

public class QuestionParagraph extends Paragraph
{
	
	public QuestionParagraph
	(ParagraphId id, ParagraphType type, String title, String subtitle, String body)
	{
		super(id, type, title, subtitle, body);
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
	
	
}