package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.services.backpacks.BackpackManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class InventoryListener implements Listener {

	private final BackpackManager backpackManager;

	public InventoryListener(BackpackManager backpackManager) {
		this.backpackManager = backpackManager;
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		backpackManager.saveMatchingBackpack(event.getInventory());
	}
}