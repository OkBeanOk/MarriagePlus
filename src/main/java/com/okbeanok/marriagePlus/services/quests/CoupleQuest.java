package com.okbeanok.marriagePlus.services.quests;

public record CoupleQuest(
		String id,
		String name,
		String description,
		String type,
		int amount,
		int rewardXp
) {
}