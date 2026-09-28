package com.aemotionalline.domain.negotiation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;

/**
 * Record of every negotiation that led to a discussion or a change of agreement. Its size also numbers
 * the discussions of a conversation, so entries must never be removed.
 */
public final class NegotiationArchive 
{

    private final Set<Negotiation> negotiations;

    public NegotiationArchive() 
    {
        this.negotiations = new HashSet<>();
    }

    public void add(Negotiation negotiation) 
    {
        Objects.requireNonNull(negotiation);

        if (!negotiations.add(negotiation)) 
        {
            throw new DomainException(negotiation.getId() + " was already in the archive.");
        }
        
        if (!negotiations.contains(negotiation))
        {
        	throw new DomainException(negotiation.getId() + " wasn't added in the archive.");
        }
    }

    public Negotiation findById(NegotiationId id) 
    {
        return negotiations.stream()
                .filter(negotiation -> negotiation.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> 
                    new DomainException("Negotiation not found.")
                );
    }

    public Set<Negotiation> getNegotiations() 
    {
        return Set.copyOf(negotiations);
    }
}
