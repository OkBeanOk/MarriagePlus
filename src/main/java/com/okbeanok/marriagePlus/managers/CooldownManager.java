package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class CooldownManager {

	private final MarriagePlus plugin;
	private final Map<String, Long> cooldowns = new HashMap<>();

	public CooldownManager(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	public boolean isOnCooldown(Player player, String key) {
		String cooldownKey = player.getUniqueId() + ":" + key;
		long expiresAt = cooldowns.getOrDefault(cooldownKey, 0L);

		if (System.currentTimeMillis() <= expiresAt) {
			long secondsLeft = Math.max(1L, (expiresAt - System.currentTimeMillis()) / 1000L);
			player.sendMessage(color("&cPlease wait &f" + secondsLeft + "s &cbefore using this again."));
			return true;
		}

		cooldowns.remove(cooldownKey);
		return false;
	}

	public void setCooldown(Player player, String key, int seconds) {
		if (seconds <= 0) {
			return;
		}

		cooldowns.put(player.getUniqueId() + ":" + key, System.currentTimeMillis() + seconds * 1000L);
	}

	public int configuredSeconds(String path, int fallback) {
		return plugin.getConfig().getInt(path, fallback);
	}
}