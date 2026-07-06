package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.AchievementDefinition;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class AchievementManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final Map<String, List<String>> unlockedAchievements = new HashMap<>();
	private final Map<String, AchievementDefinition> definitions = new LinkedHashMap<>();

	public AchievementManager(MarriagePlus plugin, MarriageManager marriageManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		loadDefinitions();
	}

	public Map<String, List<String>> unlockedAchievements() {
		return unlockedAchievements;
	}

	public Map<String, AchievementDefinition> definitions() {
		return definitions;
	}

	public void reloadDefinitions() {
		definitions.clear();
		loadDefinitions();
	}

	private void loadDefinitions() {
		ConfigurationSection definitionsSection = plugin.getConfig().getConfigurationSection("achievements.definitions");

		if (definitionsSection == null) {
			plugin.getLogger().warning(plugin.langManager().get("achievements.no-definitions"));
			return;
		}

		for (String id : definitionsSection.getKeys(false)) {
			String path = "achievements.definitions." + id;

			String name = plugin.getConfig().getString(path + ".name", id);
			String description = plugin.getConfig().getString(path + ".description", plugin.langManager().get("achievements.no-description"));
			String trigger = plugin.getConfig().getString(path + ".trigger", "manual").toLowerCase();
			String action = plugin.getConfig().getString(path + ".action", "").toLowerCase();
			int days = plugin.getConfig().getInt(path + ".days", 0);

			definitions.put(id, new AchievementDefinition(
					id,
					name,
					description,
					trigger,
					action,
					days
			));
		}
	}

	public void achievementsCommand(Player player, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("partner")) {
			showPartnerAchievements(player);
			return;
		}

		showAchievements(player);
	}

	public void showAchievements(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);
		List<String> unlocked = unlockedAchievements.getOrDefault(coupleKey, new ArrayList<>());

		plugin.langManager().send(player, "achievements.header");
		plugin.langManager().send(player, "achievements.unlocked-count", Map.of(
				"%unlocked%", String.valueOf(unlocked.size()),
				"%total%", String.valueOf(definitions.size())
		));

		for (AchievementDefinition definition : definitions.values()) {
			boolean hasUnlocked = unlocked.contains(definition.id());

			plugin.langManager().send(player, hasUnlocked ? "achievements.unlocked" : "achievements.locked", Map.of(
					"%achievement%", definition.name(),
					"%description%", definition.description()
			));
		}
	}

	public void unlockByTrigger(Player player, String trigger) {
		unlockByTrigger(player, trigger, "");
	}

	public void unlockByTrigger(Player player, String trigger, String actionName) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		String normalizedTrigger = trigger.toLowerCase();
		String normalizedAction = actionName == null ? "" : actionName.toLowerCase();

		for (AchievementDefinition definition : definitions.values()) {
			if (!definition.trigger().equalsIgnoreCase(normalizedTrigger)) {
				continue;
			}

			if (definition.trigger().equalsIgnoreCase("action")
					&& !definition.action().isBlank()
					&& !definition.action().equalsIgnoreCase(normalizedAction)) {
				continue;
			}

			unlock(player.getUniqueId(), partnerId, definition.id());
		}
	}

	public void showPartnerAchievements(Player player) {
		showAchievements(player);
	}

	public void unlock(Player player, String achievementId) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		unlock(player.getUniqueId(), partnerId, achievementId);
	}

	public void unlock(UUID firstId, UUID secondId, String achievementId) {
		AchievementDefinition definition = definitions.get(achievementId);

		if (definition == null) {
			return;
		}

		String coupleKey = marriageManager.coupleKey(firstId, secondId);
		List<String> unlocked = unlockedAchievements.computeIfAbsent(coupleKey, ignored -> new ArrayList<>());

		if (unlocked.contains(achievementId)) {
			return;
		}

		unlocked.add(achievementId);
		plugin.dataManager().saveData();

		reward(firstId, secondId, achievementId);
		notifyUnlock(firstId, secondId, definition);
	}

	public void checkAnniversaryAchievements(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		long date = plugin.dataManager().dataConfig().getLong("marriage-dates." + player.getUniqueId(), 0L);

		if (date <= 0L) {
			return;
		}

		long days = Math.max(0L, (System.currentTimeMillis() - date) / 86_400_000L);

		if (days >= 7) {
			unlock(player.getUniqueId(), partnerId, "anniversary_7");
		}

		if (days >= 30) {
			unlock(player.getUniqueId(), partnerId, "anniversary_30");
		}

		if (days >= 100) {
			unlock(player.getUniqueId(), partnerId, "anniversary_100");
		}
	}

	public void checkBackpackBuddies(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		if (plugin.backpackManager().backpackAllowed().contains(player.getUniqueId())
				&& plugin.backpackManager().backpackAllowed().contains(partnerId)) {
			unlock(player.getUniqueId(), partnerId, "backpack_buddies");
		}
	}

	private void notifyUnlock(UUID firstId, UUID secondId, AchievementDefinition definition) {
		if (!plugin.getConfig().getBoolean("achievements.broadcast-unlocks", true)) {
			Player first = Bukkit.getPlayer(firstId);
			Player second = Bukkit.getPlayer(secondId);

			if (first != null) {
				plugin.langManager().send(first, "achievements.unlock-message", Map.of(
						"%achievement%", definition.name()
				));
			}

			if (second != null) {
				plugin.langManager().send(second, "achievements.unlock-message", Map.of(
						"%achievement%", definition.name()
				));
			}

			return;
		}

		OfflinePlayer first = Bukkit.getOfflinePlayer(firstId);
		OfflinePlayer second = Bukkit.getOfflinePlayer(secondId);

		String firstName = first.getName() == null ? plugin.langManager().get("general.unknown") : first.getName();
		String secondName = second.getName() == null ? plugin.langManager().get("general.unknown") : second.getName();

		String message = plugin.getConfig().getString(
				"achievements.unlock-broadcast",
				"&d❤ &f%player% &7& &f%partner% &dunlocked achievement: &f%achievement%&d!"
		);

		message = message
				.replace("%player%", firstName)
				.replace("%partner%", secondName)
				.replace("%achievement%", definition.name());

		Bukkit.broadcastMessage(color(message));
	}

	private void reward(UUID firstId, UUID secondId, String achievementId) {
		String firstName = Bukkit.getOfflinePlayer(firstId).getName();
		String secondName = Bukkit.getOfflinePlayer(secondId).getName();

		if (firstName == null || secondName == null) {
			return;
		}

		rewardCommands(firstName, secondName, achievementId);
		rewardItems(firstId, secondId, firstName, secondName, achievementId);
	}

	private void rewardCommands(String firstName, String secondName, String achievementId) {
		List<String> commands = plugin.getConfig().getStringList("achievements.rewards." + achievementId + ".commands");

		for (String command : commands) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
					.replace("%player%", firstName)
					.replace("%partner%", secondName));
		}
	}

	private void rewardItems(UUID firstId, UUID secondId, String firstName, String secondName, String achievementId) {
		List<Map<?, ?>> items = plugin.getConfig().getMapList("achievements.rewards." + achievementId + ".items");

		if (items.isEmpty()) {
			return;
		}

		Player first = Bukkit.getPlayer(firstId);
		Player second = Bukkit.getPlayer(secondId);

		for (Map<?, ?> itemMap : items) {
			ItemStack firstReward = createRewardItem(itemMap, firstName, secondName);
			ItemStack secondReward = createRewardItem(itemMap, secondName, firstName);

			if (first != null && firstReward != null) {
				giveItem(first, firstReward);
			}

			if (second != null && secondReward != null) {
				giveItem(second, secondReward);
			}
		}
	}

	private ItemStack createRewardItem(Map<?, ?> itemMap, String playerName, String partnerName) {
		Object materialObject = itemMap.get("material");
		String materialName = String.valueOf(materialObject == null ? "PAPER" : materialObject).toUpperCase(Locale.ROOT);
		Material material = Material.matchMaterial(materialName);

		if (material == null || material.isAir()) {
			plugin.getLogger().warning(plugin.langManager().get("achievements.invalid-reward-material", Map.of(
					"%material%", materialName
			)));
			return null;
		}

		int amount = parseInt(itemMap.get("amount"), 1);
		amount = Math.max(1, Math.min(amount, material.getMaxStackSize()));

		ItemStack item = new ItemStack(material, amount);
		ItemMeta meta = item.getItemMeta();

		if (meta != null) {
			applyRewardName(meta, itemMap, playerName, partnerName);
			applyRewardLore(meta, itemMap, playerName, partnerName);
			applyRewardCustomModelData(meta, itemMap);
			applyRewardEnchants(meta, itemMap);
			applyRewardFlags(meta, itemMap);

			item.setItemMeta(meta);
		}

		return item;
	}

	private void applyRewardName(ItemMeta meta, Map<?, ?> itemMap, String playerName, String partnerName) {
		Object nameObject = itemMap.get("name");

		if (nameObject == null) {
			return;
		}

		meta.setDisplayName(color(applyRewardPlaceholders(String.valueOf(nameObject), playerName, partnerName)));
	}

	private void applyRewardLore(ItemMeta meta, Map<?, ?> itemMap, String playerName, String partnerName) {
		Object loreObject = itemMap.get("lore");

		if (!(loreObject instanceof List<?> rawLore)) {
			return;
		}

		List<String> lore = new ArrayList<>();

		for (Object line : rawLore) {
			lore.add(color(applyRewardPlaceholders(String.valueOf(line), playerName, partnerName)));
		}

		meta.setLore(lore);
	}

	private void applyRewardCustomModelData(ItemMeta meta, Map<?, ?> itemMap) {
		Object customModelDataObject = itemMap.get("custom-model-data");

		if (customModelDataObject == null) {
			return;
		}

		meta.setCustomModelData(parseInt(customModelDataObject, 0));
	}

	private void applyRewardEnchants(ItemMeta meta, Map<?, ?> itemMap) {
		Object enchantsObject = itemMap.get("enchants");

		if (!(enchantsObject instanceof Map<?, ?> enchants)) {
			return;
		}

		for (Map.Entry<?, ?> entry : enchants.entrySet()) {
			Enchantment enchantment = getEnchantment(String.valueOf(entry.getKey()));

			if (enchantment == null) {
				plugin.getLogger().warning(plugin.langManager().get("achievements.invalid-reward-enchantment", Map.of(
						"%enchantment%", String.valueOf(entry.getKey())
				)));
				continue;
			}

			int level = Math.max(1, parseInt(entry.getValue(), 1));
			meta.addEnchant(enchantment, level, true);
		}
	}

	private void applyRewardFlags(ItemMeta meta, Map<?, ?> itemMap) {
		Object hideEnchantsObject = itemMap.get("hide-enchants");

		if (hideEnchantsObject != null && Boolean.parseBoolean(String.valueOf(hideEnchantsObject))) {
			meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}

		Object flagsObject = itemMap.get("flags");

		if (!(flagsObject instanceof List<?> flags)) {
			return;
		}

		for (Object flagObject : flags) {
			try {
				meta.addItemFlags(ItemFlag.valueOf(String.valueOf(flagObject).toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning(plugin.langManager().get("achievements.invalid-reward-flag", Map.of(
						"%flag%", String.valueOf(flagObject)
				)));
			}
		}
	}

	private Enchantment getEnchantment(String enchantmentName) {
		String normalizedName = enchantmentName.toLowerCase(Locale.ROOT).replace("_", ".");

		Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(normalizedName));

		if (enchantment != null) {
			return enchantment;
		}

		return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantmentName.toLowerCase(Locale.ROOT)));
	}

	private void giveItem(Player player, ItemStack item) {
		Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);

		for (ItemStack leftover : leftovers.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), leftover);
		}
	}

	private String applyRewardPlaceholders(String text, String playerName, String partnerName) {
		return text
				.replace("%player%", playerName)
				.replace("%partner%", partnerName);
	}

	private int parseInt(Object object, int fallback) {
		if (object == null) {
			return fallback;
		}

		if (object instanceof Number number) {
			return number.intValue();
		}

		try {
			return Integer.parseInt(String.valueOf(object));
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}
}