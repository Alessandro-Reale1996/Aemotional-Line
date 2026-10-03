package com.aemotionalline.domain.conversation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;

/**
 * Every paragraph ever sent in a conversation, stored flat. The tree structure is not stored:
 * it is recovered from each paragraph's references, which keeps the graph consistent with the messages
 * and supports the archive's "history by topic" without a second source of truth.
 */
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
	
	public void addAllParagraphsInMessage(Message message)
	{
		for(Paragraph paragraph : message.getParagraphs())
		{
			addParagraph(paragraph);
		}
	}
	
	public void addParagraph(Paragraph paragraph)
	{
		if (paragraph == null)
		{
			throw new DomainException("Paragraph cannot be null.");
		}
		
		paragraphs.add(paragraph);
		
		if (paragraphs.isEmpty())
		{
			throw new DomainException("Paragraph was not added to the ConversationGraph."); 
		}
	}
	
	public List<Paragraph> findRootParagraphs()
	{
	    return paragraphs.stream()
	        .filter(p -> p.getReferences().isEmpty())
	        .toList();
	}
	
	/**
	 * The whole topic a paragraph belongs to: every paragraph connected to it through references, in either
	 * direction, returned in the order they were sent. When a paragraph replies to two topics, the topics are
	 * merged into one branch, and the result is the same whichever paragraph of the branch is passed.
	 * Each paragraph is visited once, so reference cycles can't make the walk loop forever.
	 */
	public List<Paragraph> findConversationBranchContaining(Paragraph paragraph)
	{
	    ensureInGraph(paragraph);

	    Set<ParagraphId> visited = new HashSet<>();
	    Deque<Paragraph> toVisit = new ArrayDeque<>();
	    toVisit.push(paragraph);

	    while (!toVisit.isEmpty())
	    {
	        Paragraph current = toVisit.pop();

	        if (!visited.add(current.getId()))
	        {
	            continue;
	        }

	        for (Paragraph reference : current.getReferences())
	        {
	            toVisit.push(findById(reference.getId()));
	        }

	        for (Paragraph reply : findRepliesTo(current))
	        {
	            toVisit.push(reply);
	        }
	    }

	    return inSendingOrder(visited);
	}
	
	/**
	 * The roots (paragraphs that reference nothing) a paragraph descends from, following every reference,
	 * in the order they were sent. A paragraph that replies to two topics has two roots.
	 * A cycle with no way out has no root at all: that can only come from corrupt data, so it is reported.
	 */
	public List<Paragraph> findRootsOf(Paragraph paragraph)
	{
	    ensureInGraph(paragraph);

	    Set<ParagraphId> visited = new HashSet<>();
	    Set<ParagraphId> roots = new HashSet<>();
	    Deque<Paragraph> toVisit = new ArrayDeque<>();
	    toVisit.push(paragraph);

	    while (!toVisit.isEmpty())
	    {
	        Paragraph current = toVisit.pop();

	        if (!visited.add(current.getId()))
	        {
	            continue;
	        }

	        if (current.getReferences().isEmpty())
	        {
	            roots.add(current.getId());
	        }

	        for (Paragraph reference : current.getReferences())
	        {
	            toVisit.push(findById(reference.getId()));
	        }
	    }

	    if (roots.isEmpty())
	    {
	        throw new DomainException("The paragraph is part of a reference cycle with no root.");
	    }

	    return inSendingOrder(roots);
	}
	
	private void ensureInGraph(Paragraph paragraph)
	{
	    if (paragraph == null)
	    {
	        throw new DomainException("Paragraph cannot be null.");
	    }

	    findById(paragraph.getId());
	}
	
	// The graph's list is in sending order, so filtering it keeps the result chronological and deterministic.
	private List<Paragraph> inSendingOrder(Set<ParagraphId> ids)
	{
	    return paragraphs.stream()
	        .filter(p -> ids.contains(p.getId()))
	        .toList();
	}
	
	public boolean containsParagraph(ParagraphId id)
	{
	    return paragraphs.stream().anyMatch(p -> p.getId().equals(id));
	}
	
	private Paragraph findById(ParagraphId id)
	{
	    return paragraphs.stream()
	        .filter(p -> p.getId().equals(id))
	        .findFirst()
	        .orElseThrow(() -> new DomainException("Paragraph " + id.value() + " is not in the conversation graph."));
	}
	
	public List<Paragraph> findRepliesTo(Paragraph paragraph)
	{
	    if (paragraph == null)
	    {
	        throw new DomainException("Paragraph cannot be null.");
	    }

	    ParagraphId targetId = paragraph.getId();

	    return paragraphs.stream()
	        .filter(p -> hasReferenceTo(p, targetId))
	        .toList();
	}

	private boolean hasReferenceTo(Paragraph paragraph, ParagraphId targetId)
	{
	    return paragraph.getReferences().stream()
	        .map(Paragraph :: getId)
	        .anyMatch(id -> id.equals(targetId));
	}
	
}
