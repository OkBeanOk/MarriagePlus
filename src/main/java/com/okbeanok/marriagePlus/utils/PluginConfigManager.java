package com.okbeanok.marriagePlus.utils;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class PluginConfigManager {

	private final MarriagePlus plugin;

	private FileConfiguration storageConfig;
	private FileConfiguration actionsConfig;
	private FileConfiguration achievementsConfig;
	private FileConfiguration questsConfig;
	private FileConfiguration buffsConfig;
	private FileConfiguration profilesConfig;
	private FileConfiguration mailConfig;
	private FileConfiguration anniversariesConfig;
	private FileConfiguration loveNotesConfig;
	private FileConfiguration ringsConfig;
	private FileConfiguration guiConfig;
	private FileConfiguration ceremoniesConfig;
	private FileConfiguration leaderboardsConfig;
	private FileConfiguration notificationsConfig;
	private FileConfiguration familiesConfig;

	public PluginConfigManager(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	public void setup() {
		storageConfig = loadConfig("configs/storage.yml");
		actionsConfig = loadConfig("configs/actions.yml");
		achievementsConfig = loadConfig("configs/achievements.yml");
		questsConfig = loadConfig("configs/quests.yml");
		buffsConfig = loadConfig("configs/buffs.yml");
		profilesConfig = loadConfig("configs/profiles.yml");
		mailConfig = loadConfig("configs/mail.yml");
		anniversariesConfig = loadConfig("configs/anniversaries.yml");
		loveNotesConfig = loadConfig("configs/love-notes.yml");
		ringsConfig = loadConfig("configs/rings.yml");
		guiConfig = loadConfig("configs/gui.yml");
		leaderboardsConfig = loadConfig("configs/leaderboards.yml");
		ceremoniesConfig = loadConfig("configs/ceremonies.yml");
		notificationsConfig = loadConfig("configs/notifications.yml");
		familiesConfig = loadConfig("configs/families.yml");
	}

	public void reload() {
		plugin.reloadConfig();
		setup();
	}

	private FileConfiguration loadConfig(String fileName) {
		File file = new File(plugin.getDataFolder(), fileName);

		if (!file.exists()) {
			File parent = file.getParentFile();

			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}

			plugin.saveResource(fileName, false);
		}

		return YamlConfiguration.loadConfiguration(file);
	}

	public FileConfiguration storage() {
		return storageConfig;
	}

	public FileConfiguration actions() {
		return actionsConfig;
	}

	public FileConfiguration achievements() {
		return achievementsConfig;
	}

	public FileConfiguration quests() {
		return questsConfig;
	}

	public FileConfiguration buffs() {
		return buffsConfig;
	}

	public FileConfiguration profiles() {
		return profilesConfig;
	}

	public FileConfiguration mail() {
		return mailConfig;
	}

	public FileConfiguration notifications() {
		return notificationsConfig;
	}

	public FileConfiguration anniversaries() {
		return anniversariesConfig;
	}

	public FileConfiguration loveNotes() {
		return loveNotesConfig;
	}

	public FileConfiguration gui() {
		return guiConfig;
	}

	public FileConfiguration rings() {
		return ringsConfig;
	}

	public FileConfiguration ceremonies() {
		return ceremoniesConfig;
	}

	public FileConfiguration leaderboards() {
		return leaderboardsConfig;
	}

	public FileConfiguration families() {
		return familiesConfig;
	}
}