package com.aemotionalline.infrastructure.persistence.couple;

import java.util.Optional;

import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserId;

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

	@Override
	@Transactional
	public void save(Couple couple)
	{
		CoupleRow row = new CoupleRow(couple.getId().value(), couple.getPartnerOneId().value(), couple.getPartnerTwoId().value(),
				couple.getTherapistId().value());

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
		return new Couple(new CoupleId(row.id()), new UserId(row.partnerOneId()), new UserId(row.partnerTwoId()), new UserId(row.therapistId()));
	}
}
