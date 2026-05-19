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
	
	public List<Paragraph> findRootParagraphs()
	{
	    return paragraphs.stream()
	        .filter(p -> p.getReferences().isEmpty())
	        .toList();
	}
	
	public List<Paragraph> findConversationBranchContaining(Paragraph paragraph)
	{
	    Objects.requireNonNull(paragraph, "Paragraph cannot be null");

	    Paragraph root = findRootOf(paragraph);

	    List<Paragraph> branch = new ArrayList<>();
	    branch.add(root);
	    branch.addAll(findEntireDiscussionTree(root));

	    return List.copyOf(branch);
	}
	
	private List<Paragraph> findEntireDiscussionTree(Paragraph root)
	{
	    Objects.requireNonNull(root, "Root paragraph cannot be null");
	    
	    if(!root.getReferences().isEmpty())
	    {
	    	throw new DomainException("The paragraph to find the discussion tree is must be root.");
	    }

	    List<Paragraph> result = new ArrayList<>();

	    collectRepliesRecursively(root, result);

	    return List.copyOf(result);
	}
	
	private void collectRepliesRecursively(Paragraph paragraph, List<Paragraph> result)
	{
	    List<Paragraph> directReplies = findRepliesTo(paragraph);

	    for (Paragraph reply : directReplies)
	    {
	        result.add(reply);

	        collectRepliesRecursively(reply, result);
	    }
	}
	
	
	private Paragraph findRootOf(Paragraph paragraph)
	{
	    Paragraph current = paragraph;

	    while (!current.getReferences().isEmpty())
	    {
	        ParagraphReference firstReference = current.getReferences().get(0);

	        current = findById(firstReference.referencedParagraphId());
	    }

	    return current;
	}
	
	private Paragraph findById(ParagraphId id)
	{
	    return paragraphs.stream()
	        .filter(p -> p.getId().equals(id))
	        .findFirst()
	        .orElseThrow(() -> new DomainException("Referenced paragraph not found"));
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
