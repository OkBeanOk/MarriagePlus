package com.okbeanok.marriagePlus.services.anniversaries;

import com.okbeanok.marriagePlus.MarriagePlus;

import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class AnniversaryManager {

	private static final long MILLIS_PER_DAY = 86_400_000L;

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final MarriageXpManager marriageXpManager;
	private final Map<String, Set<Integer>> claimedMilestones = new HashMap<>();

	public AnniversaryManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			MarriageXpManager marriageXpManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.marriageXpManager = marriageXpManager;
	}

	public Map<String, Set<Integer>> claimedMilestones() {
		return claimedMilestones;
	}

	public void anniversaryCommand(Player player, String[] args) {
		if (!plugin.configs().anniversaries().getBoolean("enabled", true)) {
			plugin.langManager().send(player, "anniversary.disabled");
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("claim")) {
			claimRewards(player);
			return;
		}

		showAnniversary(player);
	}

	public void showAnniversary(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		long marriageDate = getMarriageDate(player.getUniqueId());

		if (marriageDate <= 0L) {
			plugin.langManager().send(player, "anniversary.no-date");
			return;
		}

		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);
		long days = getDaysMarried(marriageDate);

		plugin.langManager().send(player, "anniversary.header");
		plugin.langManager().send(player, "anniversary.married-for", Map.of(
				"%days%", String.valueOf(days)
		));

		for (int milestone : getMilestones()) {
			String path = "milestones." + milestone;
			String name = plugin.configs().anniversaries().getString(path + ".name", milestone + " Days");
			boolean available = days >= milestone;
			boolean claimed = hasClaimed(coupleKey, milestone);

			plugin.langManager().send(player, "anniversary.milestone", Map.of(
					"%milestone%", String.valueOf(milestone),
					"%name%", name,
					"%status%", getMilestoneStatus(available, claimed)
			));
		}

		plugin.langManager().send(player, "anniversary.claim-hint");
		plugin.achievementManager().checkAnniversaryAchievements(player);
	}

	private void claimRewards(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		long marriageDate = getMarriageDate(player.getUniqueId());

		if (marriageDate <= 0L) {
			plugin.langManager().send(player, "anniversary.no-date");
			return;
		}

		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);
		long days = getDaysMarried(marriageDate);

		int claimedCount = 0;
		int totalXp = 0;

		for (int milestone : getMilestones()) {
			if (days < milestone || hasClaimed(coupleKey, milestone)) {
				continue;
			}

			claimMilestone(coupleKey, milestone);
			rewardMilestone(player.getUniqueId(), partnerId, milestone);

			totalXp += plugin.configs().anniversaries().getInt("milestones." + milestone + ".relationship-xp", 0);
			claimedCount++;
		}

		if (claimedCount <= 0) {
			plugin.langManager().send(player, "anniversary.nothing-to-claim");
			return;
		}

		if (totalXp > 0) {
			marriageXpManager.addXp(player.getUniqueId(), partnerId, totalXp);
		}

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "anniversary.claimed", Map.of(
				"%milestones%", String.valueOf(claimedCount),
				"%xp%", String.valueOf(totalXp)
		));

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner != null) {
			plugin.notificationManager().notifyPartner(player, "anniversary-claimed", Map.of(
					"%milestones%", String.valueOf(claimedCount),
					"%xp%", String.valueOf(totalXp)
			));
		}

		plugin.achievementManager().checkAnniversaryAchievements(player);
	}

	private void rewardMilestone(UUID firstId, UUID secondId, int milestone) {
		String firstName = safeName(Bukkit.getOfflinePlayer(firstId));
		String secondName = safeName(Bukkit.getOfflinePlayer(secondId));

		rewardCommands(firstName, secondName, milestone);
		rewardItems(firstId, secondId, firstName, secondName, milestone);
	}

	private void rewardCommands(String firstName, String secondName, int milestone) {
		List<String> commands = plugin.configs().anniversaries().getStringList("milestones." + milestone + ".commands");

		if (commands.isEmpty()) {
			return;
		}

		boolean rewardBoth = plugin.configs().anniversaries().getBoolean("reward-commands-for-both-partners", true);

		for (String command : commands) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
					.replace("%player%", firstName)
					.replace("%partner%", secondName));

			if (rewardBoth) {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
						.replace("%player%", secondName)
						.replace("%partner%", firstName));
			}
		}
	}

	private void rewardItems(UUID firstId, UUID secondId, String firstName, String secondName, int milestone) {
		List<Map<?, ?>> items = plugin.configs().anniversaries().getMapList("milestones." + milestone + ".items");

		if (items.isEmpty()) {
			return;
		}

		boolean rewardBoth = plugin.configs().anniversaries().getBoolean("reward-both-partners", true);
		Player first = Bukkit.getPlayer(firstId);
		Player second = Bukkit.getPlayer(secondId);

		for (Map<?, ?> itemMap : items) {
			ItemStack firstReward = createRewardItem(itemMap, firstName, secondName);

			if (first != null && firstReward != null) {
				giveItem(first, firstReward);
			}

			if (!rewardBoth) {
				continue;
			}

			ItemStack secondReward = createRewardItem(itemMap, secondName, firstName);

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
			plugin.getLogger().warning(plugin.langManager().get("anniversary.invalid-reward-material", Map.of(
					"%material%", materialName
			)));
			return null;
		}

		int amount = parseInt(itemMap.get("amount"), 1);
		amount = Math.max(1, Math.min(amount, material.getMaxStackSize()));

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
				plugin.getLogger().warning(plugin.langManager().get("anniversary.invalid-reward-enchantment", Map.of(
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
				plugin.getLogger().warning(plugin.langManager().get("anniversary.invalid-reward-flag", Map.of(
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

	private Set<Integer> getMilestones() {
		ConfigurationSection milestonesSection = plugin.configs().anniversaries().getConfigurationSection("milestones");

		if (milestonesSection == null) {
			return Set.of();
		}

		Set<Integer> milestones = new TreeSet<>();

		for (String key : milestonesSection.getKeys(false)) {
			try {
				milestones.add(Integer.parseInt(key));
			} catch (NumberFormatException exception) {
				plugin.getLogger().warning("Invalid anniversary milestone in anniversaries.yml: " + key);
			}
		}

		return milestones;
	}

	private boolean hasClaimed(String coupleKey, int milestone) {
		return claimedMilestones.getOrDefault(coupleKey, Set.of()).contains(milestone);
	}

	private void claimMilestone(String coupleKey, int milestone) {
		claimedMilestones.computeIfAbsent(coupleKey, ignored -> new HashSet<>()).add(milestone);
	}

	private String getMilestoneStatus(boolean available, boolean claimed) {
		if (claimed) {
			return plugin.langManager().get("anniversary.status.claimed");
		}

		if (available) {
			return plugin.langManager().get("anniversary.status.available");
		}

		return plugin.langManager().get("anniversary.status.locked");
	}

	private long getMarriageDate(UUID playerId) {
		return plugin.dataManager().dataConfig().getLong("marriage-dates." + playerId, 0L);
	}

	private long getDaysMarried(long marriageDate) {
		return Math.max(0L, (System.currentTimeMillis() - marriageDate) / MILLIS_PER_DAY);
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