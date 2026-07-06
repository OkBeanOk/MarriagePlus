package com.okbeanok.marriagePlus.utils;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import static com.okbeanok.marriagePlus.utils.TextUtils.color; 

public class UpdateChecker {

	private static final int SPIGOT_RESOURCE_ID = 136737;
	private static final String DOWNLOAD_URL = "https://www.spigotmc.org/resources/marriageplus.136737/";

	private final MarriagePlus plugin;

	private boolean updateAvailable;
	private String latestVersion = "";
	private String currentVersion = "";

	public UpdateChecker(MarriagePlus plugin) {
		this.plugin = plugin;
		this.currentVersion = plugin.getPluginMeta().getVersion();
	}

	public void checkForUpdates() {
		if (!plugin.getConfig().getBoolean("settings.update-checker.enabled", true)) {
			return;
		}

		CompletableFuture.supplyAsync(() -> fetchLatestVersion(SPIGOT_RESOURCE_ID))
				.thenAccept(version -> Bukkit.getScheduler().runTask(plugin, () -> handleVersionResult(version)));
	}

	private String fetchLatestVersion(int resourceId) {
		try {
			URL url = URI.create("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).toURL();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
				return reader.readLine();
			}
		} catch (Exception exception) {
			plugin.getLogger().warning("Could not check for updates: " + exception.getMessage());
			return null;
		}
	}

	private void handleVersionResult(String version) {
		if (version == null || version.isBlank()) {
			return;
		}

		latestVersion = version.trim();
		updateAvailable = isNewerVersion(latestVersion, currentVersion);

		if (!updateAvailable) {
			plugin.getLogger().info("MarriagePlus is up to date. Current version: " + currentVersion);
			return;
		}

		plugin.getLogger().warning("A new MarriagePlus version is available!");
		plugin.getLogger().warning("Current version: " + currentVersion);
		plugin.getLogger().warning("Latest version: " + latestVersion);
		plugin.getLogger().warning("Download: " + getDownloadUrl());

		notifyOnlineAdmins();
	}

	private void notifyOnlineAdmins() {
		if (!plugin.getConfig().getBoolean("settings.update-checker.notify-admins-on-join", true)) {
			return;
		}

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!player.hasPermission("marriageplus.admin")) {
				continue;
			}

			player.sendMessage(color("&dMarriagePlus &eupdate available!"));
			player.sendMessage(color("&7Current: &f" + currentVersion));
			player.sendMessage(color("&7Latest: &a" + latestVersion));
			player.sendMessage(color("&7Download: &f" + getDownloadUrl()));
		}
	}

	private boolean isNewerVersion(String latest, String current) {
		String[] latestParts = latest.replaceAll("[^0-9.]", "").split("\\.");
		String[] currentParts = current.replaceAll("[^0-9.]", "").split("\\.");

		int maxLength = Math.max(latestParts.length, currentParts.length);

		for (int index = 0; index < maxLength; index++) {
			int latestPart = parseVersionPart(latestParts, index);
			int currentPart = parseVersionPart(currentParts, index);

			if (latestPart > currentPart) {
				return true;
			}

			if (latestPart < currentPart) {
				return false;
			}
		}

		return false;
	}

	private int parseVersionPart(String[] parts, int index) {
		if (index >= parts.length || parts[index].isBlank()) {
			return 0;
		}

		try {
			return Integer.parseInt(parts[index]);
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	public boolean isUpdateAvailable() {
		return updateAvailable;
	}

	public String getLatestVersion() {
		return latestVersion;
	}

	public String getCurrentVersion() {
		return currentVersion;
	}

	public String getDownloadUrl() {
		return DOWNLOAD_URL;
	}
}