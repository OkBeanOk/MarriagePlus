package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class AchievementInventoryListener implements Listener {

	private final MarriagePlus plugin;

	public AchievementInventoryListener(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}

		if (!isAchievementsInventory(event)) {
			return;
		}

		event.setCancelled(true);
	}

	@EventHandler
	public void onInventoryDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}

		if (!isAchievementsInventory(event)) {
			return;
		}

		int topInventorySize = event.getView().getTopInventory().getSize();

		for (int rawSlot : event.getRawSlots()) {
			if (rawSlot < topInventorySize) {
				event.setCancelled(true);
				return;
			}
		}
	}

	private boolean isAchievementsInventory(InventoryClickEvent event) {
		String title = event.getView().getTitle();
		String expectedTitle = plugin.langManager().get("achievements.gui-title");

		return title.equals(expectedTitle);
	}

	private boolean isAchievementsInventory(InventoryDragEvent event) {
		String title = event.getView().getTitle();
		String expectedTitle = plugin.langManager().get("achievements.gui-title");

		return title.equals(expectedTitle);
	}
}