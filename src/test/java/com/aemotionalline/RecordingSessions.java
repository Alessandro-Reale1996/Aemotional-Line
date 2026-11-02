package com.aemotionalline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aemotionalline.application.AccountSessions;
import com.aemotionalline.domain.user.UserId;

/** Test double: records whose sessions were ended. */
public class RecordingSessions implements AccountSessions
{
	private final List<UserId> ended = Collections.synchronizedList(new ArrayList<>());

	@Override
	public void endAllOf(UserId user)
	{
		ended.add(user);
	}

	public boolean wereEnded(UserId user)
	{
		return ended.contains(user);
	}
}
