package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

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
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		String homeName = args.length >= 2 ? normalizeHomeName(args[1]) : DEFAULT_HOME_NAME;

		if (!isValidHomeName(homeName)) {
			player.sendMessage(color("&cHome names can only use letters, numbers, underscores, and hyphens."));
			return;
		}

		int maxNameLength = plugin.getConfig().getInt("homes.max-name-length", 16);

		if (homeName.length() > maxNameLength) {
			player.sendMessage(color("&cThat home name is too long. Max length: &f" + maxNameLength));
			return;
		}

		if (!hasHome(player.getUniqueId(), homeName) && getHomeCount(player.getUniqueId()) >= getMaxHomes(player)) {
			player.sendMessage(color("&cYou have reached your couple home limit of &f" + getMaxHomes(player) + "&c."));
			return;
		}

		setHomeFor(player.getUniqueId(), homeName, player.getLocation());
		setHomeFor(partnerId, homeName, player.getLocation());

		plugin.dataManager().saveData();

		player.sendMessage(color("&aCouple home &f" + homeName + " &awas set."));
	}

	public void goHome(Player player, String[] args) {
		if (cooldownManager.isOnCooldown(player, "home")) {
			return;
		}

		String homeName = args.length >= 2 ? normalizeHomeName(args[1]) : DEFAULT_HOME_NAME;
		Location home = getHome(player.getUniqueId(), homeName);

		if (home == null) {
			player.sendMessage(color("&cYou do not have a couple home named &f" + homeName + "&c."));
			return;
		}

		cooldownManager.setCooldown(player, "home", plugin.getConfig().getInt("settings.cooldowns.home-seconds", 30));

		player.teleport(home);
		player.sendMessage(color("&dTeleported to couple home &f" + homeName + "&d."));
	}

	public void listHomes(Player player) {
		if (!marriageManager.isMarried(player.getUniqueId())) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		Map<String, Location> playerHomes = homes.get(player.getUniqueId());

		player.sendMessage(color("&dCouple Homes:"));

		if (playerHomes == null || playerHomes.isEmpty()) {
			player.sendMessage(color("&7You do not have any couple homes set."));
			return;
		}

		for (String homeName : playerHomes.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
			player.sendMessage(color("&f- &d" + homeName));
		}

		player.sendMessage(color("&7Homes: &f" + playerHomes.size() + "&7/&f" + getMaxHomes(player)));
	}

	public void deleteHome(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		if (args.length < 2) {
			player.sendMessage(color("&cUsage: /marry delhome <name>"));
			return;
		}

		String homeName = normalizeHomeName(args[1]);

		if (!hasHome(player.getUniqueId(), homeName)) {
			player.sendMessage(color("&cYou do not have a couple home named &f" + homeName + "&c."));
			return;
		}

		removeHome(player.getUniqueId(), homeName);
		removeHome(partnerId, homeName);

		plugin.dataManager().saveData();

		player.sendMessage(color("&eDeleted couple home &f" + homeName + "&e."));
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