package com.okbeanok.marriagePlus.hooks;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.Pronouns;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MarriagePlusExpansion extends PlaceholderExpansion {

	private final MarriagePlus plugin;

	public MarriagePlusExpansion(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	@Override
	public @NotNull String getIdentifier() {
		return "marriageplus";
	}

	@Override
	public @NotNull String getAuthor() {
		return "OkBeanOk";
	}

	@Override
	public @NotNull String getVersion() {
		return plugin.getPluginMeta().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public boolean canRegister() {
		return true;
	}

	@Override
	public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
		if (offlinePlayer == null) {
			return "";
		}

		UUID playerId = offlinePlayer.getUniqueId();
		UUID partnerId = plugin.marriageManager().getPartnerId(playerId);

		return switch (params.toLowerCase()) {
			case "is_married" -> String.valueOf(partnerId != null);
			case "status" -> partnerId == null ? "Single" : "Married";
			case "partner" -> partnerName(partnerId);
			case "partner_display" -> partnerDisplayName(playerId, partnerId);
			case "partner_nickname" -> partnerId == null ? "" : plugin.socialManager().getPartnerDisplayName(playerId, partnerId);
			case "days_married" -> String.valueOf(daysMarried(playerId));
			case "anniversary_days" -> String.valueOf(daysMarried(playerId));
			case "level" -> String.valueOf(plugin.marriageXpManager().getLevel(playerId));
			case "xp" -> String.valueOf(plugin.marriageXpManager().getXp(playerId));
			case "xp_required" -> String.valueOf(plugin.marriageXpManager().getXpRequired(playerId));
			case "achievements_unlocked" -> String.valueOf(achievementsUnlocked(playerId));
			case "achievements_total" -> String.valueOf(plugin.achievementManager().definitions().size());
			case "pronouns" -> plugin.pronounManager().getPronouns(playerId).display();
			case "subject" -> plugin.pronounManager().getPronouns(playerId).subject();
			case "object" -> plugin.pronounManager().getPronouns(playerId).object();
			case "possessive" -> plugin.pronounManager().getPronouns(playerId).possessive();
			case "partner_pronouns" -> partnerPronouns(partnerId).display();
			case "partner_subject" -> partnerPronouns(partnerId).subject();
			case "partner_object" -> partnerPronouns(partnerId).object();
			case "partner_possessive" -> partnerPronouns(partnerId).possessive();
			case "title" -> partnerId == null ? "" : plugin.socialManager().getMarriageTitle(playerId, partnerId);
			case "home_set" -> String.valueOf(plugin.homeManager().hasHome(playerId));
			case "backpack_access" -> String.valueOf(plugin.backpackManager().backpackAllowed().contains(playerId));
			case "partner_backpack_access" -> partnerId == null ? "false" : String.valueOf(plugin.backpackManager().backpackAllowed().contains(partnerId));
			case "pvp" -> partnerId == null ? "false" : String.valueOf(plugin.marriageManager().pvpEnabledCouples().contains(plugin.marriageManager().coupleKey(playerId, partnerId)));
			case "chat_toggle" -> String.valueOf(plugin.requestManager().coupleChatToggled().contains(playerId));
			case "requests_enabled" -> String.valueOf(!plugin.requestManager().marriageRequestsDisabled().contains(playerId));
			case "home_count" -> String.valueOf(plugin.homeManager().getHomeCount(playerId));
			case "max_homes" -> playerMaxHomes(offlinePlayer);
			default -> null;
		};
	}

	private int achievementsUnlocked(UUID playerId) {
		UUID partnerId = plugin.marriageManager().getPartnerId(playerId);

		if (partnerId == null) {
			return 0;
		}

		String coupleKey = plugin.marriageManager().coupleKey(playerId, partnerId);
		return plugin.achievementManager().unlockedAchievements().getOrDefault(coupleKey, new java.util.ArrayList<>()).size();
	}

	private String partnerName(UUID partnerId) {
		if (partnerId == null) {
			return "";
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		return partner.getName() == null ? "" : partner.getName();
	}

	private String partnerDisplayName(UUID playerId, UUID partnerId) {
		if (partnerId == null) {
			return "";
		}

		return plugin.socialManager().getPartnerDisplayName(playerId, partnerId);
	}

	private Pronouns partnerPronouns(UUID partnerId) {
		if (partnerId == null) {
			return plugin.pronounManager().getPronouns(new UUID(0L, 0L));
		}

		return plugin.pronounManager().getPronouns(partnerId);
	}

	private long daysMarried(UUID playerId) {
		long date = plugin.dataManager().dataConfig().getLong("marriage-dates." + playerId, 0L);

		if (date <= 0L) {
			return 0L;
		}

		return Math.max(0L, (System.currentTimeMillis() - date) / 86_400_000L);
	}

	private String playerMaxHomes(OfflinePlayer offlinePlayer) {
		if (offlinePlayer instanceof Player player) {
			return String.valueOf(plugin.homeManager().getMaxHomes(player));
		}

		return String.valueOf(plugin.getConfig().getInt("homes.max-homes-default", 3));
	}
}