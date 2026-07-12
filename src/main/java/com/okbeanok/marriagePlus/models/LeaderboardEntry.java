package com.okbeanok.marriagePlus.models;

import java.util.UUID;

public record LeaderboardEntry(
		UUID firstId,
		UUID secondId,
		String firstName,
		String secondName,
		long value,
		String displayValue
) {
}