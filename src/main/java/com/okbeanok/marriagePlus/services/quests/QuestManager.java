package com.okbeanok.marriagePlus.services.quests;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuestManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final MarriageXpManager marriageXpManager;
	private final Map<String, CoupleQuest> quests = new HashMap<>();
	private final Map<String, Map<String, Integer>> questProgress = new HashMap<>();
	private final Map<String, String> questResetDates = new HashMap<>();

	private int nearbyTaskId = -1;

	public QuestManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			MarriageXpManager marriageXpManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.marriageXpManager = marriageXpManager;
	}

	public Map<String, Map<String, Integer>> questProgress() {
		return questProgress;
	}

	public Map<String, String> questResetDates() {
		return questResetDates;
	}

	public void loadQuestDefinitions() {
		quests.clear();

		ConfigurationSection questsSection = plugin.configs().quests().getConfigurationSection("definitions");

		if (questsSection == null) {
			return;
		}

		for (String id : questsSection.getKeys(false)) {
			String path = "definitions." + id;

			quests.put(id, new CoupleQuest(
					id,
					plugin.configs().quests().getString(path + ".name", id),
					plugin.configs().quests().getString(path + ".description", ""),
					plugin.configs().quests().getString(path + ".type", "CUSTOM").toUpperCase(),
					plugin.configs().quests().getInt(path + ".amount", 1),
					plugin.configs().quests().getInt(path + ".reward-xp", 0)
			));
		}
	}


	public void start() {
		loadQuestDefinitions();

		if (!plugin.configs().quests().getBoolean("enabled", true)) {
			return;
		}

		long intervalTicks = Math.max(20L, plugin.configs().quests().getInt("nearby-check-seconds", 60) * 20L);
		nearbyTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickNearbyQuests, intervalTicks, intervalTicks);
	}

	public void stop() {
		if (nearbyTaskId != -1) {
			Bukkit.getScheduler().cancelTask(nearbyTaskId);
			nearbyTaskId = -1;
		}
	}

	public void questsCommand(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("claim")) {
			claimRewards(player);
			return;
		}

		resetIfNeeded(player.getUniqueId(), partnerId);

		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);

		plugin.langManager().send(player, "quests.header");

		for (CoupleQuest quest : quests.values()) {
			int progress = getProgress(coupleKey, quest.id());

			plugin.langManager().send(player, "quests.line", Map.of(
					"%quest%", quest.name(),
					"%description%", quest.description(),
					"%progress%", String.valueOf(Math.min(progress, quest.amount())),
					"%required%", String.valueOf(quest.amount()),
					"%reward_xp%", String.valueOf(quest.rewardXp())
			));
		}

		plugin.langManager().send(player, "quests.claim-hint");
	}

	public void addProgress(Player player, String questType, int amount) {
		if (!plugin.configs().quests().getBoolean("enabled", true)) {
			return;
		}

		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		resetIfNeeded(player.getUniqueId(), partnerId);

		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);

		for (CoupleQuest quest : quests.values()) {
			if (!quest.type().equalsIgnoreCase(questType)) {
				continue;
			}

			int currentProgress = getProgress(coupleKey, quest.id());

			if (currentProgress >= quest.amount()) {
				continue;
			}

			setProgress(coupleKey, quest.id(), Math.min(quest.amount(), currentProgress + amount));
		}
	}

	private void claimRewards(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		resetIfNeeded(player.getUniqueId(), partnerId);

		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);
		int claimed = 0;
		int totalXp = 0;

		for (CoupleQuest quest : quests.values()) {
			int progress = getProgress(coupleKey, quest.id());

			if (progress < quest.amount()) {
				continue;
			}

			String claimedKey = quest.id() + ":claimed";

			if (getProgress(coupleKey, claimedKey) > 0) {
				continue;
			}

			setProgress(coupleKey, claimedKey, 1);
			claimed++;
			totalXp += quest.rewardXp();
		}

		if (claimed <= 0) {
			plugin.langManager().send(player, "quests.nothing-to-claim");
			return;
		}

		if (totalXp > 0) {
			marriageXpManager.addXp(player.getUniqueId(), partnerId, totalXp);
		}

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "quests.claimed", Map.of(
				"%quests%", String.valueOf(claimed),
				"%xp%", String.valueOf(totalXp)
		));

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner != null) {
			plugin.notificationManager().notifyPartner(player, "quest-claimed", Map.of(
					"%quests%", String.valueOf(claimed),
					"%xp%", String.valueOf(totalXp)
			));
		}
	}

	private void tickNearbyQuests() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			Player partner = marriageManager.getOnlinePartnerSilent(player);

			if (partner == null) {
				continue;
			}

			if (!player.getWorld().equals(partner.getWorld())) {
				continue;
			}

			double radius = plugin.configs().quests().getDouble("nearby-radius", 32.0D);

			if (player.getLocation().distanceSquared(partner.getLocation()) > radius * radius) {
				continue;
			}

			addProgress(player, "NEAR_PARTNER_MINUTES", plugin.configs().quests().getInt("nearby-check-seconds", 60) / 60);
		}
	}

	private void resetIfNeeded(UUID firstId, UUID secondId) {
		String coupleKey = marriageManager.coupleKey(firstId, secondId);
		String today = LocalDate.now().toString();

		if (today.equals(questResetDates.get(coupleKey))) {
			return;
		}

		questProgress.put(coupleKey, new HashMap<>());
		questResetDates.put(coupleKey, today);
		plugin.dataManager().saveData();
	}

	private int getProgress(String coupleKey, String questId) {
		return questProgress.getOrDefault(coupleKey, Map.of()).getOrDefault(questId, 0);
	}

	private void setProgress(String coupleKey, String questId, int progress) {
		questProgress.computeIfAbsent(coupleKey, ignored -> new HashMap<>()).put(questId, progress);
	}
}