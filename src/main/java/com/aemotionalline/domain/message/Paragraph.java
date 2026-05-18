package com.aemotionalline.domain.message;

import java.util.ArrayList;
import java.util.List;

import com.aemotionalline.domain.common.DomainException;

public  abstract class Paragraph 
{
	private final ParagraphId id;
	private final ParagraphType type;
	private final List<ParagraphReference> references;
    private final String title;
    private final String subtitle;
    private final String body;
	
    
    
    public Paragraph(ParagraphId id, ParagraphType type, String title, String subtitle, String body)
	{
    	
		super();
		
		if (id == null)
		{
			throw new DomainException("Paragraph's id can't be null.");
		}
		
        if (title == null || title.isBlank()) 
        {
            throw new DomainException("Paragraph title cannot be blank");
        }

        if (body == null || body.isBlank()) 
        {
            throw new DomainException("Paragraph body cannot be blank");
        }
		
        this.id = id;
		this.type = type;
		this.references = new ArrayList<ParagraphReference>();
		this.title = title;
		this.subtitle = subtitle;
		this.body = body;
	}
    
    

	public ParagraphId getId()
	{
		return id;
	}

	public ParagraphType getType() 
    {
		return type;
	}
    
	public List<ParagraphReference> getReferences()
	{
		return List.copyOf(references);
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
	
	
	
	public void addReference(ParagraphReference reference)
	{
		if (reference == null)
		{
			throw new DomainException("Reference can't ne null.");
		}
		
		if (reference.referencedParagraphId().equals(this.id))
		{
			throw new DomainException("Paragraph can't reference it self.");
		}
		
		if (references.contains(reference))
		{
		    throw new DomainException("Reference already exists");
		}
		
	    references.add(reference);
	    
	    if (this.references.isEmpty())
		{
			throw new DomainException("Reference failed to be added.");
		}
	}

	
    
}
