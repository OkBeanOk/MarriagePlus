package com.okbeanok.marriagePlus.services.mail;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.PartnerMail;
import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.okbeanok.marriagePlus.utils.TextUtils.legacy;

public class MailManager {
	private static final int CHAT_MAIL_PER_PAGE = 8;

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
			plugin.marriageGuiManager().openMailMenu(player);
			return;
		}

		switch (args[1].toLowerCase()) {
			case "send" -> sendMail(player, args);
			case "read" -> readMail(player, args);
			case "inbox", "list" -> listMail(player, args);
			case "unread" -> markMailUnread(player, args);
			case "delete" -> deleteMail(player, args);
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

		String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();

		if (message.isBlank()) {
			plugin.langManager().send(player, "mail.usage-send");
			return;
		}

		int maxLength = plugin.configs().mail().getInt("max-message-length", 180);

		if (message.length() > maxLength) {
			plugin.langManager().send(player, "mail.too-long", Map.of(
					"%max%", String.valueOf(maxLength)
			));
			return;
		}

		List<PartnerMail> partnerInbox = inboxes.computeIfAbsent(partnerId, ignored -> new ArrayList<>());
		int maxInboxSize = plugin.configs().mail().getInt("max-inbox-size", 25);

		if (partnerInbox.size() >= maxInboxSize) {
			plugin.langManager().send(player, "mail.inbox-full");
			return;
		}

		partnerInbox.add(new PartnerMail(
				player.getUniqueId(),
				player.getName(),
				message,
				System.currentTimeMillis(),
				true
		));

		plugin.dataManager().saveData();
		plugin.langManager().send(player, "mail.sent");

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner == null) {
			return;
		}

		plugin.langManager().send(partner, "mail.received", Map.of(
				"%player%", player.getName()
		));
		plugin.langManager().send(partner, "mail.read-hint");
	}

	private void readMail(Player player, String[] args) {
		List<PartnerMail> inbox = inboxes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (inbox.isEmpty()) {
			plugin.langManager().send(player, "mail.empty");
			return;
		}

		if (args.length >= 3) {
			readSingleMail(player, args, inbox);
			return;
		}

		listMail(player, new String[] {"mail", "inbox"});
	}

	private void readSingleMail(Player player, String[] args, List<PartnerMail> inbox) {
		Integer mailNumber = parseMailNumber(player, args[2]);

		if (mailNumber == null) {
			return;
		}

		if (mailNumber < 1 || mailNumber > inbox.size()) {
			plugin.langManager().send(player, "mail.invalid-number");
			return;
		}

		PartnerMail mail = inbox.get(mailNumber - 1);

		if (mail.unread()) {
			inbox.set(mailNumber - 1, new PartnerMail(
					mail.senderId(),
					mail.senderName(),
					mail.message(),
					mail.sentAt(),
					false
			));

			plugin.dataManager().saveData();
		}

		plugin.langManager().send(player, "mail.message-header", Map.of(
				"%number%", String.valueOf(mailNumber)
		));

		plugin.langManager().send(player, "mail.message-from", Map.of(
				"%sender%", mail.senderName()
		));

		plugin.langManager().send(player, "mail.message-body", Map.of(
				"%message%", mail.message()
		));

		if (plugin.configs().mail().getBoolean("show-read-timestamps", true)) {
			plugin.langManager().send(player, "mail.sent-at", Map.of(
					"%time%", formatTime(mail.sentAt())
			));
		}


	}

	private void listMail(Player player, String[] args) {
		List<PartnerMail> inbox = inboxes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (inbox.isEmpty()) {
			plugin.langManager().send(player, "mail.empty");
			return;
		}

		int requestedPage = 1;

		if (args.length >= 3) {
			try {
				requestedPage = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				plugin.langManager().send(player, "mail.invalid-page");
				return;
			}
		}

		int maxPage = Math.max(1, (int) Math.ceil(inbox.size() / (double) CHAT_MAIL_PER_PAGE));
		int page = Math.max(1, Math.min(requestedPage, maxPage));
		int start = (page - 1) * CHAT_MAIL_PER_PAGE;
		int end = Math.min(start + CHAT_MAIL_PER_PAGE, inbox.size());

		plugin.langManager().send(player, "mail.header-page", Map.of(
				"%page%", String.valueOf(page),
				"%max_page%", String.valueOf(maxPage)
		));

		for (int index = start; index < end; index++) {
			PartnerMail mail = inbox.get(index);

			sendClickableMailLine(player, mail, index + 1);

			if (plugin.configs().mail().getBoolean("show-read-timestamps", true)) {
				plugin.langManager().send(player, "mail.sent-at", Map.of(
						"%time%", formatTime(mail.sentAt())
				));
			}
		}

		plugin.langManager().send(player, "mail.list-hint");

		if (page < maxPage) {
			plugin.langManager().send(player, "mail.next-page-hint", Map.of(
					"%page%", String.valueOf(page + 1)
			));
		}
	}

	private void markMailUnread(Player player, String[] args) {
		if (args.length < 3) {
			plugin.langManager().send(player, "mail.usage-unread");
			return;
		}

		Integer mailNumber = parseMailNumber(player, args[2]);

		if (mailNumber == null) {
			return;
		}

		List<PartnerMail> inbox = inboxes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (mailNumber < 1 || mailNumber > inbox.size()) {
			plugin.langManager().send(player, "mail.invalid-number");
			return;
		}

		int mailIndex = mailNumber - 1;
		PartnerMail mail = inbox.get(mailIndex);

		if (mail.unread()) {
			plugin.langManager().send(player, "mail.already-unread");
			return;
		}

		inbox.set(mailIndex, new PartnerMail(
				mail.senderId(),
				mail.senderName(),
				mail.message(),
				mail.sentAt(),
				true
		));

		plugin.dataManager().saveData();
		plugin.langManager().send(player, "mail.marked-unread", Map.of(
				"%number%", String.valueOf(mailNumber)
		));
	}

	private void sendClickableMailLine(Player player, PartnerMail mail, int mailNumber) {
		Component mailLine = legacy(plugin.langManager().get("mail.line", Map.of(
				"%number%", String.valueOf(mailNumber),
				"%sender%", mail.senderName(),
				"%message%", mail.message()
		)))
				.clickEvent(ClickEvent.runCommand("/marry mail read " + mailNumber))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("mail.list-line-hover", Map.of(
						"%number%", String.valueOf(mailNumber)
				)))));

		Component deleteButton = legacy(plugin.langManager().get("mail.list-delete-button"))
				.clickEvent(ClickEvent.suggestCommand("/marry mail delete " + mailNumber))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("mail.list-delete-hover", Map.of(
						"%number%", String.valueOf(mailNumber)
				)))));

		player.sendMessage(mailLine.append(legacy(" ")).append(deleteButton));
	}

	private void deleteMail(Player player, String[] args) {
		if (args.length < 3) {
			plugin.langManager().send(player, "mail.usage-delete");
			return;
		}

		Integer mailNumber = parseMailNumber(player, args[2]);

		if (mailNumber == null) {
			return;
		}

		List<PartnerMail> inbox = inboxes.get(player.getUniqueId());

		if (inbox == null || mailNumber < 1 || mailNumber > inbox.size()) {
			plugin.langManager().send(player, "mail.invalid-number");
			return;
		}

		inbox.remove(mailNumber - 1);

		if (inbox.isEmpty()) {
			inboxes.remove(player.getUniqueId());
		}

		plugin.dataManager().saveData();
		plugin.langManager().send(player, "mail.deleted");
	}

	private Integer parseMailNumber(Player player, String input) {
		try {
			return Integer.parseInt(input);
		} catch (NumberFormatException exception) {
			plugin.langManager().send(player, "mail.invalid-number");
			return null;
		}
	}

	private String formatTime(long timestamp) {
		String timestampFormat = plugin.configs().mail().getString("timestamp-format", "yyyy-MM-dd HH:mm");
		SimpleDateFormat dateFormat = new SimpleDateFormat(timestampFormat);

		return dateFormat.format(new Date(timestamp));
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
		if (!plugin.configs().mail().getBoolean("notify-on-join", true)) {
			return;
		}

		List<PartnerMail> inbox = inboxes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (inbox.isEmpty()) {
			return;
		}

		long unread = inboxes.getOrDefault(player.getUniqueId(), new ArrayList<>()).stream()
				.filter(PartnerMail::unread)
				.count();

		if (unread <= 0L) {
			return;
		}

		plugin.langManager().send(player, "mail.unread", Map.of(
				"%amount%", String.valueOf(unread)
		));
		plugin.langManager().send(player, "mail.read-hint");
	}

	private void sendUsage(Player player) {
		plugin.langManager().send(player, "mail.usage-send");
		plugin.langManager().send(player, "mail.usage-read");
		plugin.langManager().send(player, "mail.usage-unread");
		plugin.langManager().send(player, "mail.usage-delete");
		plugin.langManager().send(player, "mail.usage-clear");
	}
}