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
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		if (args.length < 2) {
			player.sendMessage(color("&dYour marriage title is &f" + getMarriageTitle(player.getUniqueId(), partnerId) + "&d."));
			player.sendMessage(color("&7Use &f/marry title <text>&7, &f/marry title on&7, or &f/marry title off&7."));
			return;
		}

		if (args[1].equalsIgnoreCase("off")) {
			marriageTitles.remove(player.getUniqueId());
			plugin.dataManager().saveData();
			player.sendMessage(color("&eYour marriage title was disabled."));
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
			player.sendMessage(color("&cThat title is too long. Max length: &f" + maxLength));
			return;
		}

		marriageTitles.put(player.getUniqueId(), title);
		plugin.dataManager().saveData();

		player.sendMessage(color("&aYour marriage title is now &f" + getMarriageTitle(player.getUniqueId(), partnerId) + "&a."));
	}

	public void nicknameCommand(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		if (args.length < 2) {
			player.sendMessage(color("&dYour partner nickname is &f" + getPartnerDisplayName(player.getUniqueId(), partnerId) + "&d."));
			player.sendMessage(color("&7Use &f/marry nickname <name> &7or &f/marry nickname clear&7."));
			return;
		}

		if (args[1].equalsIgnoreCase("clear")) {
			partnerNicknames.remove(player.getUniqueId());
			plugin.dataManager().saveData();
			player.sendMessage(color("&eYour partner nickname was cleared."));
			return;
		}

		String nickname = String.join(" ", List.of(args).subList(1, args.length));
		int maxLength = plugin.getConfig().getInt("nicknames.max-length", 24);

		if (ChatColor.stripColor(color(nickname)).length() > maxLength) {
			player.sendMessage(color("&cThat nickname is too long. Max length: &f" + maxLength));
			return;
		}

		partnerNicknames.put(player.getUniqueId(), nickname);
		plugin.dataManager().saveData();

		player.sendMessage(color("&aYour partner nickname is now &f" + color(nickname) + "&a."));
	}

	public String getMarriageTitle(UUID playerId, UUID partnerId) {
		String title = marriageTitles.get(playerId);

		if (title == null || title.isBlank()) {
			return "None";
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		String partnerName = partner.getName() == null ? "Partner" : partner.getName();

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
		return partner.getName() == null ? "Partner" : partner.getName();
	}
}