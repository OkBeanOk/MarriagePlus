package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
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

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

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
				player.sendMessage(color("&aYour partner can now use your backpack."));
				return;
			}

			if (args[1].equalsIgnoreCase("off")) {
				backpackAllowed.remove(player.getUniqueId());
				plugin.dataManager().saveData();
				player.sendMessage(color("&eYour partner can no longer use your backpack."));
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
			player.sendMessage(color("&cYour partner has not allowed you to use their backpack."));
			return;
		}

		Inventory backpack = backpacks.computeIfAbsent(partner.getUniqueId(), this::loadBackpack);

		cooldownManager.setCooldown(player, "backpack", plugin.getConfig().getInt("settings.cooldowns.backpack-seconds", 2));
		player.openInventory(backpack);
	}

	public Inventory loadBackpack(UUID owner) {
		OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(owner);
		String ownerName = offlineOwner.getName() == null ? "Player" : offlineOwner.getName();

		Inventory inventory = Bukkit.createInventory(null, 27, color("&d" + ownerName + "'s Marriage Backpack"));
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