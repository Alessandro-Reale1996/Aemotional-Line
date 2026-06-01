package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphReference;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.SimpleParagraph;

public class DiscussionAnalyzerTest
{

	@Test
	void shouldReturnTheCorrectInformationsWhenAnalyze()
	{
		SimpleParagraph root = 
				new SimpleParagraph
						(
							new ParagraphId(10L), 
							ParagraphType.SIMPLE, 
							"root", 
							"the root paragraph", 
							"Root paragraph for testing DiscussionAnalyzer"
						);
		
		SimpleParagraph reply = 
				new SimpleParagraph
						(
							new ParagraphId(20L), 
							ParagraphType.SIMPLE, 
							"Reply", 
							"The reply paragraph", 
							"Reply paragraph for testing DiscussionAnalyzer"
						);
		
		ParagraphReference reference = new ParagraphReference(root.getId());
		
		reply.addReference(reference);
		
		ConversationGraph graph = new ConversationGraph();
		
		graph.addParagraph(root);
		graph.addParagraph(reply);
		
		DiscussionAnalyzer analyzer = new DiscussionAnalyzer(graph);
		
		assertEquals
				(
					new DiscussionAnalysis(root,graph.getParagraphs(), DiscussionStatus.ACTIVE),
					analyzer.analyze(reply)
				);
		
		
	}
}
