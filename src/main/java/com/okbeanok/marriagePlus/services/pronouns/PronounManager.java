package com.okbeanok.marriagePlus.services.pronouns;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.Pronouns;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PronounManager {

	private final MarriagePlus plugin;
	private final Map<UUID, Pronouns> pronouns = new HashMap<>();

	public PronounManager(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	public Map<UUID, Pronouns> pronouns() {
		return pronouns;
	}

	public void pronounsCommand(Player player, String[] args) {
		if (args.length == 1) {
			Pronouns playerPronouns = getPronouns(player.getUniqueId());

			plugin.langManager().send(player, "pronouns.current", Map.of(
					"%pronouns%", playerPronouns.display()
			));
			plugin.langManager().send(player, "pronouns.usage");
			plugin.langManager().send(player, "pronouns.custom-example");
			return;
		}

		if (args[1].equalsIgnoreCase("custom")) {
			if (args.length < 5) {
				plugin.langManager().send(player, "pronouns.custom-usage");
				plugin.langManager().send(player, "pronouns.custom-example");
				return;
			}

			Pronouns customPronouns = new Pronouns(args[2], args[3], args[4], args[2] + "/" + args[3]);
			pronouns.put(player.getUniqueId(), customPronouns);
			plugin.dataManager().saveData();

			plugin.langManager().send(player, "pronouns.set", Map.of(
					"%pronouns%", customPronouns.display()
			));
			return;
		}

		Pronouns selectedPronouns = parsePronouns(args[1]);

		if (selectedPronouns == null) {
			plugin.langManager().send(player, "pronouns.unknown");
			return;
		}

		pronouns.put(player.getUniqueId(), selectedPronouns);
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "pronouns.set", Map.of(
				"%pronouns%", selectedPronouns.display()
		));
	}

	public Pronouns parsePronouns(String input) {
		String normalized = input.toLowerCase(Locale.ROOT);

		return switch (normalized) {
			case "he", "him", "he/him" -> new Pronouns("he", "him", "his", "he/him");
			case "she", "her", "she/her" -> new Pronouns("she", "her", "her", "she/her");
			case "they", "them", "they/them" -> new Pronouns("they", "them", "their", "they/them");
			case "any", "any/all" -> new Pronouns("they", "them", "their", "any/all");
			default -> null;
		};
	}

	public Pronouns getPronouns(UUID uuid) {
		return pronouns.getOrDefault(uuid, new Pronouns(
				plugin.getConfig().getString("settings.default-pronouns.subject", "they"),
				plugin.getConfig().getString("settings.default-pronouns.object", "them"),
				plugin.getConfig().getString("settings.default-pronouns.possessive", "their"),
				plugin.getConfig().getString("settings.default-pronouns.display", "they/them")
		));
	}

	public String applyPronounPlaceholders(String message, Player player, Player partner) {
		Pronouns playerPronouns = getPronouns(player.getUniqueId());
		Pronouns partnerPronouns = getPronouns(partner.getUniqueId());

		return message
				.replace("%player_pronouns%", playerPronouns.display())
				.replace("%player_subject%", playerPronouns.subject())
				.replace("%player_object%", playerPronouns.object())
				.replace("%player_possessive%", playerPronouns.possessive())
				.replace("%partner_pronouns%", partnerPronouns.display())
				.replace("%partner_subject%", partnerPronouns.subject())
				.replace("%partner_object%", partnerPronouns.object())
				.replace("%partner_possessive%", partnerPronouns.possessive());
	}
}