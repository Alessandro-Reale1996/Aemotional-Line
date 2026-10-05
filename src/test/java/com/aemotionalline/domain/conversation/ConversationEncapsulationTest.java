package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

// These mutators bypass the checks of Conversation.sendMessage, so they must stay invisible outside the package.
public class ConversationEncapsulationTest
{
	@Test
	void shouldNotExposeDiscussionMutatorsOutsideThePackage()
	{
		assertNotPublic(Discussion.class, "addMessage");
		assertNotPublic(Discussion.class, "setLastSender");
	}

	@Test
	void shouldNotExposeGraphMutatorsOutsideThePackage()
	{
		assertNotPublic(ConversationGraph.class, "addParagraph");
		assertNotPublic(ConversationGraph.class, "addAllParagraphsInMessage");
	}

	private static void assertNotPublic(Class<?> type, String methodName)
	{
		Method[] methods = Arrays.stream(type.getDeclaredMethods())
				.filter(method -> method.getName().equals(methodName))
				.toArray(Method[]::new);

		assertFalse(methods.length == 0, type.getSimpleName() + "." + methodName + " not found");

		for (Method method : methods)
		{
			assertFalse(Modifier.isPublic(method.getModifiers()), type.getSimpleName() + "." + methodName + " must not be public");
		}
	}
}
