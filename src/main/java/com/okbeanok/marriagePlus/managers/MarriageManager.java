package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

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
			player.sendMessage(color("&cYou are not married."));
			return null;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner == null) {
			player.sendMessage(color("&cYour partner is not online."));
			return null;
		}

		return partner;
	}

	public void marryPlayers(Player first, Player second) {
		if (first.getUniqueId().equals(second.getUniqueId())) {
			first.sendMessage(color("&cYou cannot marry yourself."));
			return;
		}

		if (isMarried(first.getUniqueId()) || isMarried(second.getUniqueId())) {
			first.sendMessage(color("&cOne of you is already married."));
			second.sendMessage(color("&cOne of you is already married."));
			return;
		}

		partners.put(first.getUniqueId(), second.getUniqueId());
		partners.put(second.getUniqueId(), first.getUniqueId());

		long date = System.currentTimeMillis();
		plugin.dataManager().dataConfig().set("marriage-dates." + first.getUniqueId(), date);
		plugin.dataManager().dataConfig().set("marriage-dates." + second.getUniqueId(), date);

		plugin.dataManager().saveData();

		Bukkit.broadcastMessage(color("&d❤ &f" + first.getName() + " &dand &f" + second.getName() + " &dare now married! ❤"));
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
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		String key = coupleKey(player.getUniqueId(), partnerId);

		if (enabled) {
			pvpEnabledCouples.add(key);
			player.sendMessage(color("&aPvP with your partner is now enabled."));
		} else {
			pvpEnabledCouples.remove(key);
			player.sendMessage(color("&ePvP with your partner is now disabled."));
		}

		plugin.dataManager().saveData();
	}

	public void listMarriages(org.bukkit.command.CommandSender sender) {
		sender.sendMessage(color("&dMarried Players:"));

		Set<String> shown = new HashSet<>();

		for (Map.Entry<UUID, UUID> entry : partners.entrySet()) {
			String key = coupleKey(entry.getKey(), entry.getValue());

			if (shown.add(key)) {
				OfflinePlayer first = Bukkit.getOfflinePlayer(entry.getKey());
				OfflinePlayer second = Bukkit.getOfflinePlayer(entry.getValue());

				sender.sendMessage(color("&f- &d" + first.getName() + " &7❤ &d" + second.getName()));
			}
		}

		if (shown.isEmpty()) {
			sender.sendMessage(color("&7No players are married yet."));
		}
	}

	public String coupleKey(UUID first, UUID second) {
		return first.toString().compareTo(second.toString()) < 0
				? first + ":" + second
				: second + ":" + first;
	}
}