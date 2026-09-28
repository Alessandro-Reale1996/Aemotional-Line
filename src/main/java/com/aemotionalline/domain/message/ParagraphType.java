package com.aemotionalline.domain.message;

/** Lets code branch on a paragraph's role (for example in {@code Analyzer}) without {@code instanceof} checks. */
public enum ParagraphType 
{
	SIMPLE,
    QUESTION,
    POINTED
}
