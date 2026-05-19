package com.aemotionalline.domain.conversation;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.SimpleParagraph;

public class ConversationGraphTest 
{
	@Test
	void shouldThrowExceptionWhenAddingNullParagraph()
	{
		ConversationGraph graph = new ConversationGraph();
		
		SimpleParagraph paragraph = 
				new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "Title", "Subtitle", "Body");
		
		AssertT
		
	}
}
