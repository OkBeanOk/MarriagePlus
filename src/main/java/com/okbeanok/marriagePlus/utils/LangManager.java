package com.okbeanok.marriagePlus.utils;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class LangManager {

	private static final String DEFAULT_LANGUAGE = "EN_US";
	private static final String LANGUAGE_FOLDER = "langs";

	private final MarriagePlus plugin;

	private File langFolder;
	private File langFile;
	private File defaultLangFile;

	private FileConfiguration lang;
	private FileConfiguration defaultLang;

	private String activeLanguage = DEFAULT_LANGUAGE;

	public LangManager(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	public void setupLangFile() {
		langFolder = new File(plugin.getDataFolder(), LANGUAGE_FOLDER);

		if (!langFolder.exists() && !langFolder.mkdirs()) {
			plugin.getLogger().warning("Could not create langs folder.");
		}

		defaultLangFile = new File(langFolder, DEFAULT_LANGUAGE + ".yml");

		if (!defaultLangFile.exists()) {
			saveDefaultLanguageFile();
		}

		migrateOldLangFile();
		reloadLang();
	}

	public void reloadLang() {
		activeLanguage = normalizeLanguage(plugin.getConfig().getString("language", DEFAULT_LANGUAGE));
		langFile = new File(langFolder, activeLanguage + ".yml");

		if (!langFile.exists()) {
			plugin.getLogger().warning("Language file " + activeLanguage + ".yml was not found. Falling back to " + DEFAULT_LANGUAGE + ".yml.");
			activeLanguage = DEFAULT_LANGUAGE;
			langFile = defaultLangFile;
		}

		defaultLang = YamlConfiguration.loadConfiguration(defaultLangFile);
		lang = YamlConfiguration.loadConfiguration(langFile);

		plugin.getLogger().info("Loaded language file: " + activeLanguage + ".yml");
	}

	private void saveDefaultLanguageFile() {
		try {
			plugin.saveResource(LANGUAGE_FOLDER + "/" + DEFAULT_LANGUAGE + ".yml", false);
		} catch (IllegalArgumentException exception) {
			plugin.getLogger().warning("Default language resource langs/" + DEFAULT_LANGUAGE + ".yml is missing from the jar.");
		}
	}

	private void migrateOldLangFile() {
		File oldLangFile = new File(langFolder, "lang.yml");

		if (!oldLangFile.exists() || defaultLangFile.exists()) {
			return;
		}

		if (oldLangFile.renameTo(defaultLangFile)) {
			plugin.getLogger().info("Migrated langs/lang.yml to langs/" + DEFAULT_LANGUAGE + ".yml.");
			return;
		}

		plugin.getLogger().warning("Could not migrate langs/lang.yml to langs/" + DEFAULT_LANGUAGE + ".yml.");
	}

	private String normalizeLanguage(String language) {
		if (language == null || language.isBlank()) {
			return DEFAULT_LANGUAGE;
		}

		return language
				.trim()
				.replace('-', '_')
				.toUpperCase(Locale.ROOT);
	}

	public void sendRaw(CommandSender sender, String message) {
		sender.sendMessage(color(message));
	}

	public String getRaw(String path) {
		if (lang != null && lang.contains(path)) {
			return lang.getString(path, missing(path));
		}

		if (defaultLang != null && defaultLang.contains(path)) {
			return defaultLang.getString(path, missing(path));
		}

		return missing(path);
	}

	public String get(String path) {
		return color(getRaw(path));
	}

	public String get(String path, Map<String, String> placeholders) {
		String message = getRaw(path);

		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			message = message.replace(entry.getKey(), entry.getValue());
		}

		return color(message);
	}

	public void send(CommandSender sender, String path) {
		sender.sendMessage(get(path));
	}

	public void send(CommandSender sender, String path, Map<String, String> placeholders) {
		sender.sendMessage(get(path, placeholders));
	}

	public String activeLanguage() {
		return activeLanguage;
	}

	private String missing(String path) {
		return "&cMissing language key: &f" + path;
	}
}