package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.services.backpacks.BackpackManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class InventoryListener implements Listener {

	private final BackpackManager backpackManager;

	public InventoryListener(BackpackManager backpackManager) {
		this.backpackManager = backpackManager;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		if (!backpackManager.isReadOnly()) {
			return;
		}

		if (!backpackManager.isBackpackInventory(event.getView().getTopInventory())) {
			return;
		}

		if (backpackManager.isAdminBackpackViewer(player.getUniqueId())) {
			return;
		}

		event.setCancelled(true);
	}

	@EventHandler
	public void onInventoryDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		if (!backpackManager.isReadOnly()) {
			return;
		}

		if (!backpackManager.isBackpackInventory(event.getView().getTopInventory())) {
			return;
		}

		if (backpackManager.isAdminBackpackViewer(player.getUniqueId())) {
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

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		backpackManager.saveMatchingBackpack(event.getInventory());

		if (event.getPlayer() instanceof Player player) {
			backpackManager.clearAdminBackpackViewer(player.getUniqueId());
		}
	}
}