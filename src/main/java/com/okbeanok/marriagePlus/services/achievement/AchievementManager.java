package com.okbeanok.marriagePlus.services.achievement;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.AchievementDefinition;
import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class AchievementManager {

	private static final long MILLIS_PER_DAY = 86_400_000L;

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
		ConfigurationSection definitionsSection = plugin.configs().achievements().getConfigurationSection("definitions");

		if (definitionsSection == null) {
			plugin.getLogger().warning(plugin.langManager().get("achievements.no-definitions"));
			return;
		}

		for (String id : definitionsSection.getKeys(false)) {
			String path = "definitions." + id;

			String name = plugin.configs().achievements().getString(path + ".name", id);
			String description = plugin.configs().achievements().getString(path + ".description", plugin.langManager().get("achievements.no-description"));
			String trigger = plugin.configs().achievements().getString(path + ".trigger", "manual").toLowerCase(Locale.ROOT);
			String action = plugin.configs().achievements().getString(path + ".action", "").toLowerCase(Locale.ROOT);
			int days = plugin.configs().achievements().getInt(path + ".days", 0);
			boolean hidden = plugin.configs().achievements().getBoolean(path + ".hidden", false);

			definitions.put(id, new AchievementDefinition(
					id,
					name,
					description,
					trigger,
					action,
					days,
					hidden
			));
		}
	}

	public void achievementsCommand(Player player, String[] args) {
		if (args.length >= 2 && (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("chat"))) {
			showAchievements(player);
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("partner")) {
			openAchievementsGui(player);
			return;
		}

		openAchievementsGui(player);
	}

	private void openAchievementsGui(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);
		List<String> unlocked = unlockedAchievements.getOrDefault(coupleKey, new ArrayList<>());
		boolean showHiddenPlaceholders = plugin.configs().achievements().getBoolean("gui.show-hidden-placeholders", true);
		int visibleAchievements = visibleAchievementCount(unlocked, showHiddenPlaceholders);

		int inventorySize = Math.clamp(((visibleAchievements + 8) / 9) * 9, 9, 54);
		String title = color(plugin.langManager().get("achievements.gui-title"));

		Inventory inventory = Bukkit.createInventory(null, inventorySize, title);

		int slot = 0;

		for (AchievementDefinition definition : definitions.values()) {
			boolean hasUnlocked = unlocked.contains(definition.id());

			if (definition.hidden() && !hasUnlocked && !showHiddenPlaceholders) {
				continue;
			}

			if (slot >= inventory.getSize()) {
				break;
			}

			inventory.setItem(slot, createAchievementItem(player, definition, hasUnlocked));
			slot++;
		}

		player.openInventory(inventory);
	}

	private int visibleAchievementCount(List<String> unlocked, boolean showHiddenPlaceholders) {
		int count = 0;

		for (AchievementDefinition definition : definitions.values()) {
			boolean hasUnlocked = unlocked.contains(definition.id());

			if (definition.hidden() && !hasUnlocked && !showHiddenPlaceholders) {
				continue;
			}

			count++;
		}

		return count;
	}

	private ItemStack createAchievementItem(Player player, AchievementDefinition definition, boolean unlocked) {
		boolean hiddenLocked = definition.hidden() && !unlocked;

		String materialName = plugin.configs().achievements().getString(
				unlocked ? "gui.unlocked-material" : "gui.locked-material",
				unlocked ? "LIME_DYE" : "GRAY_DYE"
		);

		Material material = Material.matchMaterial(materialName == null ? "" : materialName.toUpperCase(Locale.ROOT));

		if (material == null || material.isAir()) {
			material = unlocked ? Material.LIME_DYE : Material.GRAY_DYE;
		}

		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return item;
		}

		String achievementName = hiddenLocked
				? plugin.langManager().get("achievements.hidden-name")
				: definition.name();

		String achievementDescription = hiddenLocked
				? plugin.langManager().get("achievements.hidden-description")
				: definition.description();

		List<String> lore = new ArrayList<>();
		lore.add(color(plugin.langManager().get("achievements.gui-description", Map.of(
				"%description%", achievementDescription
		))));
		lore.add("");

		if (!hiddenLocked) {
			lore.add(color(progressLine(player, definition, unlocked)));
			lore.add("");
		}

		lore.add(color(plugin.langManager().get(unlocked ? "achievements.gui-status-unlocked" : "achievements.gui-status-locked")));

		meta.setDisplayName(color(plugin.langManager().get(
				unlocked ? "achievements.gui-item-unlocked" : "achievements.gui-item-locked",
				Map.of("%achievement%", achievementName)
		)));
		meta.setLore(lore);

		item.setItemMeta(meta);
		return item;
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

			if (definition.hidden() && !hasUnlocked) {
				continue;
			}

			plugin.langManager().send(player, hasUnlocked ? "achievements.unlocked" : "achievements.locked", Map.of(
					"%achievement%", definition.name(),
					"%description%", definition.description()
			));

			plugin.langManager().send(player, "achievements.progress-line", Map.of(
					"%progress%", progressText(player, definition, hasUnlocked)
			));
		}
	}

	private String progressLine(Player player, AchievementDefinition definition, boolean unlocked) {
		return plugin.langManager().get("achievements.gui-progress", Map.of(
				"%progress%", progressText(player, definition, unlocked)
		));
	}

	private String progressText(Player player, AchievementDefinition definition, boolean unlocked) {
		if (unlocked) {
			return plugin.langManager().get("achievements.progress-complete");
		}

		if (definition.trigger().equalsIgnoreCase("anniversary")) {
			long currentDays = marriedDays(player);
			int requiredDays = Math.max(0, definition.days());

			return plugin.langManager().get("achievements.progress-days", Map.of(
					"%current%", String.valueOf(Math.min(currentDays, requiredDays)),
					"%required%", String.valueOf(requiredDays)
			));
		}

		if (definition.trigger().equalsIgnoreCase("action") && !definition.action().isBlank()) {
			return plugin.langManager().get("achievements.progress-action", Map.of(
					"%action%", definition.action()
			));
		}

		return plugin.langManager().get("achievements.progress-trigger", Map.of(
				"%trigger%", definition.trigger()
		));
	}

	private long marriedDays(Player player) {
		long date = plugin.dataManager().dataConfig().getLong("marriage-dates." + player.getUniqueId(), 0L);

		if (date <= 0L) {
			return 0L;
		}

		return Math.max(0L, (System.currentTimeMillis() - date) / MILLIS_PER_DAY);
	}

	public void showPartnerAchievements(Player player) {
		showAchievements(player);
	}

	public void unlockByTrigger(Player player, String trigger) {
		unlockByTrigger(player, trigger, "");
	}

	public void unlockByTrigger(Player player, String trigger, String actionName) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		String normalizedTrigger = trigger.toLowerCase(Locale.ROOT);
		String normalizedAction = actionName == null ? "" : actionName.toLowerCase(Locale.ROOT);

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

	public boolean grant(UUID playerId, String achievementId) {
		UUID partnerId = marriageManager.getPartnerId(playerId);

		if (partnerId == null || !definitions.containsKey(achievementId)) {
			return false;
		}

		String coupleKey = marriageManager.coupleKey(playerId, partnerId);
		List<String> unlocked = unlockedAchievements.computeIfAbsent(coupleKey, ignored -> new ArrayList<>());

		if (unlocked.contains(achievementId)) {
			return false;
		}

		unlock(playerId, partnerId, achievementId);
		return true;
	}

	public boolean revoke(UUID playerId, String achievementId) {
		UUID partnerId = marriageManager.getPartnerId(playerId);

		if (partnerId == null || !definitions.containsKey(achievementId)) {
			return false;
		}

		String coupleKey = marriageManager.coupleKey(playerId, partnerId);
		List<String> unlocked = unlockedAchievements.get(coupleKey);

		if (unlocked == null || !unlocked.remove(achievementId)) {
			return false;
		}

		if (unlocked.isEmpty()) {
			unlockedAchievements.remove(coupleKey);
		}

		plugin.dataManager().saveData();
		return true;
	}

	public boolean hasUnlocked(UUID playerId, String achievementId) {
		UUID partnerId = marriageManager.getPartnerId(playerId);

		if (partnerId == null) {
			return false;
		}

		String coupleKey = marriageManager.coupleKey(playerId, partnerId);

		return unlockedAchievements.getOrDefault(coupleKey, new ArrayList<>()).contains(achievementId);
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

		long days = Math.max(0L, (System.currentTimeMillis() - date) / MILLIS_PER_DAY);

		for (AchievementDefinition definition : definitions.values()) {
			if (!definition.trigger().equalsIgnoreCase("anniversary")) {
				continue;
			}

			if (definition.days() <= 0) {
				continue;
			}

			if (days >= definition.days()) {
				unlock(player.getUniqueId(), partnerId, definition.id());
			}
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
		if (!plugin.configs().achievements().getBoolean("broadcast-unlocks", true)) {
			notifyPartner(firstId, definition);
			notifyPartner(secondId, definition);
			return;
		}

		OfflinePlayer first = Bukkit.getOfflinePlayer(firstId);
		OfflinePlayer second = Bukkit.getOfflinePlayer(secondId);

		String firstName = safeName(first);
		String secondName = safeName(second);

		String message = plugin.configs().achievements().getString(
				"unlock-broadcast",
				"&d❤ &f%player% &7& &f%partner% &dunlocked achievement: &f%achievement%&d!"
		);

		message = message
				.replace("%player%", firstName)
				.replace("%partner%", secondName)
				.replace("%achievement%", definition.name());

		Bukkit.broadcastMessage(color(message));
	}

	private void notifyPartner(UUID playerId, AchievementDefinition definition) {
		Player player = Bukkit.getPlayer(playerId);

		if (player == null) {
			return;
		}

		plugin.langManager().send(player, "achievements.unlock-message", Map.of(
				"%achievement%", definition.name()
		));
	}

	private void reward(UUID firstId, UUID secondId, String achievementId) {
		String firstName = safeName(Bukkit.getOfflinePlayer(firstId));
		String secondName = safeName(Bukkit.getOfflinePlayer(secondId));

		rewardCommands(firstName, secondName, achievementId);
		rewardItems(firstId, secondId, firstName, secondName, achievementId);
	}

	private void rewardCommands(String firstName, String secondName, String achievementId) {
		List<String> commands = plugin.configs().achievements().getStringList("rewards." + achievementId + ".commands");

		for (String command : commands) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
					.replace("%player%", firstName)
					.replace("%partner%", secondName));
		}
	}

	private void rewardItems(UUID firstId, UUID secondId, String firstName, String secondName, String achievementId) {
		List<Map<?, ?>> items = plugin.configs().achievements().getMapList("rewards." + achievementId + ".items");

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
		amount = Math.clamp(amount, 1, material.getMaxStackSize());

		ItemStack item = new ItemStack(material, amount);
		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return item;
		}

		applyRewardName(meta, itemMap, playerName, partnerName);
		applyRewardLore(meta, itemMap, playerName, partnerName);
		applyRewardCustomModelData(meta, itemMap);
		applyRewardEnchants(meta, itemMap);
		applyRewardFlags(meta, itemMap);

		item.setItemMeta(meta);
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

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plugin.langManager().get("general.unknown") : player.getName();
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