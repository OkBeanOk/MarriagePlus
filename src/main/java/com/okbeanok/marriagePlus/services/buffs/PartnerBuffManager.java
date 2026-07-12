package com.okbeanok.marriagePlus.services.buffs;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.UUID;

public class PartnerBuffManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final MarriageXpManager marriageXpManager;
	private int taskId = -1;

	public PartnerBuffManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			MarriageXpManager marriageXpManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.marriageXpManager = marriageXpManager;
	}

	public void start() {
		if (!plugin.configs().buffs().getBoolean("enabled", true)) {
			return;
		}

		int intervalSeconds = plugin.configs().buffs().getInt("interval-seconds", 10);
		long intervalTicks = Math.max(20L, intervalSeconds * 20L);

		taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::applyBuffs, intervalTicks, intervalTicks);
	}

	public void stop() {
		if (taskId != -1) {
			Bukkit.getScheduler().cancelTask(taskId);
			taskId = -1;
		}
	}

	private void applyBuffs() {
		double radius = plugin.configs().buffs().getDouble("radius", 16.0D);
		double radiusSquared = radius * radius;

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!player.hasPermission("marriageplus.buffs")) {
				continue;
			}

			UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

			if (partnerId == null) {
				continue;
			}

			Player partner = Bukkit.getPlayer(partnerId);

			if (partner == null || !partner.isOnline()) {
				continue;
			}

			if (!player.getWorld().equals(partner.getWorld())) {
				continue;
			}

			if (plugin.configs().buffs().getStringList("disabled-worlds").contains(player.getWorld().getName())) {
				continue;
			}

			if (player.getLocation().distanceSquared(partner.getLocation()) > radiusSquared) {
				continue;
			}

			applyConfiguredBuffs(player);
			applyConfiguredBuffs(partner);
		}
	}

	private void applyConfiguredBuffs(Player player) {
		int marriageLevel = marriageXpManager.getLevel(player.getUniqueId());

		if (plugin.configs().buffs().getConfigurationSection("effects") == null) {
			return;
		}

		for (String effectName : plugin.configs().buffs().getConfigurationSection("effects").getKeys(false)) {
			String path = "effects." + effectName;

			if (!plugin.configs().buffs().getBoolean(path + ".enabled", true)) {
				continue;
			}

			int requiredLevel = plugin.configs().buffs().getInt(path + ".required-marriage-level", 1);

			if (marriageLevel < requiredLevel) {
				continue;
			}

			PotionEffectType effectType = getPotionEffectType(effectName);

			if (effectType == null) {
				plugin.getLogger().warning("Invalid partner buff effect: " + effectName);
				continue;
			}

			int amplifier = Math.max(0, plugin.configs().buffs().getInt(path + ".amplifier", 0));
			int durationSeconds = plugin.configs().buffs().getInt(path + ".duration-seconds", 12);

			player.addPotionEffect(new PotionEffect(
					effectType,
					Math.max(20, durationSeconds * 20),
					amplifier,
					true,
					false,
					true
			));
		}
	}

	private PotionEffectType getPotionEffectType(String effectName) {
		String normalized = effectName.toLowerCase(Locale.ROOT).replace("_", "-");
		return Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(normalized));
	}
}