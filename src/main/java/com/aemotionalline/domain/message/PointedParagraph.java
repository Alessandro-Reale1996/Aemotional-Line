package com.aemotionalline.domain.message;

import java.util.List;

public class PointedParagraph extends Paragraph
{
	
	
	public PointedParagraph(ParagraphId id, ParagraphType type, String title,
			String subtitle, String body)
	{
		super(id, type, title, subtitle, body);
	}

	@Override
	public ParagraphType getType() 
	{
	    return ParagraphType.POINTED;
	}
	
}