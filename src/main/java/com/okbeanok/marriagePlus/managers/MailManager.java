package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.PartnerMail;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class MailManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final Map<UUID, List<PartnerMail>> inboxes = new HashMap<>();

	public MailManager(MarriagePlus plugin, MarriageManager marriageManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
	}

	public Map<UUID, List<PartnerMail>> inboxes() {
		return inboxes;
	}

	public void mailCommand(Player player, String[] args) {
		if (args.length < 2) {
			sendUsage(player);
			return;
		}

		switch (args[1].toLowerCase()) {
			case "send" -> sendMail(player, args);
			case "read" -> readMail(player);
			case "clear" -> clearMail(player);
			default -> sendUsage(player);
		}
	}

	private void sendMail(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length < 3) {
			plugin.langManager().send(player, "mail.usage-send");
			return;
		}

		String message = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();

		if (message.isBlank()) {
			plugin.langManager().send(player, "mail.usage-send");
			return;
		}

		int maxLength = plugin.getConfig().getInt("mail.max-message-length", 180);

		if (message.length() > maxLength) {
			plugin.langManager().send(player, "mail.too-long", Map.of(
					"%max%", String.valueOf(maxLength)
			));
			return;
		}

		List<PartnerMail> partnerInbox = inboxes.computeIfAbsent(partnerId, ignored -> new ArrayList<>());
		int maxInboxSize = plugin.getConfig().getInt("mail.max-inbox-size", 25);

		if (partnerInbox.size() >= maxInboxSize) {
			plugin.langManager().send(player, "mail.inbox-full");
			return;
		}

		partnerInbox.add(new PartnerMail(player.getUniqueId(), player.getName(), message, System.currentTimeMillis()));
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "mail.sent");

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner != null) {
			plugin.langManager().send(partner, "mail.received", Map.of(
					"%player%", player.getName()
			));
			plugin.langManager().send(partner, "mail.read-hint");
		}
	}

	private void readMail(Player player) {
		List<PartnerMail> inbox = inboxes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (inbox.isEmpty()) {
			plugin.langManager().send(player, "mail.empty");
			return;
		}

		boolean showTimestamps = plugin.getConfig().getBoolean("mail.show-read-timestamps", true);
		SimpleDateFormat dateFormat = new SimpleDateFormat(plugin.getConfig().getString("mail.timestamp-format", "yyyy-MM-dd HH:mm"));

		plugin.langManager().send(player, "mail.header");

		for (int index = 0; index < inbox.size(); index++) {
			PartnerMail mail = inbox.get(index);

			plugin.langManager().send(player, "mail.line", Map.of(
					"%number%", String.valueOf(index + 1),
					"%sender%", mail.senderName(),
					"%message%", mail.message()
			));

			if (showTimestamps) {
				plugin.langManager().send(player, "mail.sent-at", Map.of(
						"%time%", dateFormat.format(new Date(mail.sentAt()))
				));
			}
		}

		plugin.langManager().send(player, "mail.clear-hint");
	}

	private void clearMail(Player player) {
		List<PartnerMail> inbox = inboxes.get(player.getUniqueId());

		if (inbox == null || inbox.isEmpty()) {
			plugin.langManager().send(player, "mail.empty-clear");
			return;
		}

		inbox.clear();
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "mail.cleared");
	}

	public void notifyUnreadMail(Player player) {
		if (!plugin.getConfig().getBoolean("mail.notify-on-join", true)) {
			return;
		}

		List<PartnerMail> inbox = inboxes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (inbox.isEmpty()) {
			return;
		}

		plugin.langManager().send(player, "mail.unread", Map.of(
				"%amount%", String.valueOf(inbox.size())
		));
		plugin.langManager().send(player, "mail.read-hint");
	}

	private void sendUsage(Player player) {
		plugin.langManager().send(player, "mail.usage-send");
		plugin.langManager().send(player, "mail.usage-read");
		plugin.langManager().send(player, "mail.usage-clear");
	}
}