package com.aemotionalline.infrastructure.persistence.couple;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.Utc;

/** The domain's CoupleRepository on MariaDB. Ids are created by the domain, so insert and update are told apart here. */
@Repository
public class JdbcCoupleRepository implements CoupleRepository
{
	private final CoupleRowRepository rows;
	private final JdbcAggregateTemplate template;

	JdbcCoupleRepository(CoupleRowRepository rows, JdbcAggregateTemplate template)
	{
		this.rows = rows;
		this.template = template;
	}

	@Override
	public Optional<Couple> findById(CoupleId id)
	{
		return rows.findById(id.value()).map(JdbcCoupleRepository::toDomain);
	}

	/** The lock lasts until the end of the caller's transaction, so there must be one. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Couple> findByIdForUpdate(CoupleId id)
	{
		return rows.findLockedById(id.value()).map(JdbcCoupleRepository::toDomain);
	}

	@Override
	public Optional<Couple> findActiveCoupleOf(UserId patient)
	{
		return rows.findActiveOf(patient.value()).map(JdbcCoupleRepository::toDomain);
	}

	/** The locks last until the end of the caller's transaction, so there must be one. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public List<Couple> findActiveCouplesOfTherapistForUpdate(UserId therapist)
	{
		return rows.findActiveOfTherapistLocked(therapist.value()).stream().map(JdbcCoupleRepository::toDomain).toList();
	}

	@Override
	public List<CoupleId> findIdsEndedBefore(Instant instant)
	{
		return rows.findEndedBefore(Utc.toColumn(instant)).stream().map(row -> new CoupleId(row.id())).toList();
	}

	@Override
	@Transactional
	public void save(Couple couple)
	{
		CoupleRow row = new CoupleRow(couple.getId().value(), couple.getPartnerOneId().value(), couple.getPartnerTwoId().value(),
				couple.getTherapistId().value(), Utc.toColumn(couple.getEndedAt().orElse(null)));

		if (rows.existsById(row.id()))
		{
			template.update(row);
		}
		else
		{
			template.insert(row);
		}
	}

	private static Couple toDomain(CoupleRow row)
	{
		return Couple.restore(new CoupleId(row.id()), new UserId(row.partnerOneId()), new UserId(row.partnerTwoId()), new UserId(row.therapistId()),
				Utc.fromColumn(row.endedAt()));
	}
}
