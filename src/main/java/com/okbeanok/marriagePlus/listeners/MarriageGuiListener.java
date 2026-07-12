package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.gui.MarriageGuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class MarriageGuiListener implements Listener {

	private final MarriagePlus plugin;
	private final MarriageGuiManager guiManager;

	public MarriageGuiListener(MarriagePlus plugin, MarriageGuiManager guiManager) {
		this.plugin = plugin;
		this.guiManager = guiManager;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		String expectedTitle = color(plugin.configs().gui().getString("main-menu.title", "&d❤ Marriage Menu"));

		if (guiManager.isHomesMenuTitle(event.getView().getTitle())) {
			handleHomesMenuClick(event);
			return;
		}

		if (!event.getView().getTitle().equals(expectedTitle)) {
			return;
		}

		event.setCancelled(true);

		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		String action = guiManager.getActionBySlot(event.getRawSlot()).toLowerCase(Locale.ROOT);

		if (action.isBlank()) {
			return;
		}

		switch (action) {
			case "profile" -> plugin.profileManager().profileCommand(player, new String[]{"profile"});
			case "partner" -> plugin.profileManager().profileCommand(player, new String[]{"profile"});
			case "teleport" -> player.performCommand("marry tp");
			case "homes" -> plugin.marriageGuiManager().openHomesMenu(player);
			case "backpack" -> player.performCommand("marry backpack");
			case "mail" -> player.performCommand("marry mail read");
			case "love-notes" -> player.performCommand("marry note list");
			case "quests" -> player.performCommand("marry quests");
			case "anniversary" -> player.performCommand("marry anniversary");
			case "achievements" -> player.performCommand("marry achievements");
			case "ring" -> player.performCommand("marry ring replace");
			case "close" -> player.closeInventory();
			default -> plugin.langManager().send(player, "gui.unknown-action");
		}
	}

	private void handleHomesMenuClick(InventoryClickEvent event) {
		event.setCancelled(true);

		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		int slot = event.getRawSlot();

		if (slot == 45) {
			guiManager.openMainMenu(player);
			return;
		}

		if (slot == 49) {
			player.closeInventory();
			return;
		}

		ItemStack clicked = event.getCurrentItem();

		if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta() == null) {
			return;
		}

		String displayName = clicked.getItemMeta().getDisplayName();

		if (displayName == null || !displayName.contains("⌂")) {
			return;
		}

		String homeName = displayName
				.replace(color("&d⌂ &f"), "")
				.replace("§d⌂ §f", "")
				.trim();

		if (homeName.isBlank()) {
			return;
		}

		player.closeInventory();

		if (event.isShiftClick()) {
			player.performCommand("marry delhome " + homeName);
			return;
		}

		player.performCommand("marry home " + homeName);
	}
}