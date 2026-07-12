package com.okbeanok.marriagePlus.services.ceremonies;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;
import static com.okbeanok.marriagePlus.utils.TextUtils.legacy;

public class CeremonyManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final MarriageXpManager marriageXpManager;

	private final Map<UUID, CeremonyInvite> pendingCeremonies = new HashMap<>();
	private final Map<String, Long> ceremonyCooldowns = new HashMap<>();

	public CeremonyManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			MarriageXpManager marriageXpManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.marriageXpManager = marriageXpManager;
	}

	public void ceremonyCommand(Player player, String[] args) {
		if (!plugin.configs().ceremonies().getBoolean("enabled", true)) {
			plugin.langManager().send(player, "ceremony.disabled");
			return;
		}

		if (args.length < 2) {
			sendUsage(player);
			return;
		}

		switch (args[1].toLowerCase(Locale.ROOT)) {
			case "start" -> startCeremony(player);
			case "accept" -> acceptCeremony(player);
			case "cancel" -> cancelCeremony(player);
			default -> sendUsage(player);
		}
	}

	private void startCeremony(Player player) {
		UUID playerId = player.getUniqueId();
		UUID partnerId = marriageManager.getPartnerId(playerId);

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		String coupleKey = marriageManager.coupleKey(playerId, partnerId);

		if (isOnCeremonyCooldown(coupleKey)) {
			plugin.langManager().send(player, "ceremony.cooldown", Map.of(
					"%time%", formatTimeRemaining(ceremonyCooldowns.get(coupleKey) - System.currentTimeMillis())
			));
			return;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner == null) {
			plugin.langManager().send(player, "ceremony.partner-offline");
			return;
		}

		if (pendingCeremonies.containsKey(partnerId) || pendingCeremonies.containsKey(playerId)) {
			plugin.langManager().send(player, "ceremony.already-pending");
			return;
		}

		int expireSeconds = plugin.configs().ceremonies().getInt("invite-expire-seconds", 120);
		long expiresAt = System.currentTimeMillis() + expireSeconds * 1000L;

		pendingCeremonies.put(partnerId, new CeremonyInvite(playerId, partnerId, coupleKey, expiresAt));

		Bukkit.getScheduler().runTaskLater(plugin, () -> expireInvite(partnerId, playerId), Math.max(1L, expireSeconds) * 20L);

		plugin.langManager().send(player, "ceremony.started", Map.of(
				"%partner%", partner.getName()
		));

		plugin.langManager().send(partner, "ceremony.invited", Map.of(
				"%player%", player.getName()
		));

		sendCeremonyButtons(partner, expireSeconds);

		plugin.notificationManager().notifyPartner(player, "ceremony-invite");
		plugin.langManager().send(partner, "ceremony.accept-hint");
	}

	private void acceptCeremony(Player player) {
		UUID playerId = player.getUniqueId();
		CeremonyInvite invite = pendingCeremonies.remove(playerId);

		if (invite == null) {
			plugin.langManager().send(player, "ceremony.no-invite");
			return;
		}

		if (invite.expired()) {
			plugin.langManager().send(player, "ceremony.expired");
			return;
		}

		UUID partnerId = marriageManager.getPartnerId(playerId);

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (!partnerId.equals(invite.inviterId())) {
			plugin.langManager().send(player, "ceremony.no-invite");
			return;
		}

		String currentCoupleKey = marriageManager.coupleKey(playerId, partnerId);

		if (!currentCoupleKey.equals(invite.coupleKey())) {
			plugin.langManager().send(player, "ceremony.no-invite");
			return;
		}

		if (isOnCeremonyCooldown(invite.coupleKey())) {
			plugin.langManager().send(player, "ceremony.cooldown", Map.of(
					"%time%", formatTimeRemaining(ceremonyCooldowns.get(invite.coupleKey()) - System.currentTimeMillis())
			));
			return;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner == null) {
			plugin.langManager().send(player, "ceremony.partner-offline");
			return;
		}

		if (!isNearEnough(player, partner)) {
			plugin.langManager().send(player, "ceremony.not-near");
			return;
		}

		setCeremonyCooldown(invite.coupleKey());
		completeCeremony(partner, player);
	}

	private void cancelCeremony(Player player) {
		UUID playerId = player.getUniqueId();
		UUID partnerId = marriageManager.getPartnerId(playerId);

		boolean removed = pendingCeremonies.remove(playerId) != null;

		if (partnerId != null) {
			removed = pendingCeremonies.remove(partnerId) != null || removed;
		}

		plugin.langManager().send(player, removed ? "ceremony.cancelled" : "ceremony.no-invite");

		if (removed && partnerId != null) {
			Player partner = Bukkit.getPlayer(partnerId);

			if (partner != null) {
				plugin.langManager().send(partner, "ceremony.cancelled");
			}
		}
	}

	private void completeCeremony(Player first, Player second) {
		sendVows(first, second);
		playEffects(first);
		playEffects(second);
		handleReward(first, second);
		giveRings(first, second);
		broadcastCompletion(first, second);

		plugin.notificationManager().notifyBoth(first, second, "ceremony-completed");
		plugin.langManager().send(first, "ceremony.completed");
		plugin.langManager().send(second, "ceremony.completed");
	}

	private void handleReward(Player first, Player second) {
		String coupleKey = marriageManager.coupleKey(first.getUniqueId(), second.getUniqueId());
		boolean rewardOnce = plugin.configs().ceremonies().getBoolean("reward-once-per-couple", true);
		boolean rewardAlreadyClaimed = plugin.dataManager().dataConfig().getBoolean("ceremony-rewards-claimed." + coupleKey, false);

		if (rewardOnce && rewardAlreadyClaimed) {
			plugin.langManager().send(first, "ceremony.reward-already-claimed");
			plugin.langManager().send(second, "ceremony.reward-already-claimed");
			return;
		}

		int rewardXp = plugin.configs().ceremonies().getInt("reward-xp", 250);

		if (rewardXp > 0) {
			marriageXpManager.addXp(first.getUniqueId(), second.getUniqueId(), rewardXp);
		}

		if (rewardOnce) {
			plugin.dataManager().dataConfig().set("ceremony-rewards-claimed." + coupleKey, true);
			plugin.dataManager().saveDataFile();
		}
	}

	private void giveRings(Player first, Player second) {
		if (!plugin.configs().ceremonies().getBoolean("give-rings", true)) {
			return;
		}

		if (plugin.ringManager() == null) {
			return;
		}

		plugin.ringManager().giveRing(first, false);
		plugin.ringManager().giveRing(second, false);
	}

	private void broadcastCompletion(Player first, Player second) {
		if (!plugin.configs().ceremonies().getBoolean("broadcast", true)) {
			return;
		}

		String message = plugin.configs().ceremonies().getString(
				"broadcast-message",
				"&d❤ &f%player% &7and &f%partner% &dcelebrated their marriage ceremony!"
		);

		Bukkit.broadcastMessage(color(message
				.replace("%player%", first.getName())
				.replace("%partner%", second.getName())));
	}

	private void sendVows(Player first, Player second) {
		for (String vow : plugin.configs().ceremonies().getStringList("messages.vows")) {
			String message = vow
					.replace("%player%", first.getName())
					.replace("%partner%", second.getName());

			Bukkit.broadcastMessage(color(message));
		}
	}

	private void sendCeremonyButtons(Player partner, int expireSeconds) {
		Component acceptButton = legacy(plugin.langManager().get("ceremony.accept-button"))
				.clickEvent(ClickEvent.runCommand("/marry ceremony accept"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("ceremony.accept-hover"))));

		Component cancelButton = legacy(plugin.langManager().get("ceremony.cancel-button"))
				.clickEvent(ClickEvent.runCommand("/marry ceremony cancel"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("ceremony.cancel-hover"))));

		partner.sendMessage(legacy(plugin.langManager().get("ceremony.buttons-prefix"))
				.append(acceptButton)
				.append(legacy(plugin.langManager().get("ceremony.buttons-separator")))
				.append(cancelButton)
				.append(legacy(plugin.langManager().get("ceremony.buttons-expire", Map.of(
						"%seconds%", String.valueOf(expireSeconds)
				)))));
	}

	private void expireInvite(UUID receiverId, UUID inviterId) {
		CeremonyInvite invite = pendingCeremonies.get(receiverId);

		if (invite == null || !invite.inviterId().equals(inviterId)) {
			return;
		}

		if (!invite.expired()) {
			return;
		}

		pendingCeremonies.remove(receiverId);

		Player receiver = Bukkit.getPlayer(receiverId);
		Player inviter = Bukkit.getPlayer(inviterId);

		if (receiver != null) {
			plugin.langManager().send(receiver, "ceremony.expired");
		}

		if (inviter != null) {
			plugin.langManager().send(inviter, "ceremony.expired");
		}
	}

	private boolean isNearEnough(Player first, Player second) {
		if (!first.getWorld().equals(second.getWorld())) {
			return false;
		}

		double radius = plugin.configs().ceremonies().getDouble("required-radius", 12.0D);
		return first.getLocation().distanceSquared(second.getLocation()) <= radius * radius;
	}

	private void playEffects(Player player) {
		if (!plugin.configs().ceremonies().getBoolean("effects.enabled", true)) {
			return;
		}

		Location location = player.getLocation().add(0.0D, 1.0D, 0.0D);

		Particle particle = getParticle(plugin.configs().ceremonies().getString("effects.particle", "HEART"));
		int particleCount = plugin.configs().ceremonies().getInt("effects.particle-count", 35);

		if (particle != null) {
			player.getWorld().spawnParticle(
					particle,
					location,
					particleCount,
					0.7D,
					0.8D,
					0.7D,
					0.02D
			);
		}

		Sound sound = getSound(plugin.configs().ceremonies().getString("effects.sound", "ENTITY_FIREWORK_ROCKET_TWINKLE"));

		if (sound != null) {
			player.getWorld().playSound(player.getLocation(), sound, 1.0F, 1.0F);
		}

		if (plugin.configs().ceremonies().getBoolean("effects.fireworks", true)) {
			spawnFirework(player.getLocation());
		}
	}

	private void spawnFirework(Location location) {
		if (location.getWorld() == null) {
			return;
		}

		Firework firework = location.getWorld().spawn(location, Firework.class);
		FireworkMeta meta = firework.getFireworkMeta();

		meta.addEffect(FireworkEffect.builder()
				.withColor(Color.FUCHSIA)
				.withFade(Color.WHITE)
				.with(FireworkEffect.Type.BALL_LARGE)
				.trail(true)
				.flicker(true)
				.build());

		meta.addEffect(FireworkEffect.builder()
				.withColor(Color.FUCHSIA)
				.withFade(Color.RED)
				.with(FireworkEffect.Type.BALL_LARGE)
				.trail(true)
				.flicker(true)
				.build());

		meta.setPower(1);
		firework.setFireworkMeta(meta);
	}

	private Particle getParticle(String particleName) {
		if (particleName == null || particleName.isBlank()) {
			return null;
		}

		try {
			return Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			plugin.getLogger().warning("Invalid ceremony particle: " + particleName);
			return null;
		}
	}

	private Sound getSound(String soundName) {
		if (soundName == null || soundName.isBlank()) {
			return null;
		}

		String normalized = soundName.toLowerCase(Locale.ROOT);
		Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(normalized));

		if (sound != null) {
			return sound;
		}

		sound = Registry.SOUNDS.get(NamespacedKey.minecraft(normalized.replace('_', '.')));

		if (sound == null) {
			plugin.getLogger().warning("Invalid ceremony sound: " + soundName);
		}

		return sound;
	}

	private boolean isOnCeremonyCooldown(String coupleKey) {
		long expiresAt = ceremonyCooldowns.getOrDefault(coupleKey, 0L);

		if (expiresAt <= System.currentTimeMillis()) {
			ceremonyCooldowns.remove(coupleKey);
			return false;
		}

		return true;
	}

	private void setCeremonyCooldown(String coupleKey) {
		int cooldownSeconds = plugin.configs().ceremonies().getInt("cooldown-seconds", 86400);

		if (cooldownSeconds <= 0) {
			return;
		}

		ceremonyCooldowns.put(coupleKey, System.currentTimeMillis() + cooldownSeconds * 1000L);
	}

	private String formatTimeRemaining(long millis) {
		long seconds = Math.max(1L, millis / 1000L);
		long days = seconds / 86400L;
		long hours = seconds % 86400L / 3600L;
		long minutes = seconds % 3600L / 60L;

		if (days > 0L) {
			return days + "d " + hours + "h";
		}

		if (hours > 0L) {
			return hours + "h " + minutes + "m";
		}

		if (minutes > 0L) {
			return minutes + "m";
		}

		return seconds + "s";
	}

	private void sendUsage(Player player) {
		plugin.langManager().send(player, "ceremony.usage-start");
		plugin.langManager().send(player, "ceremony.usage-accept");
		plugin.langManager().send(player, "ceremony.usage-cancel");
	}

	private record CeremonyInvite(UUID inviterId, UUID receiverId, String coupleKey, long expiresAt) {

		private boolean expired() {
			return System.currentTimeMillis() > expiresAt;
		}
	}
}