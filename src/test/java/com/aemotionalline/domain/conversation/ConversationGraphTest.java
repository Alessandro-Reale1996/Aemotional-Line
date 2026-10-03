package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.SimpleParagraph;

public class ConversationGraphTest 
{
	@Test
	void shouldThrowExceptionWhenAddingNullParagraph()
	{
		ConversationGraph graph = new ConversationGraph();
		
		assertThrows(DomainException.class,() -> graph.addParagraph(null));
		
	}
	
	@Test
	void shouldAddParagraphSuccessfully()
	{
		ConversationGraph graph = new ConversationGraph();
		
		graph.addParagraph(new SimpleParagraph(new ParagraphId(10L), "Title", "Subtitle", "Body"));
		
		assertEquals(1, graph.getParagraphs().size());
	}
	
	@Test
	void shouldAddParagraphCorrectly()
	{
		ConversationGraph graph = new ConversationGraph();
		
		graph.addParagraph(new SimpleParagraph(new ParagraphId(10L), "Title", "Subtitle", "Body"));
		
		assertEquals(1, graph.getParagraphs().size());
	}
	
	@Test
	void shouldFindDirectCorrelatedParagraph()
	{
		ConversationGraph graph = new ConversationGraph();
		
		SimpleParagraph rootParagraph = 
				new SimpleParagraph(new ParagraphId(10L), "Title", "Subtitle", "Body");
		
		graph.addParagraph(rootParagraph);
		
		SimpleParagraph referencingParagraph = 
				new SimpleParagraph(new ParagraphId(20L), "Title", "Subtitle", "Body");
		
		
		referencingParagraph.addReference(rootParagraph);
		
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
				new SimpleParagraph(new ParagraphId(10L), "Title", "Subtitle", "Body");
		
		graph.addParagraph(rootParagraph);
		
		SimpleParagraph directReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(20L), "Title", "Subtitle", "Body");
		
		directReferenceParagraph.addReference(rootParagraph);
		
		graph.addParagraph(directReferenceParagraph);
		

