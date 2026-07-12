package com.okbeanok.marriagePlus.services;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MarriageManager {

	private final MarriagePlus plugin;
	private final Map<UUID, UUID> partners = new HashMap<>();
	private final Set<String> pvpEnabledCouples = new HashSet<>();

	public MarriageManager(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	public Map<UUID, UUID> partners() {
		return partners;
	}

	public Set<String> pvpEnabledCouples() {
		return pvpEnabledCouples;
	}

	public boolean isMarried(UUID uuid) {
		return partners.containsKey(uuid);
	}

	public UUID getPartnerId(UUID uuid) {
		return partners.get(uuid);
	}

	public Player getOnlinePartner(Player player) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return null;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner == null) {
			plugin.langManager().send(player, "marriage.partner-offline");
			return null;
		}

		return partner;
	}

	public Player getOnlinePartnerSilent(Player player) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			return null;
		}

		return Bukkit.getPlayer(partnerId);
	}

	public void marryPlayers(Player first, Player second) {
		if (first.getUniqueId().equals(second.getUniqueId())) {
			plugin.langManager().send(first, "marriage.cannot-marry-yourself");
			return;
		}

		if (isMarried(first.getUniqueId()) || isMarried(second.getUniqueId())) {
			plugin.langManager().send(first, "marriage.one-already-married");
			plugin.langManager().send(second, "marriage.one-already-married");
			return;
		}

		partners.put(first.getUniqueId(), second.getUniqueId());
		partners.put(second.getUniqueId(), first.getUniqueId());

		long date = System.currentTimeMillis();
		plugin.dataManager().dataConfig().set("marriage-dates." + first.getUniqueId(), date);
		plugin.dataManager().dataConfig().set("marriage-dates." + second.getUniqueId(), date);

		plugin.dataManager().saveData();

		Bukkit.broadcastMessage(plugin.langManager().get("marriage.married", Map.of(
				"%player%", first.getName(),
				"%partner%", second.getName()
		)));
	}

	public void divorceCouple(UUID first, UUID second) {
		partners.remove(first);
		partners.remove(second);

		plugin.homeManager().homes().remove(first);
		plugin.homeManager().homes().remove(second);
		plugin.requestManager().coupleChatToggled().remove(first);
		plugin.requestManager().coupleChatToggled().remove(second);

		pvpEnabledCouples.remove(coupleKey(first, second));

		plugin.dataManager().saveData();
	}

	public void setPartnerPvp(Player player, boolean enabled) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		String key = coupleKey(player.getUniqueId(), partnerId);

		if (enabled) {
			pvpEnabledCouples.add(key);
			plugin.langManager().send(player, "pvp.enabled");
		} else {
			pvpEnabledCouples.remove(key);
			plugin.langManager().send(player, "pvp.disabled");
		}

		plugin.dataManager().saveData();
	}

	public void listMarriages(org.bukkit.command.CommandSender sender) {
		plugin.langManager().send(sender, "marriage-list.header");

		Set<String> shown = new HashSet<>();

		for (Map.Entry<UUID, UUID> entry : partners.entrySet()) {
			String key = coupleKey(entry.getKey(), entry.getValue());

			if (shown.add(key)) {
				OfflinePlayer first = Bukkit.getOfflinePlayer(entry.getKey());
				OfflinePlayer second = Bukkit.getOfflinePlayer(entry.getValue());

				plugin.langManager().send(sender, "marriage-list.line", Map.of(
						"%player%", safeName(first),
						"%partner%", safeName(second)
				));
			}
		}

		if (shown.isEmpty()) {
			plugin.langManager().send(sender, "marriage-list.empty");
		}
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plugin.langManager().get("general.unknown") : player.getName();
	}

	public String coupleKey(UUID first, UUID second) {
		return first.toString().compareTo(second.toString()) < 0
				? first + ":" + second
				: second + ":" + first;
	}
}