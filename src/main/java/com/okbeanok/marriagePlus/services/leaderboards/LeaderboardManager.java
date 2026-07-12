package com.okbeanok.marriagePlus.services.leaderboards;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.LeaderboardEntry;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.achievement.AchievementManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LeaderboardManager {

	private static final long MILLIS_PER_DAY = 86_400_000L;

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final MarriageXpManager marriageXpManager;
	private final AchievementManager achievementManager;

	public LeaderboardManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			MarriageXpManager marriageXpManager,
			AchievementManager achievementManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.marriageXpManager = marriageXpManager;
		this.achievementManager = achievementManager;
	}

	public void leaderboardCommand(Player player, String[] args) {
		if (!plugin.configs().leaderboards().getBoolean("enabled", true)) {
			plugin.langManager().send(player, "leaderboards.disabled");
			return;
		}

		String type = args.length >= 2
				? args[1].toLowerCase(Locale.ROOT)
				: plugin.configs().leaderboards().getString("default-type", "level").toLowerCase(Locale.ROOT);

		if (!Set.of("level", "xp", "longest", "achievements").contains(type)) {
			plugin.langManager().send(player, "leaderboards.invalid-type");
			return;
		}

		if (!plugin.configs().leaderboards().getBoolean("types." + type + ".enabled", true)) {
			plugin.langManager().send(player, "leaderboards.type-disabled");
			return;
		}

		showLeaderboard(player, type);
	}

	private void showLeaderboard(Player player, String type) {
		List<LeaderboardEntry> entries = switch (type) {
			case "xp" -> getXpEntries();
			case "longest" -> getLongestMarriageEntries();
			case "achievements" -> getAchievementEntries();
			default -> getLevelEntries();
		};

		int maxResults = plugin.configs().leaderboards().getInt("max-results", 10);

		if (entries.size() > maxResults) {
			entries = entries.subList(0, maxResults);
		}

		String title = plugin.configs().leaderboards().getString("types." + type + ".title", "&d❤ Marriage Leaderboard");
		String header = plugin.configs().leaderboards().getString("format.header", "&d&m-----&r %title% &d&m-----");

		plugin.langManager().sendRaw(player, header.replace("%title%", title));

		if (entries.isEmpty()) {
			plugin.langManager().send(player, "leaderboards.empty");
			return;
		}

		String lineFormat = plugin.configs().leaderboards().getString(
				"format.line",
				"&d#%rank% &f%player% &7+ &f%partner% &8- &d%value%"
		);

		for (int index = 0; index < entries.size(); index++) {
			LeaderboardEntry entry = entries.get(index);

			plugin.langManager().sendRaw(player, lineFormat
					.replace("%rank%", String.valueOf(index + 1))
					.replace("%player%", entry.firstName())
					.replace("%partner%", entry.secondName())
					.replace("%value%", entry.displayValue()));
		}
	}

	private List<LeaderboardEntry> getLevelEntries() {
		List<LeaderboardEntry> entries = new ArrayList<>();

		for (Couple couple : uniqueCouples()) {
			int level = marriageXpManager.getLevel(couple.firstId());

			entries.add(new LeaderboardEntry(
					couple.firstId(),
					couple.secondId(),
					safeName(Bukkit.getOfflinePlayer(couple.firstId())),
					safeName(Bukkit.getOfflinePlayer(couple.secondId())),
					level,
					"Level " + level
			));
		}

		entries.sort(Comparator.comparingLong(LeaderboardEntry::value).reversed());
		return entries;
	}

	private List<LeaderboardEntry> getXpEntries() {
		List<LeaderboardEntry> entries = new ArrayList<>();

		for (Couple couple : uniqueCouples()) {
			int xp = marriageXpManager.getXp(couple.firstId());

			entries.add(new LeaderboardEntry(
					couple.firstId(),
					couple.secondId(),
					safeName(Bukkit.getOfflinePlayer(couple.firstId())),
					safeName(Bukkit.getOfflinePlayer(couple.secondId())),
					xp,
					xp + " XP"
			));
		}

		entries.sort(Comparator.comparingLong(LeaderboardEntry::value).reversed());
		return entries;
	}

	private List<LeaderboardEntry> getLongestMarriageEntries() {
		List<LeaderboardEntry> entries = new ArrayList<>();

		for (Couple couple : uniqueCouples()) {
			long marriageDate = plugin.dataManager().dataConfig().getLong("marriage-dates." + couple.firstId(), 0L);

			if (marriageDate <= 0L) {
				marriageDate = plugin.dataManager().dataConfig().getLong("marriage-dates." + couple.secondId(), 0L);
			}

			if (marriageDate <= 0L) {
				continue;
			}

			long days = Math.max(0L, (System.currentTimeMillis() - marriageDate) / MILLIS_PER_DAY);

			entries.add(new LeaderboardEntry(
					couple.firstId(),
					couple.secondId(),
					safeName(Bukkit.getOfflinePlayer(couple.firstId())),
					safeName(Bukkit.getOfflinePlayer(couple.secondId())),
					days,
					days + " day(s)"
			));
		}

		entries.sort(Comparator.comparingLong(LeaderboardEntry::value).reversed());
		return entries;
	}

	private List<LeaderboardEntry> getAchievementEntries() {
		List<LeaderboardEntry> entries = new ArrayList<>();

		for (Couple couple : uniqueCouples()) {
			String coupleKey = marriageManager.coupleKey(couple.firstId(), couple.secondId());
			int unlocked = achievementManager.unlockedAchievements().getOrDefault(coupleKey, List.of()).size();

			entries.add(new LeaderboardEntry(
					couple.firstId(),
					couple.secondId(),
					safeName(Bukkit.getOfflinePlayer(couple.firstId())),
					safeName(Bukkit.getOfflinePlayer(couple.secondId())),
					unlocked,
					unlocked + " achievement(s)"
			));
		}

		entries.sort(Comparator.comparingLong(LeaderboardEntry::value).reversed());
		return entries;
	}

	private List<Couple> uniqueCouples() {
		List<Couple> couples = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (Map.Entry<UUID, UUID> entry : marriageManager.partners().entrySet()) {
			UUID firstId = entry.getKey();
			UUID secondId = entry.getValue();
			String coupleKey = marriageManager.coupleKey(firstId, secondId);

			if (seen.contains(coupleKey)) {
				continue;
			}

			seen.add(coupleKey);
			couples.add(new Couple(firstId, secondId));
		}

		return couples;
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plugin.langManager().get("general.unknown") : player.getName();
	}

	private record Couple(UUID firstId, UUID secondId) {
	}
}