package com.aemotionalline.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.NegotiationEffect;
import com.aemotionalline.application.view.NegotiationView;
import com.aemotionalline.application.view.Views;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.NegotiationRecord.Purpose;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.NegotiationRepository;
import com.aemotionalline.domain.negotiation.ProposalContent;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

/**
 * The negotiations ("stipula"): starting one for a purpose, the exchange of proposals, and its end. A negotiation that
 * ends with an agreement takes effect at once (starts the conversation, changes the agreement or opens the discussion)
 * and is deleted; one that fails is recorded in the conversation's failed section and deleted, unless it tried to start
 * the conversation, which keeps it at couple level until the conversation starts. Locks are taken in the order couple,
 * conversation, negotiation.
 */
@Service
@Transactional
public class NegotiationService
{
	private final CoupleRepository couples;
	private final NegotiationRepository negotiations;
	private final ConversationRepository conversations;
	private final NegotiationPurposeRepository purposes;
	private final Clock clock;

	NegotiationService(CoupleRepository couples, NegotiationRepository negotiations, ConversationRepository conversations,
			NegotiationPurposeRepository purposes, Clock clock)
	{
		this.couples = couples;
		this.negotiations = negotiations;
		this.conversations = conversations;
		this.purposes = purposes;
		this.clock = clock;
	}

	// STARTING

