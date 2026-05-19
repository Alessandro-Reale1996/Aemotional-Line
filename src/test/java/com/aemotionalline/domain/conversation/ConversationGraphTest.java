package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphReference;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.SimpleParagraph;

public class ConversationGraphTest 
{
	@Test
	void shouldThrowExceptionWhenAddingNullParagraph()
	{
		ConversationGraph graph = new ConversationGraph();
		
		assertThrows(NullPointerException.class,() -> graph.addParagraph(null));
		
	}
	
	@Test
	void shouldAddParagraphSuccessfully()
	{
		ConversationGraph graph = new ConversationGraph();
		
		graph.addParagraph(new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body"));
		
		assertEquals(1, graph.getParagraphs().size());
	}
	
	@Test
	void shouldAddParagraphCorrectly()
	{
		ConversationGraph graph = new ConversationGraph();
		
		graph.addParagraph(new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body"));
		
		assertEquals(1, graph.getParagraphs().size());
	}
	
	@Test
	void shouldFindDirectCorrelatedParagraph()
	{
		ConversationGraph graph = new ConversationGraph();
		
		SimpleParagraph rootParagraph = 
				new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		graph.addParagraph(rootParagraph);
		
		SimpleParagraph referencingParagraph = 
				new SimpleParagraph(new ParagraphId(20L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		ParagraphReference reference = new ParagraphReference(rootParagraph.getId());
		
		referencingParagraph.addReference(reference);
		
		graph.addParagraph(referencingParagraph);
		
		List<Paragraph> found = graph.findRepliesTo(rootParagraph);
		
		assertEquals(1, found.size());
		assertEquals(referencingParagraph.getId(), found.get(0).getId());
		
	}
	
	@Test
	void shouldFindIndirectAllCorrelatedParagraph()
	{
		ConversationGraph graph = new ConversationGraph();
		
		SimpleParagraph rootParagraph = 
				new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		graph.addParagraph(rootParagraph);
		
		SimpleParagraph directReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(20L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		ParagraphReference directReference = new ParagraphReference(rootParagraph.getId());
		
		directReferenceParagraph.addReference(directReference);
		
		graph.addParagraph(directReferenceParagraph);
		

		SimpleParagraph undirectReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(30L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		ParagraphReference undirectReference = new ParagraphReference(directReferenceParagraph.getId());
		
		undirectReferenceParagraph.addReference(undirectReference);
		
		graph.addParagraph(undirectReferenceParagraph);
		
		List<Paragraph> found = graph.findConversationBranchContaining(undirectReferenceParagraph);
		
		assertEquals(3, found.size());
		assertEquals(rootParagraph.getId(), found.get(0).getId());
		assertEquals(directReferenceParagraph.getId(), found.get(1).getId());
		assertEquals(undirectReferenceParagraph.getId(), found.get(2).getId());
	}
	
	@Test
	void shouldFindIndirectAllCorrelatedRootParagraph()
	{
		ConversationGraph graph = new ConversationGraph();
		
		SimpleParagraph rootParagraph = 
				new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		graph.addParagraph(rootParagraph);
		
		SimpleParagraph directReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(20L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		ParagraphReference directReference = new ParagraphReference(rootParagraph.getId());
		
		directReferenceParagraph.addReference(directReference);
		
		graph.addParagraph(directReferenceParagraph);
		

		SimpleParagraph undirectReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(30L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		ParagraphReference undirectReference = new ParagraphReference(directReferenceParagraph.getId());
		
		undirectReferenceParagraph.addReference(undirectReference);
		
		graph.addParagraph(undirectReferenceParagraph);
		
		List<Paragraph> found = graph.findConversationBranchContaining(rootParagraph);
		
		assertEquals(3, found.size());
		assertEquals(rootParagraph.getId(), found.get(0).getId());
		assertEquals(directReferenceParagraph.getId(), found.get(1).getId());
		assertEquals(undirectReferenceParagraph.getId(), found.get(2).getId());
	}
	
	@Test
	void shouldFindRootParagraph()
	{
		ConversationGraph graph = new ConversationGraph();
		
		SimpleParagraph rootParagraph = 
				new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		graph.addParagraph(rootParagraph);
		
		SimpleParagraph directReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(20L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		ParagraphReference directReference = new ParagraphReference(rootParagraph.getId());
		
		directReferenceParagraph.addReference(directReference);
		
		graph.addParagraph(directReferenceParagraph);
		

		SimpleParagraph undirectReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(30L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		ParagraphReference undirectReference = new ParagraphReference(directReferenceParagraph.getId());
		
		undirectReferenceParagraph.addReference(undirectReference);
		
		graph.addParagraph(undirectReferenceParagraph);
		
		List<Paragraph> found = graph.findRootParagraphs();
		
		assertEquals(1, found.size());
		assertEquals(rootParagraph.getId(), found.get(0).getId());
		
		
	}
	
	
}


