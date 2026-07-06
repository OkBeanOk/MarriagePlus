package com.okbeanok.marriagePlus.models;

import java.util.UUID;

public record PartnerMail(
		UUID senderId,
		String senderName,
		String message,
		long sentAt
) {
}