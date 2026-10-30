package com.aemotionalline.infrastructure.persistence.couple;

import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

interface CoupleRowRepository extends ListCrudRepository<CoupleRow, UUID>
{
}
