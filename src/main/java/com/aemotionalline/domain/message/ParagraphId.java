package com.aemotionalline.domain.message;

import java.util.Objects;

public record ParagraphId(Long value) 
{

    public ParagraphId 
    {
        Objects.requireNonNull(value);
    }
}
	
	

