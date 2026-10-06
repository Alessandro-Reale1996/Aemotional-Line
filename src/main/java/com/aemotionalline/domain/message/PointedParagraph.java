package com.aemotionalline.domain.message;

import com.aemotionalline.domain.common.DomainException;

/**
 * A direct answer to a question ("paragrafo puntuale"). It answers exactly one {@link QuestionParagraph},
 * which is given when it is created: the type makes it impossible to build a pointed paragraph without a question,
 * or one that answers anything else (a simple paragraph can never receive a pointed answer).
 * Its title is not written by the user: the spec wants it standardized, so it is "In risposta a «question title»".
 * Whether the question belongs to the message being answered is checked when the message is sent.
 */
public class PointedParagraph extends Paragraph
{
	private static final int MAX_BODY_LENGTH = 500;
	
	public PointedParagraph
	(ParagraphId id, QuestionParagraph question, String body)
	{
		super(id, ParagraphType.POINTED, standardTitle(question), null, body);
		
		if(body != null && body.length() > MAX_BODY_LENGTH)
        {
            throw new DomainException(
                "Paragraph body cannot exceed "
                + MAX_BODY_LENGTH
                + " characters");
        }
		
		// The overridden addReference below rejects every reference, so the base one is called directly.
		super.addReference(question);
	}
	
	// Static because it runs before the constructor body: the title must exist when Paragraph is built.
	private static String standardTitle(QuestionParagraph question)
	{
		if (question == null)
		{
			throw new DomainException("A pointed paragraph must answer a question.");
		}
		
		return "In risposta a \u00ab" + question.getTitle() + "\u00bb";
	}
	
	public QuestionParagraph getQuestion()
	{
		return (QuestionParagraph) getReferences().get(0);
	}

	// The question is fixed at creation: a pointed paragraph never gets a second reference.
	@Override
	public void addReference(Paragraph reference)
	{
		validateReference(reference);
		
		throw new DomainException("A pointed paragraph answers exactly one question, given when it is created.");
	}
}
