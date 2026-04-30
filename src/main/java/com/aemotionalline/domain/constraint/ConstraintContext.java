package com.aemotionalline.domain.constraint;

import java.time.LocalTime;

public class ConstraintContext
{
    private final LocalTime time;

    public ConstraintContext(LocalTime time)
    {
        this.time = time;
    }

    public LocalTime time() 
    {
        return time;
    }
}
    

