package com.aemotionalline.application;

import com.aemotionalline.application.view.PersonView;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/** How the views name a member of a couple or an invitation: the id and the display name, null once the account is deleted. */
final class People
{
	private People()
	{
	}

	static PersonView of(UserRepository users, UserId id)
	{
		return new PersonView(id, users.findById(id).map(User::getDisplayName).orElse(null));
	}
}
