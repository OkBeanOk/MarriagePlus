package com.okbeanok.marriagePlus.services.notifications;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class NotificationManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;

	public NotificationManager(MarriagePlus plugin, MarriageManager marriageManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
	}

	public void notifyPartner(Player actor, String type) {
		notifyPartner(actor, type, Map.of());
	}

	public void notifyPartner(Player actor, String type, Map<String, String> placeholders) {
		if (!plugin.configs().notifications().getBoolean("enabled", true)) {
			return;
		}

		if (!plugin.configs().notifications().getBoolean("defaults." + type, true)) {
			return;
		}

		UUID partnerId = marriageManager.getPartnerId(actor.getUniqueId());

		if (partnerId == null) {
			return;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner == null) {
			return;
		}

		String message = plugin.configs().notifications().getString("messages." + type, "");

		if (message == null || message.isBlank()) {
			return;
		}

		message = message
				.replace("%player%", partner.getName())
				.replace("%partner%", actor.getName());

		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			message = message.replace(entry.getKey(), entry.getValue());
		}

		partner.sendMessage(color(message));
	}

	public void notifyBoth(Player first, Player second, String type) {
		notifyBoth(first, second, type, Map.of());
	}

	public void notifyBoth(Player first, Player second, String type, Map<String, String> placeholders) {
		if (!plugin.configs().notifications().getBoolean("enabled", true)) {
			return;
		}

		if (!plugin.configs().notifications().getBoolean("defaults." + type, true)) {
			return;
		}

		sendDirect(first, second, type, placeholders);
		sendDirect(second, first, type, placeholders);
	}

	private void sendDirect(Player receiver, Player partner, String type, Map<String, String> placeholders) {
		String message = plugin.configs().notifications().getString("messages." + type, "");

		if (message == null || message.isBlank()) {
			return;
		}

		message = message
				.replace("%player%", receiver.getName())
				.replace("%partner%", partner.getName());

		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			message = message.replace(entry.getKey(), entry.getValue());
		}

		receiver.sendMessage(color(message));
	}
}