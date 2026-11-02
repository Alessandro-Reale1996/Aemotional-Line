package com.aemotionalline.infrastructure.persistence.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.OneTimeLink;
import com.aemotionalline.application.OneTimeLinkRepository;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class JdbcOneTimeLinkRepositoryTest
{
	private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

	@Autowired
	private OneTimeLinkRepository links;

	@Autowired
	private UserRepository users;

	private UserId user;

	@BeforeEach
	void saveAUser()
	{
		user = UserId.random();
		users.save(User.pending(user, new EmailAddress(user.value() + "@example.org"), "Anna", Role.PATIENT));
	}

	private static String freshHash()
	{
		return (UserId.random().value().toString() + UserId.random().value()).replace("-", "").substring(0, 64);
	}

	private OneTimeLink link(OneTimeLink.Purpose purpose)
	{
		return new OneTimeLink(freshHash(), user, purpose, NOW, NOW.plus(Duration.ofDays(7)), Optional.empty());
	}

	@Test
	void shouldFindALinkByTheHashOfItsCodeAndRecordItsUse()
	{
		OneTimeLink link = link(OneTimeLink.Purpose.ACTIVATION);
		links.save(link);

		assertEquals(Optional.of(link), links.findByCodeHash(link.codeHash()));

		links.save(link.usedAt(NOW.plusSeconds(60)));

		assertFalse(links.findByCodeHash(link.codeHash()).orElseThrow().isUsableAt(NOW.plusSeconds(61)));
		assertTrue(links.findByCodeHash(freshHash()).isEmpty());
	}

	@Test
	void shouldInvalidateTheUnusedLinksOfAPurpose()
	{
		OneTimeLink reset = link(OneTimeLink.Purpose.PASSWORD_RESET);
		OneTimeLink activation = link(OneTimeLink.Purpose.ACTIVATION);
		links.save(reset);
		links.save(activation);

		links.invalidateUnused(user, OneTimeLink.Purpose.PASSWORD_RESET, NOW.plusSeconds(1));

		assertFalse(links.findByCodeHash(reset.codeHash()).orElseThrow().isUsableAt(NOW.plusSeconds(2)));
		assertTrue(links.findByCodeHash(activation.codeHash()).orElseThrow().isUsableAt(NOW.plusSeconds(2)));
	}
}
