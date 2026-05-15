package com.aemotionalline.domain.message;

public class QuestionParagraph extends Paragraph
{

	public QuestionParagraph(ParagraphType type, String title, String subtitle, String body)
	{
		super(type, title, subtitle, body);
	}
	
	@Override
    public ParagraphType getType() 
	{
        return ParagraphType.SIMPLE;
    }
	
}