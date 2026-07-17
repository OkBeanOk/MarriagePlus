package com.okbeanok.marriagePlus.services.backpacks;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.CooldownManager;
import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
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
	private final Set<UUID> adminBackpackViewers = new HashSet<>();

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
				if (isSharedCoupleMode()) {
					plugin.langManager().send(player, "backpack.access-toggle-not-used-shared");
					return;
				}

				backpackAllowed.add(player.getUniqueId());
				plugin.dataManager().saveData();
				plugin.langManager().send(player, "backpack.enabled");
				plugin.achievementManager().checkBackpackBuddies(player);
				return;
			}

			if (args[1].equalsIgnoreCase("off")) {
				if (isSharedCoupleMode()) {
					plugin.langManager().send(player, "backpack.access-toggle-not-used-shared");
					return;
				}

				backpackAllowed.remove(player.getUniqueId());
				plugin.dataManager().saveData();
				plugin.langManager().send(player, "backpack.disabled");
				return;
			}
		}

		if (cooldownManager.isOnCooldown(player, "backpack")) {
			return;
		}

		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (isSharedCoupleMode()) {
			openSharedCoupleBackpack(player, partnerId, partner);
			return;
		}

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

		if (isReadOnly()) {
			plugin.langManager().send(player, "backpack.read-only");
		}

		notifyOwnerBackpackOpened(player, partner);
		player.openInventory(backpack);
	}

	private void openSharedCoupleBackpack(Player player, UUID partnerId, Player partner) {
		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);
		UUID sharedBackpackId = sharedBackpackId(coupleKey);

		Inventory backpack = backpacks.computeIfAbsent(sharedBackpackId, ignored -> loadSharedBackpack(sharedBackpackId, player, partnerId));

		cooldownManager.setCooldown(player, "backpack", plugin.getConfig().getInt("settings.cooldowns.backpack-seconds", 2));
		plugin.langManager().send(player, "backpack.shared-opened");

		if (isReadOnly()) {
			plugin.langManager().send(player, "backpack.read-only");
		}

		if (partner != null && plugin.configs().backpack().getBoolean("notify-owner-on-open", true)) {
			plugin.langManager().send(partner, "backpack.shared-opened-by-partner", Map.of(
					"%player%", player.getName()
			));
		}

		player.openInventory(backpack);
	}

	private void notifyOwnerBackpackOpened(Player opener, Player owner) {
		if (!plugin.configs().backpack().getBoolean("notify-owner-on-open", true)) {
			return;
		}

		plugin.langManager().send(owner, "backpack.opened-by-partner", Map.of(
				"%player%", opener.getName()
		));
	}

	private Inventory loadSharedBackpack(UUID sharedBackpackId, Player player, UUID partnerId) {
		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		String partnerName = partner.getName() == null ? plugin.langManager().get("general.partner") : partner.getName();

		Inventory inventory = Bukkit.createInventory(null, getBackpackSize(), plugin.langManager().get("backpack.shared-title", Map.of(
				"%player%", player.getName(),
				"%partner%", partnerName
		)));
		ItemStack[] items = plugin.dataManager().loadBackpackContents(sharedBackpackId);

		inventory.setContents(resizeContents(items, inventory.getSize()));
		return inventory;
	}

	private boolean isSharedCoupleMode() {
		return plugin.configs().backpack().getString("mode", "PLAYER_OWNED").equalsIgnoreCase("SHARED_COUPLE");
	}

	private UUID sharedBackpackId(String coupleKey) {
		return UUID.nameUUIDFromBytes(("shared-backpack:" + coupleKey).getBytes(StandardCharsets.UTF_8));
	}

	public Inventory loadBackpack(UUID owner) {
		OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(owner);
		String ownerName = offlineOwner.getName() == null ? plugin.langManager().get("general.player") : offlineOwner.getName();

		Inventory inventory = Bukkit.createInventory(null, getBackpackSize(), plugin.langManager().get("backpack.title", Map.of(
				"%player%", ownerName
		)));
		ItemStack[] items = plugin.dataManager().loadBackpackContents(owner);

		inventory.setContents(resizeContents(items, inventory.getSize()));
		return inventory;
	}

	private int getBackpackSize() {
		int size = plugin.configs().backpack().getInt("size", 27);
		size = Math.max(9, Math.min(54, size));

		if (size % 9 != 0) {
			size = (size / 9) * 9;
		}

		return Math.max(9, size);
	}

	private ItemStack[] resizeContents(ItemStack[] originalContents, int size) {
		ItemStack[] resizedContents = new ItemStack[size];

		if (originalContents == null) {
			return resizedContents;
		}

		System.arraycopy(originalContents, 0, resizedContents, 0, Math.min(originalContents.length, resizedContents.length));
		return resizedContents;
	}

	public void saveBackpack(UUID owner, Inventory inventory) {
		plugin.dataManager().saveBackpack(owner, inventory);
	}

	public boolean isReadOnly() {
		return plugin.configs().backpack().getBoolean("read-only", false);
	}

	public boolean isBackpackInventory(Inventory inventory) {
		return backpacks.containsValue(inventory);
	}

	public void openAdminBackpack(Player admin, String targetName) {
		OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
		UUID targetId = target.getUniqueId();
		UUID partnerId = marriageManager.getPartnerId(targetId);
		String displayName = target.getName() == null ? targetName : target.getName();

		UUID selectedBackpackId = targetId;

		if (isSharedCoupleMode() && partnerId != null) {
			selectedBackpackId = sharedBackpackId(marriageManager.coupleKey(targetId, partnerId));
		}

		UUID backpackId = selectedBackpackId;

		Inventory backpack = backpacks.computeIfAbsent(backpackId, ignored -> {
			if (isSharedCoupleMode() && partnerId != null) {
				Player onlineTarget = Bukkit.getPlayer(targetId);
				Player titlePlayer = onlineTarget == null ? admin : onlineTarget;
				return loadSharedBackpack(backpackId, titlePlayer, partnerId);
			}

			return loadBackpack(targetId);
		});

		plugin.langManager().send(admin, "backpack.admin-opened", Map.of(
				"%player%", displayName
		));

		Player onlineTarget = Bukkit.getPlayer(targetId);

		if (onlineTarget != null && plugin.configs().backpack().getBoolean("notify-owner-on-admin-open", true)) {
			plugin.langManager().send(onlineTarget, "backpack.opened-by-admin", Map.of(
					"%player%", admin.getName()
			));
		}

		adminBackpackViewers.add(admin.getUniqueId());
		admin.openInventory(backpack);
	}


	public boolean isAdminBackpackViewer(UUID playerId) {
		return adminBackpackViewers.contains(playerId);
	}

	public void clearAdminBackpackViewer(UUID playerId) {
		adminBackpackViewers.remove(playerId);
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