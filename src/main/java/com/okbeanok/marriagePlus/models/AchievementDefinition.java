package com.okbeanok.marriagePlus.models;

public record AchievementDefinition(
		String id,
		String name,
		String description,
		String trigger,
		String action,
		int days
) {
}