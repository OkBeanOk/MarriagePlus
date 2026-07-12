package com.okbeanok.marriagePlus.services.profiles;

import com.okbeanok.marriagePlus.MarriagePlus;

import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.pronouns.PronounManager;
import com.okbeanok.marriagePlus.services.social.SocialManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class ProfileManager {

	private static final long MILLIS_PER_DAY = 86_400_000L;

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final MarriageXpManager marriageXpManager;
	private final PronounManager pronounManager;
	private final SocialManager socialManager;
	private final Map<UUID, String> partnerStatuses = new HashMap<>();

	public ProfileManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			MarriageXpManager marriageXpManager,
			PronounManager pronounManager,
			SocialManager socialManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.marriageXpManager = marriageXpManager;
		this.pronounManager = pronounManager;
		this.socialManager = socialManager;
	}

	public Map<UUID, String> partnerStatuses() {
		return partnerStatuses;
	}

	public String getStatus(UUID playerId) {
		return partnerStatuses.getOrDefault(
				playerId,
				plugin.configs().profiles().getString("default-status", "No status set.")
		);
	}

	public void setStatus(Player player, String[] args) {
		if (args.length < 3) {
			plugin.langManager().send(player, "profile.status-usage");
			return;
		}

		String status = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();

		if (status.isBlank()) {
			plugin.langManager().send(player, "profile.status-usage");
			return;
		}

		int maxLength = plugin.configs().profiles().getInt("status.max-length", 64);

		if (status.length() > maxLength) {
			plugin.langManager().send(player, "profile.status-too-long", Map.of(
					"%max%", String.valueOf(maxLength)
			));
			return;
		}

		partnerStatuses.put(player.getUniqueId(), status);
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "profile.status-set", Map.of(
				"%status%", status
		));
	}

	public void clearStatus(Player player) {
		if (!partnerStatuses.containsKey(player.getUniqueId())) {
			plugin.langManager().send(player, "profile.status-cleared");
			return;
		}

		partnerStatuses.remove(player.getUniqueId());
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "profile.status-cleared");
	}

	public void profileCommand(Player viewer, String[] args) {
		OfflinePlayer target = args.length >= 2
				? Bukkit.getOfflinePlayer(args[1])
				: viewer;

		UUID targetId = target.getUniqueId();
		UUID partnerId = marriageManager.getPartnerId(targetId);

		plugin.langManager().send(viewer, "profile.header");
		plugin.langManager().send(viewer, "profile.player", Map.of(
				"%player%", safeName(target)
		));
		plugin.langManager().send(viewer, "profile.pronouns", Map.of(
				"%pronouns%", pronounManager.getPronouns(targetId).display()
		));
		plugin.langManager().send(viewer, "profile.status", Map.of(
				"%status%", getStatus(targetId)
		));

		if (partnerId == null) {
			plugin.langManager().send(viewer, "marriage.not-married");
			return;
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		long marriageDate = plugin.dataManager().dataConfig().getLong("marriage-dates." + targetId, 0L);
		long days = marriageDate <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - marriageDate) / MILLIS_PER_DAY);

		plugin.langManager().send(viewer, "profile.partner", Map.of(
				"%partner%", safeName(partner)
		));
		plugin.langManager().send(viewer, "profile.partner-display", Map.of(
				"%partner%", socialManager.getPartnerDisplayName(targetId, partnerId)
		));
		plugin.langManager().send(viewer, "profile.married-for", Map.of(
				"%days%", String.valueOf(days)
		));
		plugin.langManager().send(viewer, "profile.level", Map.of(
				"%level%", String.valueOf(marriageXpManager.getLevel(targetId))
		));
		plugin.langManager().send(viewer, "profile.xp", Map.of(
				"%xp%", String.valueOf(marriageXpManager.getXp(targetId)),
				"%required%", String.valueOf(marriageXpManager.getXpRequired(targetId))
		));
		plugin.langManager().send(viewer, "profile.partner-online", Map.of(
				"%status%", Bukkit.getPlayer(partnerId) == null
						? color("&cNo")
						: color("&aYes")
		));
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plugin.langManager().get("general.unknown") : player.getName();
	}
}