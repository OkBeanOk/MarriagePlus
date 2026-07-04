package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class HomeManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final CooldownManager cooldownManager;

	private final Map<UUID, Location> homes = new HashMap<>();

	public HomeManager(MarriagePlus plugin, MarriageManager marriageManager, CooldownManager cooldownManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.cooldownManager = cooldownManager;
	}

	public Map<UUID, Location> homes() {
		return homes;
	}

	public void setHome(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		homes.put(player.getUniqueId(), player.getLocation());
		homes.put(partnerId, player.getLocation());

		plugin.dataManager().saveData();

		player.sendMessage(color("&aCouple home set."));
	}

	public void goHome(Player player) {
		if (cooldownManager.isOnCooldown(player, "home")) {
			return;
		}

		Location home = homes.get(player.getUniqueId());

		if (home == null) {
			player.sendMessage(color("&cYou do not have a couple home set."));
			return;
		}

		cooldownManager.setCooldown(player, "home", plugin.getConfig().getInt("settings.cooldowns.home-seconds", 30));

		player.teleport(home);
		player.sendMessage(color("&dTeleported to your couple home."));
	}

	public boolean hasHome(UUID playerId) {
		return homes.containsKey(playerId);
	}
}