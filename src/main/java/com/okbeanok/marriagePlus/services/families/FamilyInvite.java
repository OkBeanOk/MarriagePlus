package com.okbeanok.marriagePlus.services.families;

import java.util.UUID;

public record FamilyInvite(
		String familyId,
		UUID inviter,
		UUID target,
		long expiresAt
) {

	public boolean expired() {
		return System.currentTimeMillis() > expiresAt;
	}
}