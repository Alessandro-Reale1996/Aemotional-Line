package com.aemotionalline.application.view;

import java.time.Instant;

import com.aemotionalline.domain.couple.CoupleId;

/** An ended couple as the administrator may see it (CLAUDE.md, R4): its id and when it ended, nothing about its members. */
public record EndedCoupleView(CoupleId id, Instant endedAt)
{
}
