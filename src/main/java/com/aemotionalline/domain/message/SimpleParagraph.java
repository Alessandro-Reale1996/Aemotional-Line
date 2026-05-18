package com.aemotionalline.domain.message;

import java.util.List;

public class SimpleParagraph extends Paragraph
{
	
	public SimpleParagraph(ParagraphId id, ParagraphType type, String title,
			String subtitle, String body)
	{
		super(id, type, title, subtitle, body);
	}

	@Override
	public ParagraphType getType() 
	{
	    return ParagraphType.SIMPLE;
	}
	
}