		SimpleParagraph undirectReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(30L), "Title", "Subtitle", "Body");
		
		undirectReferenceParagraph.addReference(directReferenceParagraph);
		
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
				new SimpleParagraph(new ParagraphId(10L), "Title", "Subtitle", "Body");
		
		graph.addParagraph(rootParagraph);
		
		SimpleParagraph directReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(20L), "Title", "Subtitle", "Body");
		
		directReferenceParagraph.addReference(rootParagraph);
		
		graph.addParagraph(directReferenceParagraph);
		

		SimpleParagraph undirectReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(30L), "Title", "Subtitle", "Body");
				
		undirectReferenceParagraph.addReference(directReferenceParagraph);
		
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
				new SimpleParagraph(new ParagraphId(10L), "Title", "Subtitle", "Body");
		
		graph.addParagraph(rootParagraph);
		
		SimpleParagraph directReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(20L), "Title", "Subtitle", "Body");
				
		directReferenceParagraph.addReference(rootParagraph);
		
		graph.addParagraph(directReferenceParagraph);
		

		SimpleParagraph undirectReferenceParagraph = 
				new SimpleParagraph(new ParagraphId(30L), "Title", "Subtitle", "Body");
		
		undirectReferenceParagraph.addReference(directReferenceParagraph);
		
		graph.addParagraph(undirectReferenceParagraph);
		
		List<Paragraph> found = graph.findRootParagraphs();
		
		assertEquals(1, found.size());
		assertEquals(rootParagraph.getId(), found.get(0).getId());
		
		
	}
	
	
	// MULTIPLE REFERENCES AND REFERENCE CYCLES:
	
	// A walk that loops forever would hang the whole suite: the timeout turns it into a failure instead.
	private static final Duration NO_INFINITE_LOOP = Duration.ofSeconds(1);
	
	private static SimpleParagraph paragraph(long id)
	{
		return new SimpleParagraph(new ParagraphId(id), "Title", "Subtitle", "Body");
	}
	
	private static List<Long> ids(List<Paragraph> paragraphs)
	{
		return paragraphs.stream().map(p -> p.getId().value()).toList();
	}
	
	@Test
	void shouldKeepReferencesInInsertionOrder()
	{
		SimpleParagraph first = paragraph(10L);
		SimpleParagraph second = paragraph(20L);
		SimpleParagraph third = paragraph(30L);
		SimpleParagraph reply = paragraph(40L);
		
		reply.addReference(third);
		reply.addReference(first);
		reply.addReference(second);
		
		assertEquals(List.of(30L, 10L, 20L), ids(reply.getReferences()));
	}
	
	@Test
	void shouldFindTheSingleRootOfAChain()
	{
		ConversationGraph graph = new ConversationGraph();
		SimpleParagraph root = paragraph(10L);
		SimpleParagraph reply = paragraph(20L);
		SimpleParagraph replyToReply = paragraph(30L);
		
		reply.addReference(root);
		replyToReply.addReference(reply);
		graph.addParagraph(root);
		graph.addParagraph(reply);
		graph.addParagraph(replyToReply);
		
		assertEquals(List.of(10L), ids(graph.findRootsOf(replyToReply)));
	}
	
	@Test
	void shouldFindEveryRootOfAParagraphReplyingToTwoTopics()
	{
		ConversationGraph graph = new ConversationGraph();
		SimpleParagraph firstTopic = paragraph(10L);
		SimpleParagraph secondTopic = paragraph(20L);
		SimpleParagraph reply = paragraph(30L);
		
		// The second topic is referenced first: the roots still come back in sending order.
		reply.addReference(secondTopic);
		reply.addReference(firstTopic);
		graph.addParagraph(firstTopic);
		graph.addParagraph(secondTopic);
		graph.addParagraph(reply);
		
		assertEquals(List.of(10L, 20L), ids(graph.findRootsOf(reply)));
	}
	
	@Test
	void shouldMergeTopicsJoinedByAReplyWhicheverParagraphIsPassed()
	{
		ConversationGraph graph = new ConversationGraph();
		SimpleParagraph firstTopic = paragraph(10L);
		SimpleParagraph secondTopic = paragraph(20L);
		SimpleParagraph reply = paragraph(30L);
		
		reply.addReference(firstTopic);
		reply.addReference(secondTopic);
		graph.addParagraph(firstTopic);
		graph.addParagraph(secondTopic);
		graph.addParagraph(reply);
		
		List<Long> merged = List.of(10L, 20L, 30L);
		
		assertEquals(merged, ids(graph.findConversationBranchContaining(reply)));
		assertEquals(merged, ids(graph.findConversationBranchContaining(firstTopic)));
		assertEquals(merged, ids(graph.findConversationBranchContaining(secondTopic)));
	}
	
	@Test
	void shouldNotRepeatAParagraphReachableThroughTwoPaths()
	{
		ConversationGraph graph = new ConversationGraph();
		SimpleParagraph root = paragraph(10L);
		SimpleParagraph left = paragraph(20L);
		SimpleParagraph right = paragraph(30L);
		SimpleParagraph join = paragraph(40L);
		
		left.addReference(root);
		right.addReference(root);
		join.addReference(left);
		join.addReference(right);
		graph.addParagraph(root);
		graph.addParagraph(left);
		graph.addParagraph(right);
		graph.addParagraph(join);
		
		assertEquals(List.of(10L, 20L, 30L, 40L), ids(graph.findConversationBranchContaining(root)));
	}
	
	@Test
	void shouldNotLoopForeverOnACycleWithNoRoot()
	{
		ConversationGraph graph = new ConversationGraph();
		SimpleParagraph x = paragraph(10L);
		SimpleParagraph y = paragraph(20L);
		
		x.addReference(y);
		y.addReference(x);
		graph.addParagraph(x);
		graph.addParagraph(y);
		
		assertTimeoutPreemptively(NO_INFINITE_LOOP, () -> 
		{
			assertThrows(DomainException.class, () -> graph.findRootsOf(y));
			assertEquals(List.of(10L, 20L), ids(graph.findConversationBranchContaining(y)));
		});
	}
	
	@Test
	void shouldNotOverflowOnACycleBelowARoot()
	{
		ConversationGraph graph = new ConversationGraph();
		SimpleParagraph root = paragraph(10L);
		SimpleParagraph x = paragraph(20L);
		SimpleParagraph y = paragraph(30L);
		
		x.addReference(root);
		y.addReference(x);
		x.addReference(y);
		graph.addParagraph(root);
		graph.addParagraph(x);
		graph.addParagraph(y);
		
		assertTimeoutPreemptively(NO_INFINITE_LOOP, () -> 
		{
			assertEquals(List.of(10L, 20L, 30L), ids(graph.findConversationBranchContaining(root)));
			assertEquals(List.of(10L), ids(graph.findRootsOf(y)));
		});
	}
	
	@Test
	void shouldRejectAParagraphThatIsNotInTheGraph()
	{
		ConversationGraph graph = new ConversationGraph();
		graph.addParagraph(paragraph(10L));
		
		SimpleParagraph outsider = paragraph(99L);
		
		assertThrows(DomainException.class, () -> graph.findRootsOf(outsider));
		assertThrows(DomainException.class, () -> graph.findConversationBranchContaining(outsider));
		assertThrows(DomainException.class, () -> graph.findRepliesTo(null));
	}
	
}