	/**
	 * A partner asks to negotiate the agreement: to start the couple's conversation if there is none yet, otherwise to change
	 * the agreement. Only one agreement negotiation can be in progress at a time.
	 */
	public NegotiationId startAgreementNegotiation(UserId requester, CoupleId coupleId, String text)
	{
		Couple couple = couples.findByIdForUpdate(coupleId).orElseThrow(() -> new NotFoundException("Couple not found."));
		Access.ensureMember(couple, requester, "Couple");

		boolean agreementInProgress = purposes.findInProgressOf(coupleId).stream()
				.anyMatch(purpose -> purpose.purpose() != Purpose.OPENED_DISCUSSION);

		if (agreementInProgress)
		{
			throw new DomainException("An agreement negotiation is already in progress.");
		}

		Purpose purpose = conversations.findIdByCouple(coupleId).isPresent() ? Purpose.MODIFIED_AGREEMENT : Purpose.STARTED_CONVERSATION;

		Negotiation negotiation = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), requester, text), clock);
		negotiations.save(negotiation);
		purposes.save(new NegotiationPurpose(negotiation.getId(), purpose, Optional.empty()));

		return negotiation.getId();
	}

	/**
	 * A partner asks to open a specific discussion, with the title and subtitle to negotiate and, optionally, the paragraph
	 * it will expand. The origin is checked now (the requester can see it, it isn't expanded yet, no other request is on
	 * it), so the partners don't negotiate for days a discussion that can't be opened.
	 */
	public NegotiationId startDiscussionNegotiation(UserId requester, ConversationId conversationId, String title, String subtitle,
			Optional<ParagraphId> origin)
	{
		Conversation conversation = conversations.findById(conversationId).orElseThrow(() -> new NotFoundException("Conversation not found."));
		Access.ensureMember(conversation.getCouple(), requester, "Conversation");

		Couple couple = couples.findByIdForUpdate(conversation.getCouple().getId()).orElseThrow();

		if (origin.isPresent())
		{
			ParagraphId paragraph = origin.get();
			Paragraphs.visibleTo(requester, conversation, paragraph);

			if (conversation.getDiscussions().stream().anyMatch(discussion -> discussion.getOriginParagraphId().equals(origin)))
			{
				throw new DomainException("This paragraph is already expanded by a discussion.");
			}

			if (purposes.findInProgressOf(couple.getId()).stream().anyMatch(purpose -> purpose.origin().equals(origin)))
			{
				throw new DomainException("A request to expand this paragraph is already in progress.");
			}
		}

		Negotiation negotiation = Negotiation.start(NegotiationId.random(), couple, new DiscussionProposal(ProposalId.random(), requester, title, subtitle), clock);
		negotiations.save(negotiation);
		purposes.save(new NegotiationPurpose(negotiation.getId(), Purpose.OPENED_DISCUSSION, origin));

		return negotiation.getId();
	}

	// THE EXCHANGE (the negotiation alone changes)

	public void acceptRequest(UserId user, NegotiationId id)
	{
		Negotiation negotiation = locked(user, id);
		negotiation.acceptNegotiation(user);
		negotiations.save(negotiation);
	}

	public void editProposal(UserId user, NegotiationId id, ProposalContent content)
	{
		Negotiation negotiation = locked(user, id);
		negotiation.editCurrentProposal(user, content);
		negotiations.save(negotiation);
	}

	public void sendProposal(UserId user, NegotiationId id, String justification)
	{
		Negotiation negotiation = locked(user, id);
		negotiation.sendProposal(user, justification);
		negotiations.save(negotiation);
	}

	/** Refusing a proposal starts the refuser's counter-proposal: the id of the new draft is returned. */
	public ProposalId refuseProposal(UserId user, NegotiationId id)
	{
		Negotiation negotiation = locked(user, id);
		ProposalId draft = ProposalId.random();
		negotiation.refuseProposal(user, draft);
		negotiations.save(negotiation);

		return draft;
	}

	// THE END

	/** Accepting the last proposal ends the negotiation with an agreement, which takes effect at once. */
	public NegotiationEffect acceptProposal(UserId user, NegotiationId id)
	{
		NegotiationPurpose purpose = purposeOf(user, id);

		if (purpose.purpose() == Purpose.STARTED_CONVERSATION)
		{
			Negotiation peek = negotiations.findById(id).orElseThrow();
			Couple couple = couples.findByIdForUpdate(peek.getCouple().getId()).orElseThrow();
			Negotiation negotiation = negotiations.findByIdForUpdate(id).orElseThrow();
			negotiation.acceptProposal(user);

			List<Negotiation> failedStarts = negotiations.findFailedStartsOf(couple.getId());
			Conversation conversation = Conversation.start(ConversationId.random(), couple, negotiation, clock, failedStarts);
			conversations.save(conversation);
			negotiations.delete(id);
			failedStarts.forEach(failed -> negotiations.delete(failed.getId()));

			return new NegotiationEffect(conversation.getId(), Optional.empty());
		}

		Conversation conversation = lockedConversationOf(id);
		Negotiation negotiation = negotiations.findByIdForUpdate(id).orElseThrow();
		negotiation.acceptProposal(user);

		Optional<DiscussionId> opened = Optional.empty();

		if (purpose.purpose() == Purpose.MODIFIED_AGREEMENT)
		{
			conversation.modifyAgreement(negotiation);
		}
		else
		{
			if (purpose.origin().isPresent())
			{
				conversation.openDiscussion(negotiation, purpose.origin().get());
			}
			else
			{
				conversation.openDiscussion(negotiation);
			}

			opened = Optional.of(conversation.getDiscussions().getLast().getId());
		}

		negotiations.delete(id);
		conversations.save(conversation);

		return new NegotiationEffect(conversation.getId(), opened);
	}

	public void refuseRequest(UserId user, NegotiationId id)
	{
		endWithoutAgreement(user, id, negotiation -> negotiation.refuseNegotiation(user));
	}

	public void abandon(UserId user, NegotiationId id)
	{
		endWithoutAgreement(user, id, negotiation -> negotiation.abandon(user));
	}

	// A failed attempt to start the conversation stays at couple level; any other is recorded in the conversation and deleted.
	private void endWithoutAgreement(UserId user, NegotiationId id, Consumer<Negotiation> ending)
	{
		NegotiationPurpose purpose = purposeOf(user, id);

		if (purpose.purpose() == Purpose.STARTED_CONVERSATION)
		{
			Negotiation negotiation = negotiations.findByIdForUpdate(id).orElseThrow();
			ending.accept(negotiation);
			negotiations.save(negotiation);
			return;
		}

		Conversation conversation = lockedConversationOf(id);
		Negotiation negotiation = negotiations.findByIdForUpdate(id).orElseThrow();
		ending.accept(negotiation);
		conversation.recordFailedNegotiation(negotiation);
		negotiations.delete(id);
		conversations.save(conversation);
	}

	// READING

	@Transactional(readOnly = true)
	public NegotiationView view(UserId viewer, NegotiationId id)
	{
		Negotiation negotiation = negotiations.findById(id).orElseThrow(() -> new NotFoundException("Negotiation not found."));
		Access.ensureMember(negotiation.getCouple(), viewer, "Negotiation");
		NegotiationPurpose purpose = purposes.findByNegotiation(id).orElseThrow(() -> new NotFoundException("Negotiation not found."));

		return Views.negotiation(negotiation, purpose.purpose(), purpose.origin(), viewer);
	}

	/** The couple's negotiations in progress, oldest first. */
	@Transactional(readOnly = true)
	public List<NegotiationView> inProgress(UserId viewer, CoupleId coupleId)
	{
		Couple couple = couples.findById(coupleId).orElseThrow(() -> new NotFoundException("Couple not found."));
		Access.ensureMember(couple, viewer, "Couple");

		return purposes.findInProgressOf(coupleId).stream()
				.map(purpose -> Views.negotiation(negotiations.findById(purpose.negotiationId()).orElseThrow(), purpose.purpose(), purpose.origin(), viewer))
				.toList();
	}

	/** The couple's failed attempts to start its conversation, kept at couple level until the conversation starts. */
	@Transactional(readOnly = true)
	public List<NegotiationView> failedStarts(UserId viewer, CoupleId coupleId)
	{
		Couple couple = couples.findById(coupleId).orElseThrow(() -> new NotFoundException("Couple not found."));
		Access.ensureMember(couple, viewer, "Couple");

		return negotiations.findFailedStartsOf(coupleId).stream()
				.map(negotiation -> Views.negotiation(negotiation, Purpose.STARTED_CONVERSATION, Optional.empty(), viewer))
				.toList();
	}

	// HELPERS

	private Negotiation locked(UserId user, NegotiationId id)
	{
		Negotiation negotiation = negotiations.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Negotiation not found."));
		Access.ensureMember(negotiation.getCouple(), user, "Negotiation");

		return negotiation;
	}

	private NegotiationPurpose purposeOf(UserId user, NegotiationId id)
	{
		Negotiation negotiation = negotiations.findById(id).orElseThrow(() -> new NotFoundException("Negotiation not found."));
		Access.ensureMember(negotiation.getCouple(), user, "Negotiation");

		return purposes.findByNegotiation(id).orElseThrow(() -> new NotFoundException("Negotiation not found."));
	}

	private Conversation lockedConversationOf(NegotiationId id)
	{
		CoupleId couple = negotiations.findById(id).orElseThrow().getCouple().getId();
		ConversationId conversation = conversations.findIdByCouple(couple).orElseThrow(() -> new NotFoundException("Conversation not found."));

		return conversations.findByIdForUpdate(conversation).orElseThrow();
	}
}
