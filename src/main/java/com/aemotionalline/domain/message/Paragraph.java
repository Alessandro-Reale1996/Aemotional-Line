package com.aemotionalline.domain.message;

import com.aemotionalline.domain.common.DomainException;

public class Paragraph 
{
	private final ParagraphType type;
    private final String title;
    private final String subtitle;
    private final String body;
	
    
    
    public Paragraph(ParagraphType type, String title, String subtitle, String body)
	{
    	
		super();
		

        if (title == null || title.isBlank()) 
        {
            throw new DomainException("Paragraph title cannot be blank");
        }

        if (body == null || body.isBlank()) 
        {
            throw new DomainException("Paragraph body cannot be blank");
        }
        
		this.type = type;
		this.title = title;
		this.subtitle = subtitle;
		this.body = body;
	}

	public ParagraphType getType() 
    {
		return type;
	}
    
	public String getTitle()
	{
		return title;
	}
	
	public String getSubtitle() 
	{
		return subtitle;
	}
	
	public String getBody() 
	{
		return body;
	}
	
	

	
    
}
