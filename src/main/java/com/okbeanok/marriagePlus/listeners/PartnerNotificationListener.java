package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.services.notifications.NotificationManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.Map;

public class PartnerNotificationListener implements Listener {

	private final NotificationManager notificationManager;

	public PartnerNotificationListener(NotificationManager notificationManager) {
		this.notificationManager = notificationManager;
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		notificationManager.notifyPartner(event.getEntity(), "death");
	}

	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent event) {
		notificationManager.notifyPartner(event.getPlayer(), "world-change", Map.of(
				"%world%", event.getPlayer().getWorld().getName()
		));
	}
}