package com.aemotionalline.domain.message;

public class PointedParagraph extends Paragraph
{
	private final ParagraphType type = ParagraphType.POINTED;

	public PointedParagraph(ParagraphType type, String title, String subtitle, String body)
	{
		super(type, title, subtitle, body);
	}
	
	@Override
    public ParagraphType getType() 
	{
        return type;
    }
	
}