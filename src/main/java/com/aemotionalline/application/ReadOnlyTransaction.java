package com.aemotionalline.application;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A read-only operation (CLAUDE.md, 2026-11-03): it sees one consistent state of the database across its several queries
 * (REPEATABLE READ: everything as it was at its first read). Write operations run at the pool's READ COMMITTED instead,
 * so that what they read after taking their locks is always the latest committed data.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public @interface ReadOnlyTransaction
{
}
