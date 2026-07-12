package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class PartnerPvpListener implements Listener {

	private final MarriageManager marriageManager;

	public PartnerPvpListener(MarriageManager marriageManager) {
		this.marriageManager = marriageManager;
	}

	@EventHandler
	public void onPartnerPvp(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}

		if (!(event.getDamager() instanceof Player attacker)) {
			return;
		}

		UUID attackerPartner = marriageManager.getPartnerId(attacker.getUniqueId());

		if (attackerPartner == null || !attackerPartner.equals(victim.getUniqueId())) {
			return;
		}

		if (!marriageManager.pvpEnabledCouples().contains(marriageManager.coupleKey(attacker.getUniqueId(), victim.getUniqueId()))) {
			event.setCancelled(true);
			attacker.sendMessage(color("&cPvP with your partner is disabled. Use /marry pvpon to enable it."));
		}
	}
}