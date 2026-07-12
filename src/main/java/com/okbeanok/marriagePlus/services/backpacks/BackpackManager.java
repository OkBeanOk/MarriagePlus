package com.okbeanok.marriagePlus.services.backpacks;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.CooldownManager;
import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BackpackManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final CooldownManager cooldownManager;

	private final Map<UUID, Inventory> backpacks = new HashMap<>();
	private final Set<UUID> backpackAllowed = new HashSet<>();

	public BackpackManager(MarriagePlus plugin, MarriageManager marriageManager, CooldownManager cooldownManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.cooldownManager = cooldownManager;
	}

	public Map<UUID, Inventory> backpacks() {
		return backpacks;
	}

	public Set<UUID> backpackAllowed() {
		return backpackAllowed;
	}

	public void backpackCommand(Player player, String[] args) {
		if (args.length >= 2) {
			if (args[1].equalsIgnoreCase("on")) {
				backpackAllowed.add(player.getUniqueId());
				plugin.dataManager().saveData();
				plugin.langManager().send(player, "backpack.enabled");
				plugin.achievementManager().checkBackpackBuddies(player);
				return;
			}

			if (args[1].equalsIgnoreCase("off")) {
				backpackAllowed.remove(player.getUniqueId());
				plugin.dataManager().saveData();
				plugin.langManager().send(player, "backpack.disabled");
				return;
			}
		}

		if (cooldownManager.isOnCooldown(player, "backpack")) {
			return;
		}

		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		if (!backpackAllowed.contains(partner.getUniqueId())) {
			plugin.langManager().send(player, "backpack.not-allowed");
			return;
		}

		Inventory backpack = backpacks.computeIfAbsent(partner.getUniqueId(), this::loadBackpack);

		cooldownManager.setCooldown(player, "backpack", plugin.getConfig().getInt("settings.cooldowns.backpack-seconds", 2));
		plugin.langManager().send(player, "backpack.opened");
		player.openInventory(backpack);
	}

	public Inventory loadBackpack(UUID owner) {
		OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(owner);
		String ownerName = offlineOwner.getName() == null ? plugin.langManager().get("general.player") : offlineOwner.getName();

		Inventory inventory = Bukkit.createInventory(null, 27, plugin.langManager().get("backpack.title", Map.of(
				"%player%", ownerName
		)));
		ItemStack[] items = plugin.dataManager().loadBackpackContents(owner);

		inventory.setContents(items);
		return inventory;
	}


	public void saveBackpack(UUID owner, Inventory inventory) {
		plugin.dataManager().saveBackpack(owner, inventory);
	}

	public void saveMatchingBackpack(Inventory inventory) {
		for (Map.Entry<UUID, Inventory> entry : backpacks.entrySet()) {
			if (entry.getValue().equals(inventory)) {
				saveBackpack(entry.getKey(), inventory);
				return;
			}
		}
	}
}