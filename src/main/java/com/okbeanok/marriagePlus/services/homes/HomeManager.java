package com.okbeanok.marriagePlus.services.homes;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.CooldownManager;
import com.okbeanok.marriagePlus.services.MarriageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.legacy;

public class HomeManager {

	public static final String DEFAULT_HOME_NAME = "main";

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final CooldownManager cooldownManager;

	private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
	private final Map<UUID, String> defaultHomes = new HashMap<>();
	private final Map<UUID, PendingHomeDelete> deleteConfirmations = new HashMap<>();

	public HomeManager(MarriagePlus plugin, MarriageManager marriageManager, CooldownManager cooldownManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.cooldownManager = cooldownManager;
	}

	public Map<UUID, Map<String, Location>> homes() {
		return homes;
	}

	public Map<UUID, String> defaultHomes() {
		return defaultHomes;
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

		String homeName = args.length >= 2 ? normalizeHomeName(args[1]) : getDefaultHomeName(player.getUniqueId());
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
			sendClickableHomeLine(player, homeName);
		}

		plugin.langManager().send(player, "home.count", Map.of(
				"%homes%", String.valueOf(playerHomes.size()),
				"%max%", String.valueOf(getMaxHomes(player))
		));
	}

	private void sendClickableHomeLine(Player player, String homeName) {
		Component homeLine = legacy(plugin.langManager().get("home.clickable-line-prefix"))
				.append(legacy(plugin.langManager().get("home.clickable-name", Map.of(
						"%home%", homeName
				)))
						.clickEvent(ClickEvent.runCommand("/marry home " + homeName))
						.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("home.clickable-hover", Map.of(
								"%home%", homeName
						))))))
				.append(legacy(plugin.langManager().get("home.clickable-delete", Map.of(
						"%home%", homeName
				)))
						.clickEvent(ClickEvent.suggestCommand("/marry delhome " + homeName))
						.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("home.clickable-delete-hover", Map.of(
								"%home%", homeName
						))))));

		player.sendMessage(homeLine);
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

		if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
			int seconds = plugin.getConfig().getInt("homes.delete-confirm-seconds", 30);
			deleteConfirmations.put(player.getUniqueId(), new PendingHomeDelete(
					homeName,
					System.currentTimeMillis() + seconds * 1000L
			));

			plugin.langManager().send(player, "home.delete-confirm", Map.of(
					"%home%", homeName,
					"%seconds%", String.valueOf(seconds)
			));
			return;
		}

		PendingHomeDelete pendingDelete = deleteConfirmations.get(player.getUniqueId());

		if (pendingDelete == null || !pendingDelete.homeName().equalsIgnoreCase(homeName)) {
			plugin.langManager().send(player, "home.delete-confirm-missing");
			return;
		}

		if (System.currentTimeMillis() > pendingDelete.expiresAt()) {
			deleteConfirmations.remove(player.getUniqueId());
			plugin.langManager().send(player, "home.delete-confirm-expired");
			return;
		}

		deleteConfirmations.remove(player.getUniqueId());
		removeHome(player.getUniqueId(), homeName);
		removeHome(partnerId, homeName);

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "home.deleted", Map.of(
				"%home%", homeName
		));
	}

	public void renameHome(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length < 3) {
			plugin.langManager().send(player, "home.rename-usage");
			return;
		}

		String oldName = normalizeHomeName(args[1]);
		String newName = normalizeHomeName(args[2]);

		if (!hasHome(player.getUniqueId(), oldName)) {
			plugin.langManager().send(player, "home.not-set", Map.of(
					"%home%", oldName
			));
			return;
		}

		if (!isValidHomeName(newName)) {
			plugin.langManager().send(player, "home.invalid-name");
			return;
		}

		int maxNameLength = plugin.getConfig().getInt("homes.max-name-length", 16);

		if (newName.length() > maxNameLength) {
			plugin.langManager().send(player, "home.name-too-long", Map.of(
					"%max%", String.valueOf(maxNameLength)
			));
			return;
		}

		if (oldName.equalsIgnoreCase(newName)) {
			plugin.langManager().send(player, "home.rename-same-name");
			return;
		}

		if (hasHome(player.getUniqueId(), newName)) {
			plugin.langManager().send(player, "home.already-set", Map.of(
					"%home%", newName
			));
			return;
		}

		Location location = getHome(player.getUniqueId(), oldName);

		removeHome(player.getUniqueId(), oldName);
		removeHome(partnerId, oldName);
		setHomeFor(player.getUniqueId(), newName, location);
		setHomeFor(partnerId, newName, location);

		updateDefaultHomeAfterRename(player.getUniqueId(), oldName, newName);
		updateDefaultHomeAfterRename(partnerId, oldName, newName);

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "home.renamed", Map.of(
				"%old_home%", oldName,
				"%new_home%", newName
		));
	}

	public void setDefaultHome(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(player, "home.default-usage");
			return;
		}

		String homeName = normalizeHomeName(args[1]);

		if (!hasHome(player.getUniqueId(), homeName)) {
			plugin.langManager().send(player, "home.not-set", Map.of(
					"%home%", homeName
			));
			return;
		}

		defaultHomes.put(player.getUniqueId(), homeName);
		defaultHomes.put(partnerId, homeName);

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "home.default-set", Map.of(
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

		String normalizedHomeName = normalizeHomeName(homeName);
		playerHomes.remove(normalizedHomeName);

		if (getDefaultHomeName(playerId).equalsIgnoreCase(normalizedHomeName)) {
			defaultHomes.remove(playerId);
		}

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

	public String getDefaultHomeName(UUID playerId) {
		return defaultHomes.getOrDefault(playerId, DEFAULT_HOME_NAME);
	}

	public void setDefaultHomeFor(UUID playerId, String homeName) {
		defaultHomes.put(playerId, normalizeHomeName(homeName));
	}

	private boolean isValidHomeName(String homeName) {
		return homeName.matches("[a-z0-9_-]+");
	}

	private void updateDefaultHomeAfterRename(UUID playerId, String oldName, String newName) {
		if (getDefaultHomeName(playerId).equalsIgnoreCase(oldName)) {
			defaultHomes.put(playerId, normalizeHomeName(newName));
		}
	}

	private record PendingHomeDelete(String homeName, long expiresAt) {
	}
}