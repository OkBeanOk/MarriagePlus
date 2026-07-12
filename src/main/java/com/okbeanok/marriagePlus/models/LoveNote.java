package com.okbeanok.marriagePlus.models;

import java.util.UUID;

public record LoveNote(
		UUID senderId,
		String senderName,
		String message,
		long sentAt,
		boolean unread
) {
}