package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.services.lovenotes.LoveNoteManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class LoveNoteInventoryListener implements Listener {

	private final LoveNoteManager loveNoteManager;

	public LoveNoteInventoryListener(LoveNoteManager loveNoteManager) {
		this.loveNoteManager = loveNoteManager;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		loveNoteManager.handleInventoryClick(event);
	}
}