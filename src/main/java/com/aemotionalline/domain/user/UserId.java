package com.aemotionalline.domain.user;

import java.util.Objects;

public record UserId(Long value) 
{

    public UserId 
    {
        Objects.requireNonNull(value);
    }
    
}