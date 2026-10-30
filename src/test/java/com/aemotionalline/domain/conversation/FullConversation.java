package com.aemotionalline.domain.conversation;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.constraint.ReplyDelayConstraint;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.conversation.OutcomeDeclaration.Outcome;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.PointedParagraph;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

/**
 * Test fixture: a conversation holding every kind of state, built through the normal operations with fresh ids (so it can
 * be saved more than once in a shared database): earlier failed starts, a changed agreement with a refused intermediate
 * proposal, a failed negotiation, applied/rejected/pending constraint requests of every constraint type, questions answered
 * and skipped, a self-citation, recorded and missing reads, a branch closed by agreement with both declarations, a branch
 * waiting for the therapist. The last message of the main discussion is A's and B hasn't read it.
 */
public class FullConversation
{
	public static final ConstraintContext NOON = new ConstraintContext(LocalTime.NOON);

	public final Couple couple;
	public final UserId a;
	public final UserId b;
	public final UserId therapist;
	public final MutableClock clock;
	public final Conversation conversation;

	public final DiscussionId main;
	public final DiscussionId closedBranch;
	public final DiscussionId pendingBranch;

	public final QuestionParagraph q;
	public final SimpleParagraph p;
	public final SimpleParagraph r;
	public final SimpleParagraph s;
	public final SimpleParagraph t;
	public final MessageId lastMainMessage;
	public final NegotiationId closedBranchNegotiation;
	public final ConstraintChangeRequestId pendingConstraints;

	public FullConversation(Couple couple, ConversationId id, MutableClock clock)
	{
		this.couple = couple;
		this.a = couple.getPartnerOneId();
		this.b = couple.getPartnerTwoId();
		this.therapist = couple.getTherapistId();
		this.clock = clock;
		this.main = new DiscussionId(id, 0);
		this.closedBranch = new DiscussionId(id, 1);
		this.pendingBranch = new DiscussionId(id, 2);

		Negotiation refusedStart = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), a, "TOO EARLY"), clock);
		refusedStart.refuseNegotiation(b);
		clock.advance(Duration.ofDays(1));

		Negotiation start = agreementNegotiation("FIRST AGREEMENT");
		start.acceptProposal(b);
		conversation = Conversation.start(id, couple, start, clock, List.of(refusedStart));

		ConstraintChangeRequestId applied = conversation.proposeConstraints(therapist, List.of(
				new ConstraintAssignment(a, new TimeConstraint(LocalTime.of(9, 0))),
				new ConstraintAssignment(b, new LocationConstraint(new GeoPosition(45.4642, 9.19), 250, LocationConstraint.Direction.BEYOND,
						com.aemotionalline.domain.constraint.ConstraintScope.WRITE))));
		conversation.approveConstraints(applied, a);
		conversation.approveConstraints(applied, b);
		ConstraintChangeRequestId rejected = conversation.proposeConstraints(therapist, List.of(new ConstraintAssignment(b, new ReplyDelayConstraint(Duration.ofHours(2)))));
		conversation.rejectConstraints(rejected, b);

		q = new QuestionParagraph(ParagraphId.random(), "Why?", "Tell me.");
		p = simple();
		QuestionParagraph skipped = new QuestionParagraph(ParagraphId.random(), "And then?", "Go on.");
		send(main, a, q, p, skipped);
		r = simple(p);
		// B writes away from home (the applied location constraint), so B's context carries a position.
		send(main, b, new ConstraintContext(LocalTime.NOON, new GeoPosition(45.48, 9.19)), new PointedParagraph(ParagraphId.random(), q, "Because."), r);
		s = simple(r);
		s.addSelfCitation(p);
		t = simple();
		lastMainMessage = send(main, a, s, t).getId();

		Negotiation change = agreementNegotiation("SECOND AGREEMENT");
		change.refuseProposal(b, ProposalId.random());
		change.editCurrentProposal(b, new AgreementText("B'S AGREEMENT"));
		change.sendProposal(b, "BETTER");
		change.acceptProposal(a);
		clock.advance(Duration.ofMinutes(1));
		conversation.modifyAgreement(change);

		Negotiation abandoned = Negotiation.start(NegotiationId.random(), couple, new DiscussionProposal(ProposalId.random(), b, "NEVER", "MIND"), clock);
		abandoned.abandon(b);
		conversation.recordFailedNegotiation(abandoned);

		closedBranchNegotiation = NegotiationId.random();
		conversation.openDiscussion(discussionNegotiation(closedBranchNegotiation), s.getId());
		SimpleParagraph x = simple(s);
		send(closedBranch, b, new ConstraintContext(LocalTime.NOON, new GeoPosition(45.48, 9.19)), x);
		send(closedBranch, a, simple(x));
		clock.advance(Duration.ofMinutes(1));
		conversation.requestClosing(closedBranch, a);
		conversation.acceptClosing(closedBranch, b);
		conversation.declareOutcome(closedBranch, a, Outcome.RESOLVED, "Agreed.");
		conversation.declareOutcome(closedBranch, b, Outcome.STALLED, "Not really.");

		conversation.openDiscussion(discussionNegotiation(NegotiationId.random()));
		send(pendingBranch, a, simple());
		conversation.requestClosing(pendingBranch, b);
		conversation.refuseClosing(pendingBranch, a);
		conversation.requestTherapistClosing(pendingBranch, b, "A refuses every time.");

		pendingConstraints = conversation.proposeConstraints(therapist, List.of(new ConstraintAssignment(b, new TimeConstraint(LocalTime.of(8, 0)))));
		conversation.approveConstraints(pendingConstraints, a);
	}

	private Negotiation agreementNegotiation(String text)
	{
		Negotiation negotiation = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), a, text), clock);
		negotiation.acceptNegotiation(b);
		negotiation.sendProposal(a, "WHY " + text);

		return negotiation;
	}

	public Negotiation discussionNegotiation(NegotiationId id)
	{
		Negotiation negotiation = Negotiation.start(id, couple, new DiscussionProposal(ProposalId.random(), a, "TITLE", "SUBTITLE"), clock);
		negotiation.acceptNegotiation(b);
		negotiation.sendProposal(a, "WHY");
		negotiation.acceptProposal(b);

		return negotiation;
	}

	private Message send(DiscussionId discussion, UserId sender, Paragraph... paragraphs)
	{
		return send(discussion, sender, NOON, paragraphs);
	}

	private Message send(DiscussionId discussion, UserId sender, ConstraintContext context, Paragraph... paragraphs)
	{
		Message message = new Message(MessageId.random(), sender);

		for (Paragraph paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}

		clock.advance(Duration.ofMinutes(1));
		Reading.send(conversation, message, discussion, context);

		return message;
	}

	public static SimpleParagraph simple(Paragraph... answered)
	{
		SimpleParagraph paragraph = new SimpleParagraph(ParagraphId.random(), "Title", "Subtitle", "Body àèìòù");

		for (Paragraph reference : answered)
		{
			paragraph.addReference(reference);
		}

		return paragraph;
	}
}
