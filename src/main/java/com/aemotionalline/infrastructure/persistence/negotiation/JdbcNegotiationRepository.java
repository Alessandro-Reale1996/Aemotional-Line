package com.aemotionalline.infrastructure.persistence.negotiation;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.NegotiationRepository;
import com.aemotionalline.domain.negotiation.NegotiationSnapshot;
import com.aemotionalline.domain.negotiation.NegotiationStatus;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.negotiation.ProposalStatus;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.Utc;

/**
 * The domain's NegotiationRepository on MariaDB: a header row plus proposal rows. A negotiation has few proposals, so
 * saving rewrites them. Deleting a consumed negotiation removes its header and its unsent draft only: the proposals it
 * sent now belong to the conversation's record.
 */
@Repository
public class JdbcNegotiationRepository implements NegotiationRepository
{
	private static final List<String> FAILED = List.of(NegotiationStatus.REFUSED.name(), NegotiationStatus.ABANDONED.name());

	private final NegotiationRowRepository negotiations;
	private final ProposalRowRepository proposals;
	private final CoupleRepository couples;
	private final JdbcAggregateTemplate template;
	private final Clock clock;

	JdbcNegotiationRepository(NegotiationRowRepository negotiations, ProposalRowRepository proposals, CoupleRepository couples,
			JdbcAggregateTemplate template, Clock clock)
	{
		this.negotiations = negotiations;
		this.proposals = proposals;
		this.couples = couples;
		this.template = template;
		this.clock = clock;
	}

	@Override
	public Optional<Negotiation> findById(NegotiationId id)
	{
		return negotiations.findById(id.value()).map(this::toDomain);
	}

	@Override
	public Optional<Negotiation> findByIdForUpdate(NegotiationId id)
	{
		return negotiations.findLockedById(id.value()).map(this::toDomain);
	}

	@Override
	public List<Negotiation> findFailedStartsOf(CoupleId coupleId)
	{
		return negotiations.findByCoupleIdAndStatusInOrderByStartedAt(coupleId.value(), FAILED).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	@Transactional
	public void save(Negotiation negotiation)
	{
		NegotiationRow row = toRow(negotiation);

		if (negotiations.existsById(row.id()))
		{
			template.update(row);
		}
		else
		{
			template.insert(row);
		}

		proposals.deleteAllOfNegotiation(row.id());

		List<Proposal> sent = negotiation.getProposals();

		for (int position = 0; position < sent.size(); position++)
		{
			template.insert(ProposalRows.toRow(sent.get(position), row.id(), position));
		}

		if (negotiation.getCurrentProposal() != null)
		{
			template.insert(ProposalRows.toRow(negotiation.getCurrentProposal(), row.id(), null));
		}
	}

	@Override
	@Transactional
	public void delete(NegotiationId id)
	{
		proposals.deleteDraftOf(id.value());
		negotiations.deleteById(id.value());
	}

	private NegotiationRow toRow(Negotiation negotiation)
	{
		ProposalRow request = ProposalRows.toRow(negotiation.getRequest(), negotiation.getId().value(), null);

		return new NegotiationRow(negotiation.getId().value(), negotiation.getCouple().getId().value(), negotiation.getNegotiationStatus().name(),
				negotiation.getCurrentResponder().value(), request.id(), request.authorId(), request.kind(), request.text(), request.title(),
				request.subtitle(), Utc.toColumn(negotiation.getStartedAt()), negotiation.getClosedBy().map(UserId::value).orElse(null),
				Utc.toColumn(negotiation.getClosedAt().orElse(null)));
	}

	private Negotiation toDomain(NegotiationRow row)
	{
		Couple couple = couples.findById(new CoupleId(row.coupleId()))
				.orElseThrow(() -> new IllegalStateException("Negotiation " + row.id() + " refers to a missing couple."));

		Proposal request = Proposal.restore(new ProposalId(row.requestId()), new UserId(row.requestAuthor()),
				ProposalRows.content(row.requestKind(), row.requestText(), row.requestTitle(), row.requestSubtitle()), null, null, ProposalStatus.DRAFT);

		List<Proposal> sent = proposals.findByNegotiationIdAndPositionIsNotNullOrderByPosition(row.id()).stream()
				.map(ProposalRows::toDomain)
				.toList();

		Optional<Proposal> draft = proposals.findByNegotiationIdAndPositionIsNull(row.id()).map(ProposalRows::toDomain);

		return Negotiation.restore(new NegotiationSnapshot(new NegotiationId(row.id()), couple, NegotiationStatus.valueOf(row.status()),
				new UserId(row.currentResponder()), draft, request, sent, Utc.fromColumn(row.startedAt()),
				Optional.ofNullable(row.closedBy()).map(UserId::new), Optional.ofNullable(Utc.fromColumn(row.closedAt()))), clock);
	}
}
