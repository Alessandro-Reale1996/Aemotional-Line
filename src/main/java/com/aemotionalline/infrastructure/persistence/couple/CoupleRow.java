package com.aemotionalline.infrastructure.persistence.couple;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of the couple table. Persistence model only: the domain's Couple is rebuilt from it. */
@Table("couple")
public record CoupleRow(@Id UUID id, UUID partnerOneId, UUID partnerTwoId, UUID therapistId)
{
}
