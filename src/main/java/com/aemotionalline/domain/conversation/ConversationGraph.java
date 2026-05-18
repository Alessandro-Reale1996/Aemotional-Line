package com.aemotionalline.domain.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphReference;

public class ConversationGraph
{
	private final List<Paragraph> paragraphs;
	
	public ConversationGraph()
	{
		this.paragraphs = new ArrayList<Paragraph>();
	}
	
	public List<Paragraph> getParagraphs()
	{
		return List.copyOf(paragraphs);
	}
	
	public void addParagraph(Paragraph paragraph)
	{
		Objects.requireNonNull(paragraph);
		
		paragraphs.add(paragraph);
		
		if (paragraphs.isEmpty())
		{
			throw new DomainException("Paragraph non added at ConversationGraph's paragraphs."); 
		}
	}
	
	public List<Paragraph> findRepliesTo(Paragraph paragraph)
	{
	    Objects.requireNonNull(paragraph);

	    ParagraphId targetId = paragraph.getId();

	    return paragraphs.stream()
	        .filter(p -> hasReferenceTo(p, targetId))
	        .toList();
	}

	private boolean hasReferenceTo(Paragraph paragraph, ParagraphId targetId)
	{
	    return paragraph.getReferences().stream()
	        .map(ParagraphReference::referencedParagraphId)
	        .anyMatch(id -> id.equals(targetId));
	}
	
}
