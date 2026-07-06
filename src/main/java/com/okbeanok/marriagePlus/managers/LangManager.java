package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.Map;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class LangManager {

	private final MarriagePlus plugin;
	private File langFile;
	private FileConfiguration lang;

	public LangManager(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	public void setupLangFile() {
		langFile = new File(plugin.getDataFolder(), "lang.yml");

		if (!langFile.exists()) {
			plugin.saveResource("lang.yml", false);
		}

		reloadLang();
	}

	public void reloadLang() {
		lang = YamlConfiguration.loadConfiguration(langFile);
	}

	public String getRaw(String path) {
		return lang.getString(path, "&cMissing language key: &f" + path);
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
}