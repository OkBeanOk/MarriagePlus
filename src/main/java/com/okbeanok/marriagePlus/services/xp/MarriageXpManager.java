package com.okbeanok.marriagePlus.services.xp;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class MarriageXpManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final Map<String, Integer> coupleXp = new HashMap<>();

	public MarriageXpManager(MarriagePlus plugin, MarriageManager marriageManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
	}

	public Map<String, Integer> coupleXp() {
		return coupleXp;
	}

	public void levelCommand(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);
		int xp = getXp(coupleKey);
		int level = getLevelFromXp(xp);
		int required = getXpRequiredForNextLevel(level);
		int currentLevelStart = getTotalXpRequiredForLevel(level);
		int progress = Math.max(0, xp - currentLevelStart);

		plugin.langManager().send(player, "xp.header");
		plugin.langManager().send(player, "xp.level", Map.of(
				"%level%", String.valueOf(level)
		));
		plugin.langManager().send(player, "xp.xp", Map.of(
				"%xp%", String.valueOf(xp)
		));
		plugin.langManager().send(player, "xp.progress", Map.of(
				"%progress%", String.valueOf(progress),
				"%required%", String.valueOf(required)
		));
	}

	public void xpCommand(Player player) {
		levelCommand(player);
	}

	public void addXp(Player player, String reason) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		int amount = plugin.getConfig().getInt("xp.sources." + reason, 0);

		if (amount <= 0) {
			return;
		}

		addXp(player.getUniqueId(), partnerId, amount);
	}

	public void addXp(UUID firstId, UUID secondId, int amount) {
		if (amount <= 0) {
			return;
		}

		String coupleKey = marriageManager.coupleKey(firstId, secondId);
		int oldXp = getXp(coupleKey);
		int oldLevel = getLevelFromXp(oldXp);
		int newXp = oldXp + amount;
		int newLevel = getLevelFromXp(newXp);

		coupleXp.put(coupleKey, newXp);
		plugin.dataManager().saveData();

		if (newLevel > oldLevel) {
			notifyLevelUp(firstId, secondId, newLevel);
		}
	}

	public int getXp(UUID playerId) {
		UUID partnerId = marriageManager.getPartnerId(playerId);

		if (partnerId == null) {
			return 0;
		}

		return getXp(marriageManager.coupleKey(playerId, partnerId));
	}

	public int getXp(String coupleKey) {
		return coupleXp.getOrDefault(coupleKey, 0);
	}

	public int getLevel(UUID playerId) {
		return getLevelFromXp(getXp(playerId));
	}

	public int getXpRequired(UUID playerId) {
		int level = getLevel(playerId);
		return getXpRequiredForNextLevel(level);
	}

	public int getLevelFromXp(int xp) {
		int level = 1;

		while (xp >= getTotalXpRequiredForLevel(level + 1)) {
			level++;
		}

		return level;
	}

	public int getXpRequiredForNextLevel(int level) {
		int base = plugin.getConfig().getInt("xp.level-base-required", 100);
		double multiplier = plugin.getConfig().getDouble("xp.level-multiplier", 1.25D);

		return Math.max(1, (int) Math.round(base * Math.pow(multiplier, Math.max(0, level - 1))));
	}

	public int getTotalXpRequiredForLevel(int level) {
		if (level <= 1) {
			return 0;
		}

		int total = 0;

		for (int currentLevel = 1; currentLevel < level; currentLevel++) {
			total += getXpRequiredForNextLevel(currentLevel);
		}

		return total;
	}

	private void notifyLevelUp(UUID firstId, UUID secondId, int newLevel) {
		if (!plugin.getConfig().getBoolean("xp.broadcast-level-ups", true)) {
			Player first = Bukkit.getPlayer(firstId);
			Player second = Bukkit.getPlayer(secondId);

			if (first != null) {
				plugin.langManager().send(first, "xp.level-up", Map.of(
						"%level%", String.valueOf(newLevel)
				));
			}

			if (second != null) {
				plugin.langManager().send(second, "xp.level-up", Map.of(
						"%level%", String.valueOf(newLevel)
				));
			}

			return;
		}

		String firstName = Bukkit.getOfflinePlayer(firstId).getName();
		String secondName = Bukkit.getOfflinePlayer(secondId).getName();

		if (firstName == null) {
			firstName = plugin.langManager().get("general.unknown");
		}

		if (secondName == null) {
			secondName = plugin.langManager().get("general.unknown");
		}

		String message = plugin.getConfig().getString(
				"xp.level-up-broadcast",
				"&d❤ &f%player% &7& &f%partner% &dreached marriage level &f%level%&d!"
		);

		message = message
				.replace("%player%", firstName)
				.replace("%partner%", secondName)
				.replace("%level%", String.valueOf(newLevel));

		Bukkit.broadcastMessage(color(message));
	}
}