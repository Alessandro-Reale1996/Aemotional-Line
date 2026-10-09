package com.aemotionalline.domain.negotiation;

import com.aemotionalline.domain.common.DomainException;

/**
 * Title and subtitle (a short description) of a specific discussion ("discorso specifico"). Neither can be empty;
 * the limits are the same as the title and subtitle of a simple paragraph.
 */
public record DiscussionTitle(String title, String subtitle) implements ProposalContent
{
	public static final int MAX_TITLE_LENGTH = 100;
	public static final int MAX_SUBTITLE_LENGTH = 200;
	
	public DiscussionTitle
	{
		if (title == null || title.isBlank())
		{
			throw new DomainException("The discussion title cannot be blank.");
		}
		
		if (title.length() > MAX_TITLE_LENGTH)
		{
			throw new DomainException("The discussion title can't be longer than " + MAX_TITLE_LENGTH + " characters.");
		}
		
		if (subtitle == null || subtitle.isBlank())
		{
			throw new DomainException("The discussion subtitle cannot be blank.");
		}
		
		if (subtitle.length() > MAX_SUBTITLE_LENGTH)
		{
			throw new DomainException("The discussion subtitle can't be longer than " + MAX_SUBTITLE_LENGTH + " characters.");
		}
	}
}
