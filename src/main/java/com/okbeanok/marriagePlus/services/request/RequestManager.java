package com.okbeanok.marriagePlus.services.request;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.families.FamilyMarriageCheckResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;
import static com.okbeanok.marriagePlus.utils.TextUtils.legacy;

public class RequestManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;

	private final Map<UUID, UUID> proposals = new HashMap<>();
	private final Set<UUID> coupleChatToggled = new HashSet<>();
	private final Set<UUID> listeningToMarriageChat = new HashSet<>();
	private final Set<UUID> marriageRequestsDisabled = new HashSet<>();
	private final Set<String> blockedMarriageRequests = new HashSet<>();
	private final Map<UUID, String> chatColors = new HashMap<>();
	private final Set<UUID> chatPrefixDisabled = new HashSet<>();
	private final Map<UUID, Long> proposalCooldowns = new HashMap<>();

	public RequestManager(MarriagePlus plugin, MarriageManager marriageManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
	}

	public Map<UUID, UUID> proposals() {
		return proposals;
	}

	public Set<UUID> coupleChatToggled() {
		return coupleChatToggled;
	}

	public Set<UUID> listeningToMarriageChat() {
		return listeningToMarriageChat;
	}

	public Set<UUID> marriageRequestsDisabled() {
		return marriageRequestsDisabled;
	}

	public Set<String> blockedMarriageRequests() {
		return blockedMarriageRequests;
	}

	public void sendMarriageRequest(Player player, String[] args) {
		if (args.length < 2) {
			plugin.langManager().send(player, "requests.usage");
			return;
		}

		if (isOnProposalCooldown(player)) {
			return;
		}

		if (marriageManager.isMarried(player.getUniqueId())) {
			plugin.langManager().send(player, "marriage.already-married");
			return;
		}

		Player target = Bukkit.getPlayerExact(args[1]);

		if (target == null) {
			plugin.langManager().send(player, "general.player-not-online");
			return;
		}

		if (target.getUniqueId().equals(player.getUniqueId())) {
			plugin.langManager().send(player, "marriage.cannot-marry-yourself");
			return;
		}

		if (marriageManager.isMarried(target.getUniqueId())) {
			plugin.langManager().send(player, "marriage.target-already-married");
			return;
		}

		if (marriageRequestsDisabled.contains(target.getUniqueId())) {
			plugin.langManager().send(player, "requests.blocked");
			return;
		}

		if (blockedMarriageRequests.contains(blockKey(target.getUniqueId(), player.getUniqueId()))) {
			plugin.langManager().send(player, "requests.blocked");
			return;
		}

		FamilyMarriageCheckResult familyMarriageCheckResult = plugin.familyManager().checkMarriageAllowed(player, target);

		if (familyMarriageCheckResult == FamilyMarriageCheckResult.BLOCKED) {
			plugin.langManager().send(player, "family.marriage-blocked-related");
			return;
		}

		if (familyMarriageCheckResult == FamilyMarriageCheckResult.WARN) {
			plugin.langManager().send(player, "family.marriage-warning-related");
			plugin.langManager().send(target, "family.marriage-warning-related");
		}

		UUID existingProposerId = proposals.get(target.getUniqueId());

		if (existingProposerId != null) {
			if (existingProposerId.equals(player.getUniqueId())) {
				plugin.langManager().send(player, "requests.already-sent", Map.of(
						"%player%", target.getName()
				));
				return;
			}

			plugin.langManager().send(player, "requests.target-has-pending", Map.of(
					"%player%", target.getName()
			));
			return;

		}

		proposals.put(target.getUniqueId(), player.getUniqueId());
		setProposalCooldown(player);

		int expireSeconds = plugin.getConfig().getInt("settings.proposal-expire-seconds", 60);

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			UUID proposerId = proposals.get(target.getUniqueId());

			if (proposerId != null && proposerId.equals(player.getUniqueId())) {
				proposals.remove(target.getUniqueId());

				if (player.isOnline()) {
					plugin.langManager().send(player, "requests.expired-sender", Map.of(
							"%player%", target.getName()
					));
				}

				if (target.isOnline()) {
					plugin.langManager().send(target, "requests.expired-receiver", Map.of(
							"%player%", player.getName()
					));
				}
			}
		}, Math.max(1L, expireSeconds) * 20L);

		plugin.langManager().send(player, "requests.sent", Map.of(
				"%player%", target.getName()
		));
		plugin.langManager().send(target, "requests.received", Map.of(
				"%player%", player.getName()
		));
		sendProposalButtons(target, player, expireSeconds);
		plugin.langManager().send(target, "requests.expires-in", Map.of(
				"%seconds%", String.valueOf(expireSeconds)
		));
	}

	private boolean isOnProposalCooldown(Player player) {
		long expiresAt = proposalCooldowns.getOrDefault(player.getUniqueId(), 0L);

		if (expiresAt <= System.currentTimeMillis()) {
			proposalCooldowns.remove(player.getUniqueId());
			return false;
		}

		long seconds = Math.max(1L, (expiresAt - System.currentTimeMillis()) / 1000L);

		plugin.langManager().send(player, "requests.cooldown", Map.of(
				"%seconds%", String.valueOf(seconds)
		));

		return true;
	}

	private void setProposalCooldown(Player player) {
		int cooldownSeconds = plugin.getConfig().getInt("settings.proposal-cooldown-seconds", 15);

		if (cooldownSeconds <= 0) {
			return;
		}

		proposalCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
	}

	private void sendProposalButtons(Player target, Player proposer, int expireSeconds) {
		Component acceptButton = legacy(plugin.langManager().get("requests.accept-button"))
				.clickEvent(ClickEvent.runCommand("/marry accept"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("requests.accept-hover", Map.of(
						"%player%", proposer.getName()
				)))));

		Component denyButton = legacy(plugin.langManager().get("requests.deny-button"))
				.clickEvent(ClickEvent.runCommand("/marry deny"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("requests.deny-hover", Map.of(
						"%player%", proposer.getName()
				)))));

		target.sendMessage(legacy(plugin.langManager().get("requests.buttons-prefix"))
				.append(acceptButton)
				.append(legacy(" "))
				.append(denyButton)
				.append(legacy(plugin.langManager().get("requests.buttons-expire", Map.of(
						"%seconds%", String.valueOf(expireSeconds)
				)))));
	}

	private void sendIncomingRequestDashboardButtons(Player player, String proposerName) {
		Component acceptButton = legacy(plugin.langManager().get("requests.accept-button"))
				.clickEvent(ClickEvent.runCommand("/marry accept"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("requests.accept-hover", Map.of(
						"%player%", proposerName
				)))));

		Component denyButton = legacy(plugin.langManager().get("requests.deny-button"))
				.clickEvent(ClickEvent.runCommand("/marry deny"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("requests.deny-hover", Map.of(
						"%player%", proposerName
				)))));

		player.sendMessage(legacy(plugin.langManager().get("requests.dashboard-actions-prefix"))
				.append(acceptButton)
				.append(legacy(plugin.langManager().get("requests.dashboard-actions-separator")))
				.append(denyButton));
	}

	private void sendOutgoingRequestDashboardButton(Player player, String targetName) {
		Component cancelButton = legacy(plugin.langManager().get("requests.cancel-button"))
				.clickEvent(ClickEvent.runCommand("/marry requests cancel"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("requests.cancel-hover", Map.of(
						"%player%", targetName
				)))));

		player.sendMessage(legacy(plugin.langManager().get("requests.dashboard-actions-prefix"))
				.append(cancelButton));
	}

	public void acceptProposal(Player player) {
		UUID proposerId = proposals.remove(player.getUniqueId());

		if (proposerId == null) {
			plugin.langManager().send(player, "requests.no-pending");
			return;
		}

		Player proposer = Bukkit.getPlayer(proposerId);

		if (proposer == null) {
			plugin.langManager().send(player, "general.player-not-online");
			return;
		}

		plugin.langManager().send(player, "requests.accepted");
		marriageManager.marryPlayers(proposer, player);
		// if (plugin.configs().rings().getBoolean("give-on-marriage", true)) {
		//	plugin.ringManager().giveRing(player, false);
		//	plugin.ringManager().giveRing(requester, false);
		//}
	}


	public void denyProposal(Player player) {
		UUID proposerId = proposals.remove(player.getUniqueId());

		if (proposerId == null) {
			plugin.langManager().send(player, "requests.no-pending");
			return;
		}

		Player proposer = Bukkit.getPlayer(proposerId);

		plugin.langManager().send(player, "requests.denied");

		if (proposer != null) {
			plugin.langManager().send(proposer, "requests.denied-sender", Map.of(
					"%player%", player.getName()
			));
		}
	}

	public void marriageChat(Player player, String[] args) {
		if (!marriageManager.isMarried(player.getUniqueId())) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("toggle")) {
			if (coupleChatToggled.remove(player.getUniqueId())) {
				plugin.langManager().send(player, "chat.toggled-off");
			} else {
				coupleChatToggled.add(player.getUniqueId());
				plugin.langManager().send(player, "chat.toggled-on");
			}

			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("color")) {
			chatColorCommand(player, args);
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("prefix")) {
			chatPrefixCommand(player, args);
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(player, "chat.usage");
			plugin.langManager().send(player, "chat.extra");
			return;
		}

		sendCoupleChat(player, String.join(" ", List.of(args).subList(1, args.length)));
	}

	public void sendCoupleChat(Player player, String message) {
		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		String playerColor = chatColors.getOrDefault(
				player.getUniqueId(),
				plugin.getConfig().getString("chat.default-color", "&f")
		);

		String format = chatPrefixDisabled.contains(player.getUniqueId())
				? "%color%%message%"
				: plugin.getConfig().getString("chat.format", "&d[Marriage Chat] &f%player%&7: %color%%message%");

		String formatted = applyChatPlaceholders(format, player, partner, message, playerColor);

		player.sendMessage(color(formatted));
		partner.sendMessage(color(formatted));
		plugin.marriageXpManager().addXp(player, "chat");
		plugin.achievementManager().unlockByTrigger(player, "chat");

		String spyFormat = plugin.getConfig().getString(
				"chat.spy-format",
				"&8[Marriage Spy] &f%player% &7-> &f%partner%&7: &f%message%"
		);

		String spyMessage = applyChatPlaceholders(spyFormat, player, partner, message, playerColor);

		for (UUID listenerId : listeningToMarriageChat) {
			Player listener = Bukkit.getPlayer(listenerId);

			if (listener != null
					&& !listener.getUniqueId().equals(player.getUniqueId())
					&& !listener.getUniqueId().equals(partner.getUniqueId())) {
				listener.sendMessage(color(spyMessage));
			}
		}
	}

	private String applyChatPlaceholders(String format, Player player, Player partner, String message, String playerColor) {
		return format
				.replace("%player%", player.getName())
				.replace("%partner%", partner.getName())
				.replace("%player_display%", plugin.socialManager().getPartnerDisplayName(partner.getUniqueId(), player.getUniqueId()))
				.replace("%partner_display%", plugin.socialManager().getPartnerDisplayName(player.getUniqueId(), partner.getUniqueId()))
				.replace("%color%", playerColor)
				.replace("%message%", message);
	}

	public void toggleListenChat(Player player) {
		if (!player.hasPermission("marriageplus.admin")) {
			plugin.langManager().send(player, "general.no-permission");
			return;
		}

		if (listeningToMarriageChat.remove(player.getUniqueId())) {
			plugin.langManager().send(player, "chat.spy-disabled");
		} else {
			listeningToMarriageChat.add(player.getUniqueId());
			plugin.langManager().send(player, "chat.spy-enabled");
		}
	}

	public void requestsCommand(Player player, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("pending")) {
			showPendingProposal(player);
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
			cancelOutgoingProposal(player);
			return;
		}

		if (args.length < 2) {
			showRequestStatus(player);
			return;
		}

		if (args[1].equalsIgnoreCase("off")) {
			marriageRequestsDisabled.add(player.getUniqueId());
			plugin.dataManager().saveData();
			plugin.langManager().send(player, "requests.requests-disabled");
			return;
		}

		if (args[1].equalsIgnoreCase("on")) {
			marriageRequestsDisabled.remove(player.getUniqueId());
			plugin.dataManager().saveData();
			plugin.langManager().send(player, "requests.requests-enabled");
			return;
		}

		plugin.langManager().send(player, "requests.toggle-usage-error");
	}

	private void showRequestStatus(Player player) {
		String status = plugin.langManager().get(marriageRequestsDisabled.contains(player.getUniqueId())
				? "requests.status-disabled"
				: "requests.status-enabled");

		UUID incomingProposerId = proposals.get(player.getUniqueId());
		String incoming = incomingProposerId == null
				? plugin.langManager().get("general.none")
				: safeName(Bukkit.getOfflinePlayer(incomingProposerId));

		UUID outgoingTargetId = findOutgoingProposalTarget(player.getUniqueId());
		String outgoing = outgoingTargetId == null
				? plugin.langManager().get("general.none")
				: safeName(Bukkit.getOfflinePlayer(outgoingTargetId));

		plugin.langManager().send(player, "requests.status-header");
		plugin.langManager().send(player, "requests.status", Map.of(
				"%status%", status
		));
		plugin.langManager().send(player, "requests.status-incoming", Map.of(
				"%player%", incoming
		));

		if (incomingProposerId != null) {
			sendIncomingRequestDashboardButtons(player, incoming);
		}

		plugin.langManager().send(player, "requests.status-outgoing", Map.of(
				"%player%", outgoing
		));

		if (outgoingTargetId != null) {
			sendOutgoingRequestDashboardButton(player, outgoing);
		}

		plugin.langManager().send(player, "requests.toggle-usage");
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plugin.langManager().get("general.unknown") : player.getName();
	}

	private void showPendingProposal(Player player) {
		UUID incomingProposerId = proposals.get(player.getUniqueId());
		UUID outgoingTargetId = findOutgoingProposalTarget(player.getUniqueId());

		if (incomingProposerId == null && outgoingTargetId == null) {
			plugin.langManager().send(player, "requests.no-pending");
			return;
		}

		plugin.langManager().send(player, "requests.pending-header");

		if (incomingProposerId != null) {
			OfflinePlayer proposer = Bukkit.getOfflinePlayer(incomingProposerId);
			String proposerName = safeName(proposer);

			plugin.langManager().send(player, "requests.pending-incoming-line", Map.of(
					"%player%", proposerName
			));

			sendIncomingRequestDashboardButtons(player, proposerName);
		}

		if (outgoingTargetId != null) {
			OfflinePlayer target = Bukkit.getOfflinePlayer(outgoingTargetId);
			String targetName = safeName(target);

			plugin.langManager().send(player, "requests.pending-outgoing-line", Map.of(
					"%player%", targetName
			));

			sendOutgoingRequestDashboardButton(player, targetName);
		}
	}

	private UUID findOutgoingProposalTarget(UUID proposerId) {
		for (Map.Entry<UUID, UUID> entry : proposals.entrySet()) {
			if (entry.getValue().equals(proposerId)) {
				return entry.getKey();
			}
		}

		return null;
	}

	public void blockCommand(Player player, String[] args) {
		if (args.length < 2) {
			plugin.langManager().send(player, "requests.block-usage");
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

		if (target.getUniqueId().equals(player.getUniqueId())) {
			plugin.langManager().send(player, "requests.cannot-block-yourself");
			return;
		}

		blockedMarriageRequests.add(blockKey(player.getUniqueId(), target.getUniqueId()));
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "requests.player-blocked", Map.of(
				"%player%", args[1]
		));
	}

	private void cancelOutgoingProposal(Player player) {
		UUID targetId = findOutgoingProposalTarget(player.getUniqueId());

		if (targetId == null) {
			plugin.langManager().send(player, "requests.no-outgoing");
			return;
		}

		proposals.remove(targetId);

		Player onlineTarget = Bukkit.getPlayer(targetId);
		String targetName = safeName(Bukkit.getOfflinePlayer(targetId));

		plugin.langManager().send(player, "requests.cancelled-outgoing", Map.of(
				"%player%", targetName
		));

		if (onlineTarget != null) {
			plugin.langManager().send(onlineTarget, "requests.cancelled-incoming", Map.of(
					"%player%", player.getName()
			));
		}
	}

	public void unblockCommand(Player player, String[] args) {
		if (args.length < 2) {
			plugin.langManager().send(player, "requests.unblock-usage");
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

		blockedMarriageRequests.remove(blockKey(player.getUniqueId(), target.getUniqueId()));
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "requests.player-unblocked", Map.of(
				"%player%", args[1]
		));
	}

	public void showBlocklist(Player player) {
		List<String> blockedNames = blockedMarriageRequests.stream()
				.filter(key -> key.startsWith(player.getUniqueId() + ":"))
				.map(key -> UUID.fromString(key.substring(key.indexOf(':') + 1)))
				.map(Bukkit::getOfflinePlayer)
				.map(offlinePlayer -> offlinePlayer.getName() == null ? offlinePlayer.getUniqueId().toString() : offlinePlayer.getName())
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		plugin.langManager().send(player, "requests.blocklist-header");

		if (blockedNames.isEmpty()) {
			plugin.langManager().send(player, "requests.blocklist-empty");
			return;
		}

		for (String blockedName : blockedNames) {
			plugin.langManager().send(player, "requests.blocklist-line", Map.of(
					"%player%", blockedName
			));
		}
	}

	public String blockKey(UUID blocker, UUID blocked) {
		return blocker + ":" + blocked;
	}

	private void chatColorCommand(Player player, String[] args) {
		if (!plugin.getConfig().getBoolean("chat.allow-custom-colors", true)) {
			plugin.langManager().send(player, "chat.custom-colors-disabled");
			return;
		}

		if (args.length < 3) {
			plugin.langManager().send(player, "chat.color-usage");
			plugin.langManager().send(player, "chat.color-examples");
			return;
		}

		if (args[2].equalsIgnoreCase("reset")) {
			chatColors.remove(player.getUniqueId());
			plugin.dataManager().saveData();
			plugin.langManager().send(player, "chat.color-reset");
			return;
		}

		String selectedColor = args[2];

		if (!isAllowedChatColor(selectedColor)) {
			plugin.langManager().send(player, "chat.invalid-color");
			return;
		}

		chatColors.put(player.getUniqueId(), selectedColor);
		plugin.dataManager().saveData();

		plugin.langManager().send(player, "chat.color-set", Map.of(
				"%color%", selectedColor
		));
	}

	private void chatPrefixCommand(Player player, String[] args) {
		if (args.length < 3) {
			plugin.langManager().send(player, "chat.prefix-usage");
			return;
		}

		if (args[2].equalsIgnoreCase("off")) {
			chatPrefixDisabled.add(player.getUniqueId());
			plugin.dataManager().saveData();
			plugin.langManager().send(player, "chat.prefix-off");
			return;
		}

		if (args[2].equalsIgnoreCase("on")) {
			chatPrefixDisabled.remove(player.getUniqueId());
			plugin.dataManager().saveData();
			plugin.langManager().send(player, "chat.prefix-on");
			return;
		}

		plugin.langManager().send(player, "chat.prefix-usage");
	}

	private boolean isAllowedChatColor(String input) {
		return input.matches("&[0-9a-fA-F]")
				|| input.matches("&#[A-Fa-f0-9]{6}")
				|| input.matches("<#[A-Fa-f0-9]{6}>");
	}

	public Map<UUID, String> chatColors() {
		return chatColors;
	}

	public Set<UUID> chatPrefixDisabled() {
		return chatPrefixDisabled;
	}
}