package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.quests.QuestManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class QuestListener implements Listener {

	private final MarriageManager marriageManager;
	private final QuestManager questManager;

	public QuestListener(MarriageManager marriageManager, QuestManager questManager) {
		this.marriageManager = marriageManager;
		this.questManager = questManager;
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();

		if (!isNearPartner(player)) {
			return;
		}

		questManager.addProgress(player, "MINE_BLOCKS_NEAR_PARTNER", 1);
	}

	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		LivingEntity entity = event.getEntity();
		Player player = entity.getKiller();

		if (player == null) {
			return;
		}

		if (!isNearPartner(player)) {
			return;
		}

		questManager.addProgress(player, "KILL_MOBS_NEAR_PARTNER", 1);
	}

	private boolean isNearPartner(Player player) {
		Player partner = marriageManager.getOnlinePartnerSilent(player);

		if (partner == null) {
			return false;
		}

		if (!player.getWorld().equals(partner.getWorld())) {
			return false;
		}

		return player.getLocation().distanceSquared(partner.getLocation()) <= 32.0D * 32.0D;
	}
}