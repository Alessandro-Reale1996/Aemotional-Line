package com.aemotionalline.domain.message;

public class SimpleParagraph extends Paragraph
{
	public SimpleParagraph(ParagraphType type, String title, String subtitle, String body)
	{
		super(type, title, subtitle, body);
	}
	
	@Override
	public ParagraphType getType() 
	{
	    return ParagraphType.SIMPLE;
	}
	
}
