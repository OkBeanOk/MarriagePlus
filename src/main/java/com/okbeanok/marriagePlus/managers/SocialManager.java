package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class SocialManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;

	private final Map<UUID, String> marriageTitles = new HashMap<>();
	private final Map<UUID, String> partnerNicknames = new HashMap<>();

	public SocialManager(MarriagePlus plugin, MarriageManager marriageManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
	}

	public Map<UUID, String> marriageTitles() {
		return marriageTitles;
	}

	public Map<UUID, String> partnerNicknames() {
		return partnerNicknames;
	}

	public void titleCommand(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(player, "title.current", Map.of(
					"%title%", getMarriageTitle(player.getUniqueId(), partnerId)
			));
			plugin.langManager().send(player, "title.usage");
			return;
		}

		if (args[1].equalsIgnoreCase("off")) {
			marriageTitles.remove(player.getUniqueId());
			plugin.dataManager().saveData();
			plugin.langManager().send(player, "title.disabled");
			return;
		}

		String title;

		if (args[1].equalsIgnoreCase("on")) {
			title = plugin.getConfig().getString("titles.default-format", "&d❤ Married to %partner%");
		} else {
			title = String.join(" ", List.of(args).subList(1, args.length));
		}

		int maxLength = plugin.getConfig().getInt("titles.max-length", 32);

		if (ChatColor.stripColor(color(title)).length() > maxLength) {
			plugin.langManager().send(player, "title.too-long", Map.of(
					"%max%", String.valueOf(maxLength)
			));
			return;
		}

		marriageTitles.put(player.getUniqueId(), title);
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "title.set", Map.of(
				"%title%", getMarriageTitle(player.getUniqueId(), partnerId)
		));
	}

	public void nicknameCommand(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(player, "nickname.current", Map.of(
					"%nickname%", getPartnerDisplayName(player.getUniqueId(), partnerId)
			));
			plugin.langManager().send(player, "nickname.usage");
			return;
		}

		if (args[1].equalsIgnoreCase("clear")) {
			partnerNicknames.remove(player.getUniqueId());
			plugin.dataManager().saveData();
			plugin.langManager().send(player, "nickname.cleared");
			return;
		}

		String nickname = String.join(" ", List.of(args).subList(1, args.length));
		int maxLength = plugin.getConfig().getInt("nicknames.max-length", 24);

		if (ChatColor.stripColor(color(nickname)).length() > maxLength) {
			plugin.langManager().send(player, "nickname.too-long", Map.of(
					"%max%", String.valueOf(maxLength)
			));
			return;
		}

		partnerNicknames.put(player.getUniqueId(), nickname);
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "nickname.set", Map.of(
				"%nickname%", color(nickname)
		));
	}
	
	public String getMarriageTitle(UUID playerId, UUID partnerId) {
		String title = marriageTitles.get(playerId);

		if (title == null || title.isBlank()) {
			return plugin.langManager().get("title.none");
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		String partnerName = partner.getName() == null ? plugin.langManager().get("general.partner") : partner.getName();

		return color(title
				.replace("%partner%", partnerName)
				.replace("%partner_nickname%", getPartnerDisplayName(playerId, partnerId)));
	}

	public String getPartnerDisplayName(UUID playerId, UUID partnerId) {
		String nickname = partnerNicknames.get(playerId);

		if (nickname != null && !nickname.isBlank()) {
			return color(nickname);
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		return partner.getName() == null ? plugin.langManager().get("general.partner") : partner.getName();
	}
}