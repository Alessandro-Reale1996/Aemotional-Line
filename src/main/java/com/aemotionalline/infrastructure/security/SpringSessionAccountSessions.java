package com.aemotionalline.infrastructure.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

import com.aemotionalline.application.AccountSessions;
import com.aemotionalline.domain.user.UserId;

/**
 * Ends every login session of an account (password reset, disabling), finding them in Spring Session's table by
 * principal name, which is the account's id (see SessionUser). Outside the web application (the first-administrator
 * command) there is no session repository and nothing to end.
 */
@Component
public class SpringSessionAccountSessions implements AccountSessions
{
	private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessions;

	SpringSessionAccountSessions(ObjectProvider<FindByIndexNameSessionRepository<?>> sessions)
	{
		this.sessions = sessions;
	}

	@Override
	public void endAllOf(UserId user)
	{
		sessions.ifAvailable(repository -> repository.findByPrincipalName(SessionUser.nameOf(user)).keySet().forEach(repository::deleteById));
	}
}
