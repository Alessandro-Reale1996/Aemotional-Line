package com.aemotionalline.domain.message;

import java.util.Objects;

public record ParagraphReference (ParagraphId referencedParagraphId)
{

	 public ParagraphReference
	    {
	        Objects.requireNonNull(referencedParagraphId);
	    }
	
	 
}
