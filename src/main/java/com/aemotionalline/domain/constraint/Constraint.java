package com.aemotionalline.domain.constraint;

public interface Constraint
{
    boolean isSatisfied(ConstraintContext context);

    String getDescription();
}
