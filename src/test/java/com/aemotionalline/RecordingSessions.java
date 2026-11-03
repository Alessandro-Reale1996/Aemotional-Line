package com.aemotionalline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aemotionalline.application.AccountSessions;
import com.aemotionalline.domain.user.UserId;

/** Records whose sessions were ended, then really ends them (the HTTP tests check that the sessions are gone). */
public class RecordingSessions implements AccountSessions
{
	private final List<UserId> ended = Collections.synchronizedList(new ArrayList<>());
	private final AccountSessions sessions;

	public RecordingSessions(AccountSessions sessions)
	{
		this.sessions = sessions;
	}

	@Override
	public void endAllOf(UserId user)
	{
		ended.add(user);
		sessions.endAllOf(user);
	}

	public boolean wereEnded(UserId user)
	{
		return ended.contains(user);
	}
}
