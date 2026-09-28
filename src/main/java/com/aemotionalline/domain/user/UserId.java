package com.aemotionalline.domain.user;

import java.util.Objects;

/** A dedicated type instead of a bare Long so a user's id cannot be mixed up with the other ids of the domain. */
public record UserId(Long value) 
{

    public UserId 
    {
        Objects.requireNonNull(value);
    }
    
}