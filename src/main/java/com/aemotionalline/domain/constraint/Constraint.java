package com.aemotionalline.domain.constraint;

/**
 * A rule about when or how a partner may send a message. It is an interface so new kinds
 * (read-to-reply delay, location, device) can be added without touching {@link ConstraintSet}.
 */
public interface Constraint
{
    boolean isSatisfied(ConstraintContext context);

    String getDescription();

    /** Writing by default: the first constraint, a time of day, restricted sending. */
    default ConstraintScope getScope()
    {
        return ConstraintScope.WRITE;
    }
}
