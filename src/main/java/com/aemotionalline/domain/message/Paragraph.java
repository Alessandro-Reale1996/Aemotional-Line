package com.aemotionalline.domain.message;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;

/**
 * The smallest unit of a message and a node of the conversation graph. A paragraph replies to earlier ones
 * through {@link #addReference}; subclasses decide which kinds of paragraph they may reference
 * and how long their text may be, which keeps every message short and focused.
 * It can also cite paragraphs its author wrote earlier ({@link #addCitation}): a citation is only a link in the
 * text and takes no part in rebuilding the conversation. Once the message is sent the paragraph is sealed.
 */
public  abstract class Paragraph 
{
	private final ParagraphId id;
	private final ParagraphType type;
	private final Set<Paragraph> references;
	
	// Citations are a separate kind of link: a pointer to a paragraph the same user wrote earlier, shown as a link
	// in the text. Unlike references they don't rebuild the conversation, so the graph and Analyzer ignore them.
	private final Set<Paragraph> citations;
	
	// Set when the message containing the paragraph is sent: from then on its references can't change.
	private boolean sealed;
 
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

		this.references = new LinkedHashSet<Paragraph>();
		this.citations = new LinkedHashSet<Paragraph>();
		
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
    
	public List<Paragraph> getReferences()
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
	
	
	
	public void addReference(Paragraph reference)
	{
		validateReference(reference);
		
		if (reference.getId().equals(this.id))
		{
			throw new DomainException("Paragraph can't reference it self.");
		}
		
		if (references.contains(reference))
		{
		    throw new DomainException("Reference already exists");
		}
		
	    references.add(reference);
	    
	    if (!this.references.contains(reference))
		{
			throw new DomainException("Reference failed to be added.");
		}
	}

	// Every subclass calls this first, so a sealed paragraph is rejected before any type-specific rule.
	protected void validateReference(Paragraph reference)
	{
	    if (sealed)
	    {
	        throw new DomainException("A sent paragraph can't be changed.");
	    }
	    
	    if(reference == null)
	    {
	        throw new DomainException("Reference can't be null.");
	    }
	}
	
	public List<Paragraph> getCitations()
	{
		return List.copyOf(citations);
	}
	
	// A citation answers nothing, so any kind of paragraph may cite any other: the subclasses' reference rules don't apply.
	// Who wrote the cited paragraph, and whether it was already sent, is checked when the message is sent.
	public void addCitation(Paragraph cited)
	{
		if (sealed)
		{
			throw new DomainException("A sent paragraph can't be changed.");
		}
		
		if (cited == null)
		{
			throw new DomainException("Citation can't be null.");
		}
		
		if (cited.getId().equals(this.id))
		{
			throw new DomainException("Paragraph can't cite itself.");
		}
		
		if (citations.contains(cited))
		{
			throw new DomainException("Citation already exists.");
		}
		
		citations.add(cited);
	}
	
	public boolean isSealed()
	{
		return sealed;
	}
	
	// Package-private: only Message, when it is sent, can seal its paragraphs.
	void seal()
	{
		this.sealed = true;
	}
	
	
	// Identity is the id alone; the exact class check stops two paragraph kinds sharing an id from being equal.
	@Override
	public boolean equals(Object obj)
	{
	    if(this == obj)
	    {
	        return true;
	    }

	    if(obj == null || getClass() != obj.getClass())
	    {
	        return false;
	    }

	    Paragraph paragraph = (Paragraph) obj;

	    return id.equals(paragraph.id);
	}

	@Override
	public int hashCode()
	{
	    return id.hashCode();
	}
    
}
