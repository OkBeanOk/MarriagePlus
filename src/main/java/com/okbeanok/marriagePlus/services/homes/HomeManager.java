package com.okbeanok.marriagePlus.services.homes;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.CooldownManager;
import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

	public static final String DEFAULT_HOME_NAME = "main";

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final CooldownManager cooldownManager;

	private final Map<UUID, Map<String, Location>> homes = new HashMap<>();

	public HomeManager(MarriagePlus plugin, MarriageManager marriageManager, CooldownManager cooldownManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.cooldownManager = cooldownManager;
	}

	public Map<UUID, Map<String, Location>> homes() {
		return homes;
	}

	public void setHome(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		String homeName = args.length >= 2 ? normalizeHomeName(args[1]) : DEFAULT_HOME_NAME;

		if (!isValidHomeName(homeName)) {
			plugin.langManager().send(player, "home.invalid-name");
			return;
		}

		int maxNameLength = plugin.getConfig().getInt("homes.max-name-length", 16);

		if (homeName.length() > maxNameLength) {
			plugin.langManager().send(player, "home.name-too-long", Map.of(
					"%max%", String.valueOf(maxNameLength)
			));
			return;
		}

		if (!hasHome(player.getUniqueId(), homeName) && getHomeCount(player.getUniqueId()) >= getMaxHomes(player)) {
			plugin.langManager().send(player, "home.limit-reached", Map.of(
					"%max%", String.valueOf(getMaxHomes(player))
			));
			return;
		}

		setHomeFor(player.getUniqueId(), homeName, player.getLocation());
		setHomeFor(partnerId, homeName, player.getLocation());

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "home.set", Map.of(
				"%home%", homeName
		));
		plugin.marriageXpManager().addXp(player, "homes");
		plugin.achievementManager().unlockByTrigger(player, "home");
	}

	public void goHome(Player player, String[] args) {
		if (cooldownManager.isOnCooldown(player, "home")) {
			return;
		}

		String homeName = args.length >= 2 ? normalizeHomeName(args[1]) : DEFAULT_HOME_NAME;
		Location home = getHome(player.getUniqueId(), homeName);

		if (home == null) {
			plugin.langManager().send(player, "home.not-set", Map.of(
					"%home%", homeName
			));
			return;
		}

		cooldownManager.setCooldown(player, "home", plugin.getConfig().getInt("settings.cooldowns.home-seconds", 30));

		player.teleport(home);
		plugin.langManager().send(player, "home.teleporting", Map.of(
				"%home%", homeName
		));
	}

	public void listHomes(Player player) {
		if (!marriageManager.isMarried(player.getUniqueId())) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		Map<String, Location> playerHomes = homes.get(player.getUniqueId());

		plugin.langManager().send(player, "home.header");

		if (playerHomes == null || playerHomes.isEmpty()) {
			plugin.langManager().send(player, "home.none");
			return;
		}

		for (String homeName : playerHomes.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
			plugin.langManager().send(player, "home.line", Map.of(
					"%home%", homeName
			));
		}

		plugin.langManager().send(player, "home.count", Map.of(
				"%homes%", String.valueOf(playerHomes.size()),
				"%max%", String.valueOf(getMaxHomes(player))
		));
	}

	public void deleteHome(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(player, "home.delete-usage");
			return;
		}

		String homeName = normalizeHomeName(args[1]);

		if (!hasHome(player.getUniqueId(), homeName)) {
			plugin.langManager().send(player, "home.not-set", Map.of(
					"%home%", homeName
			));
			return;
		}

		removeHome(player.getUniqueId(), homeName);
		removeHome(partnerId, homeName);

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "home.deleted", Map.of(
				"%home%", homeName
		));
	}

	public void setHomeFor(UUID playerId, String homeName, Location location) {
		homes.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(normalizeHomeName(homeName), location);
	}

	public void removeHome(UUID playerId, String homeName) {
		Map<String, Location> playerHomes = homes.get(playerId);

		if (playerHomes == null) {
			return;
		}

		playerHomes.remove(normalizeHomeName(homeName));

		if (playerHomes.isEmpty()) {
			homes.remove(playerId);
		}
	}

	public Location getHome(UUID playerId, String homeName) {
		Map<String, Location> playerHomes = homes.get(playerId);

		if (playerHomes == null) {
			return null;
		}

		return playerHomes.get(normalizeHomeName(homeName));
	}

	public boolean hasHome(UUID playerId) {
		Map<String, Location> playerHomes = homes.get(playerId);
		return playerHomes != null && !playerHomes.isEmpty();
	}

	public boolean hasHome(UUID playerId, String homeName) {
		Map<String, Location> playerHomes = homes.get(playerId);
		return playerHomes != null && playerHomes.containsKey(normalizeHomeName(homeName));
	}

	public int getHomeCount(UUID playerId) {
		Map<String, Location> playerHomes = homes.get(playerId);
		return playerHomes == null ? 0 : playerHomes.size();
	}

	public int getMaxHomes(Player player) {
		int maxHomes = plugin.getConfig().getInt("homes.max-homes-default", 3);

		for (int amount = 100; amount >= 1; amount--) {
			if (player.hasPermission("marriageplus.homes." + amount)) {
				return amount;
			}
		}

		return maxHomes;
	}

	public String normalizeHomeName(String input) {
		if (input == null || input.isBlank()) {
			return DEFAULT_HOME_NAME;
		}

		return input.toLowerCase(Locale.ROOT);
	}

	private boolean isValidHomeName(String homeName) {
		return homeName.matches("[a-z0-9_-]+");
	}
}