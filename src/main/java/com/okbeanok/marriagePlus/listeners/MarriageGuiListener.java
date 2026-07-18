package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.PartnerMail;
import com.okbeanok.marriagePlus.services.gui.MarriageGuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class MarriageGuiListener implements Listener {

	private final MarriagePlus plugin;
	private final MarriageGuiManager guiManager;

	public MarriageGuiListener(MarriagePlus plugin, MarriageGuiManager guiManager) {
		this.plugin = plugin;
		this.guiManager = guiManager;
	}

	private void handleMailMenuClick(InventoryClickEvent event) {
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

		int mailIndex = mailIndexFromSlot(slot);

		if (mailIndex < 0) {
			return;
		}

		List<PartnerMail> inbox = plugin.mailManager().inboxes().get(player.getUniqueId());

		if (inbox == null || mailIndex >= inbox.size()) {
			return;
		}

		PartnerMail mail = inbox.get(mailIndex);

		if (event.isShiftClick()) {
			inbox.remove(mailIndex);

			if (inbox.isEmpty()) {
				plugin.mailManager().inboxes().remove(player.getUniqueId());
			}

			plugin.dataManager().saveData();
			plugin.langManager().send(player, "mail.deleted");
			guiManager.openMailMenu(player);
			return;
		}

		player.closeInventory();
		plugin.langManager().send(player, "mail.message-header", Map.of(
				"%number%", String.valueOf(mailIndex + 1)
		));
		plugin.langManager().send(player, "mail.message-from", Map.of(
				"%sender%", mail.senderName()
		));
		plugin.langManager().send(player, "mail.message-body", Map.of(
				"%message%", mail.message()
		));
	}

	private int mailIndexFromSlot(int slot) {
		int[] slots = {
				10, 11, 12, 13, 14, 15, 16,
				19, 20, 21, 22, 23, 24, 25,
				28, 29, 30, 31, 32, 33, 34
		};

		for (int index = 0; index < slots.length; index++) {
			if (slots[index] == slot) {
				return index;
			}
		}

		return -1;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		String expectedTitle = color(plugin.configs().gui().getString("main-menu.title", "&d❤ Marriage Menu"));

		if (guiManager.isHomesMenuTitle(event.getView().getTitle())) {
			handleHomesMenuClick(event);
			return;
		}

		if (guiManager.isMailMenuTitle(event.getView().getTitle())) {
			handleMailMenuClick(event);
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
			case "mail" -> plugin.marriageGuiManager().openMailMenu(player);
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