package com.okbeanok.marriagePlus.services.families;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.Family;
import com.okbeanok.marriagePlus.services.MarriageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;
import static com.okbeanok.marriagePlus.utils.TextUtils.legacy;

public class FamilyManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final FamilyWebExporter webExporter;

	private final Map<String, Family> families = new HashMap<>();
	private final Map<UUID, String> playerFamilies = new HashMap<>();
	private final Map<UUID, FamilyInvite> invites = new HashMap<>();
	private final Map<UUID, Long> leaveConfirmations = new HashMap<>();
	private final Map<UUID, PendingKick> kickConfirmations = new HashMap<>();


	public FamilyManager(MarriagePlus plugin, MarriageManager marriageManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.webExporter = new FamilyWebExporter(plugin, this);
	}

	public void familyCommand(Player player, String[] args) {
		if (!plugin.configs().families().getBoolean("enabled", true)) {
			plugin.langManager().send(player, "family.disabled");
			return;
		}

		args = normalizeFamilyArgs(args);

		if (args.length == 0) {
			showFamily(player);
			return;
		}

		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "help" -> sendFamilyHelp(player);
			case "invite", "adopt" -> invite(player, args);
			case "accept" -> accept(player);
			case "deny", "decline" -> denyInvite(player);
			case "leave" -> leave(player, args);
			case "kick" -> kick(player, args);
			case "rename" -> rename(player, args);
			case "chat" -> chat(player, args);
			case "tree", "web" -> web(player);
			case "info" -> info(player, args);
			case "members" -> members(player);
			case "requests" -> requests(player);
			default -> showFamily(player);
		}
	}

	private void requests(Player player) {
		FamilyInvite invite = invites.get(player.getUniqueId());

		if (invite == null) {
			plugin.langManager().send(player, "family.no-invite");
			return;
		}

		if (invite.expired()) {
			invites.remove(player.getUniqueId());
			plugin.langManager().send(player, "family.invite-expired");
			return;
		}

		Family family = families.get(invite.familyId());

		if (family == null) {
			invites.remove(player.getUniqueId());
			plugin.langManager().send(player, "family.not-found");
			return;
		}

		OfflinePlayer inviter = Bukkit.getOfflinePlayer(invite.inviter());

		plugin.langManager().send(player, "family.requests-header");
		plugin.langManager().send(player, "family.requests-line", Map.of(
				"%player%", safeName(inviter),
				"%family%", family.name()
		));

		sendInviteButtons(player);
	}


	private String[] normalizeFamilyArgs(String[] args) {
		if (args.length == 0) {
			return args;
		}

		if (!args[0].equalsIgnoreCase("family")) {
			return args;
		}

		return Arrays.copyOfRange(args, 1, args.length);
	}

	private void sendFamilyHelp(Player player) {
		player.sendMessage(color("&d&m                              "));
		player.sendMessage(color("&d&lFamily Commands"));

		sendCommandHelp(player, "/family", "View your family profile.");
		sendCommandHelp(player, "/family invite <player>", "Invite/adopt a player into your family.");
		sendCommandHelp(player, "/family accept", "Accept a family invite.");
		sendCommandHelp(player, "/family deny", "Deny a family invite.");
		sendCommandHelp(player, "/family requests", "View pending family invites.");
		sendCommandHelp(player, "/family leave", "Leave your family.");
		sendCommandHelp(player, "/family kick <player>", "Kick a family member.");
		sendCommandHelp(player, "/family rename <name>", "Rename your family.");
		sendCommandHelp(player, "/family info [player]", "View a family member's details.");
		sendCommandHelp(player, "/family members", "List all family members.");
		sendCommandHelp(player, "/family chat <message>", "Send a family chat message.");
		sendCommandHelp(player, "/family tree", "Open your family tree webpage.");
		sendCommandHelp(player, "/family web", "Open your family tree webpage.");

		player.sendMessage(color("&d&m                              "));
	}

	private void members(Player player) {
		Family family = getFamily(player.getUniqueId());

		if (family == null) {
			plugin.langManager().send(player, "family.not-in-family");
			return;
		}

		List<String> parents = List.of(family.parentOne(), family.parentTwo()).stream()
				.map(this::formattedFamilyMemberName)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		List<String> descendants = family.childParents().keySet().stream()
				.map(this::formattedFamilyMemberName)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		List<String> others = otherFamilyMembers(family).stream()
				.map(this::formattedFamilyMemberName)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		plugin.langManager().send(player, "family.members-header", Map.of(
				"%family%", family.name(),
				"%count%", String.valueOf(family.members().size())
		));

		plugin.langManager().send(player, "family.members-parents", Map.of(
				"%members%", parents.isEmpty() ? plugin.langManager().get("general.none") : String.join(", ", parents)
		));

		plugin.langManager().send(player, "family.members-descendants", Map.of(
				"%members%", descendants.isEmpty() ? plugin.langManager().get("general.none") : String.join(", ", descendants)
		));

		plugin.langManager().send(player, "family.members-other", Map.of(
				"%members%", others.isEmpty() ? plugin.langManager().get("general.none") : String.join(", ", others)
		));
	}

	private String formattedFamilyMemberName(UUID playerId) {
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
		boolean online = Bukkit.getPlayer(playerId) != null;
		String status = plugin.langManager().get(online ? "family.online" : "family.offline");

		return safeName(offlinePlayer) + " " + status;
	}

	private void info(Player viewer, String[] args) {
		OfflinePlayer target = viewer;

		if (args.length >= 2) {
			target = Bukkit.getOfflinePlayer(args[1]);
		}

		Family family = getFamily(target.getUniqueId());

		if (family == null) {
			plugin.langManager().send(viewer, "family.info-not-in-family", Map.of(
					"%player%", safeName(target)
			));
			return;
		}

		List<String> parentNames = family.childParents().getOrDefault(target.getUniqueId(), Set.of()).stream()
				.map(parentId -> safeName(Bukkit.getOfflinePlayer(parentId)))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		List<String> childNames = childrenOf(family, target.getUniqueId()).stream()
				.map(childId -> safeName(Bukkit.getOfflinePlayer(childId)))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		UUID partnerId = marriageManager.getPartnerId(target.getUniqueId());
		String partnerName = partnerId == null
				? plugin.langManager().get("general.none")
				: safeName(Bukkit.getOfflinePlayer(partnerId));

		plugin.langManager().send(viewer, "family.info-header");
		plugin.langManager().send(viewer, "family.info-player", Map.of(
				"%player%", safeName(target)
		));
		plugin.langManager().send(viewer, "family.info-family", Map.of(
				"%family%", family.name()
		));
		plugin.langManager().send(viewer, "family.info-role", Map.of(
				"%role%", familyRole(family, target.getUniqueId())
		));
		plugin.langManager().send(viewer, "family.info-parents", Map.of(
				"%parents%", parentNames.isEmpty() ? plugin.langManager().get("general.none") : String.join(", ", parentNames)
		));
		plugin.langManager().send(viewer, "family.info-children", Map.of(
				"%children%", childNames.isEmpty() ? plugin.langManager().get("general.none") : String.join(", ", childNames)
		));
		plugin.langManager().send(viewer, "family.info-partner", Map.of(
				"%partner%", partnerName
		));
	}

	private String familyRole(Family family, UUID playerId) {
		if (family.parentOne().equals(playerId) || family.parentTwo().equals(playerId)) {
			return plugin.langManager().get("family.roles.parent");
		}

		if (family.adoptedChildren().contains(playerId)) {
			return plugin.langManager().get("family.roles.adopted-child");
		}

		if (family.members().contains(playerId)) {
			return plugin.langManager().get("family.roles.member");
		}

		return plugin.langManager().get("general.unknown");
	}

	private void sendCommandHelp(Player player, String command, String description) {
		Component component = legacy(color("&d" + command + " &8- &7" + description))
				.clickEvent(ClickEvent.suggestCommand(command))
				.hoverEvent(HoverEvent.showText(legacy(color("&7Click to suggest &f" + command))));

		player.sendMessage(component);
	}

	private void invite(Player parent, String[] args) {
		if (args.length < 2) {
			plugin.langManager().send(parent, "family.usage-invite");
			return;
		}

		Player target = Bukkit.getPlayerExact(args[1]);

		if (target == null) {
			plugin.langManager().send(parent, "general.player-not-online");
			return;
		}

		if (target.getUniqueId().equals(parent.getUniqueId())) {
			plugin.langManager().send(parent, "family.cannot-invite-self");
			return;
		}

		UUID partnerId = marriageManager.getPartnerId(parent.getUniqueId());

		if (plugin.configs().families().getBoolean("adoption.prevent-adopting-partner", true)
				&& partnerId != null
				&& partnerId.equals(target.getUniqueId())) {
			plugin.langManager().send(parent, "family.cannot-adopt-partner");
			return;
		}

		if (playerFamilies.containsKey(target.getUniqueId())) {
			plugin.langManager().send(parent, "family.already-in-family");
			return;
		}

		Family family = getFamily(parent.getUniqueId());

		if (family == null) {
			if (partnerId == null) {
				plugin.langManager().send(parent, "marriage.not-married");
				return;
			}

			family = getOrCreateParentFamily(parent.getUniqueId(), partnerId);
		}

		boolean allowMemberAdoption = plugin.configs().families().getBoolean("adoption.allow-member-adoption", true);

		if (!family.isParent(parent.getUniqueId()) && !allowMemberAdoption) {
			plugin.langManager().send(parent, "family.not-parent");
			return;
		}

		if (!family.isMember(parent.getUniqueId())) {
			plugin.langManager().send(parent, "family.not-in-family");
			return;
		}

		boolean requireMarriedParent = plugin.configs().families().getBoolean("adoption.require-married-parent", false);

		if (requireMarriedParent && partnerId == null) {
			plugin.langManager().send(parent, "family.adoption-requires-marriage");
			return;
		}

		Set<UUID> parentIds = adoptionParentIds(family, parent.getUniqueId());

		if (plugin.configs().families().getBoolean("adoption.prevent-cycles", true)
				&& adoptionWouldCreateCycle(parentIds, target.getUniqueId())) {
			plugin.langManager().send(parent, "family.adoption-would-create-cycle");
			return;
		}

		int maxMembers = plugin.configs().families().getInt("max-members", 8);

		if (family.members().size() >= maxMembers) {
			plugin.langManager().send(parent, "family.full");
			return;
		}

		long expiresAt = System.currentTimeMillis()
				+ plugin.configs().families().getInt("invite-expire-seconds", 60) * 1000L;

		invites.put(target.getUniqueId(), new FamilyInvite(
				family.id(),
				parent.getUniqueId(),
				target.getUniqueId(),
				expiresAt
		));

		Bukkit.getScheduler().runTaskLater(
				plugin,
				() -> expireInvite(target.getUniqueId(), parent.getUniqueId()),
				Math.max(1L, plugin.configs().families().getInt("invite-expire-seconds", 60)) * 20L
		);

		plugin.langManager().send(parent, "family.invite-sent", Map.of(
				"%player%", target.getName(),
				"%family%", family.name()
		));

		plugin.langManager().send(target, "family.invite-received", Map.of(
				"%player%", parent.getName(),
				"%family%", family.name()
		));

		sendInviteButtons(target);
	}

	private void sendInviteButtons(Player target) {
		Component buttons = legacy(color("&7Respond: "))
				.append(legacy(color("&a&l[ACCEPT]"))
						.clickEvent(ClickEvent.runCommand("/family accept"))
						.hoverEvent(HoverEvent.showText(legacy(color("&aAccept this family invite")))))
				.append(legacy(color(" &7| ")))
				.append(legacy(color("&c&l[DENY]"))
						.clickEvent(ClickEvent.runCommand("/family deny"))
						.hoverEvent(HoverEvent.showText(legacy(color("&cDeny this family invite")))));

		target.sendMessage(buttons);
	}

	private void expireInvite(UUID targetId, UUID inviterId) {
		FamilyInvite invite = invites.get(targetId);

		if (invite == null || !invite.inviter().equals(inviterId)) {
			return;
		}

		if (!invite.expired()) {
			return;
		}

		invites.remove(targetId);

		Player target = Bukkit.getPlayer(targetId);
		Player inviter = Bukkit.getPlayer(inviterId);

		if (target != null) {
			plugin.langManager().send(target, "family.invite-expired");
		}

		if (inviter != null) {
			plugin.langManager().send(inviter, "family.invite-expired-sender", Map.of(
					"%player%", target == null ? "Unknown" : target.getName()
			));
		}
	}

	private void accept(Player player) {
		FamilyInvite invite = invites.remove(player.getUniqueId());

		if (invite == null) {
			plugin.langManager().send(player, "family.no-invite");
			return;
		}

		if (invite.expired()) {
			plugin.langManager().send(player, "family.invite-expired");
			return;
		}

		if (playerFamilies.containsKey(player.getUniqueId())) {
			plugin.langManager().send(player, "family.already-in-family");
			return;
		}

		Family family = families.get(invite.familyId());

		if (family == null) {
			plugin.langManager().send(player, "family.not-found");
			return;
		}

		Player inviter = Bukkit.getPlayer(invite.inviter());

		if (inviter == null) {
			plugin.langManager().send(player, "general.player-not-online");
			return;
		}

		if (!family.isMember(invite.inviter())) {
			plugin.langManager().send(player, "family.not-found");
			return;
		}

		UUID inviterPartnerId = marriageManager.getPartnerId(invite.inviter());

		if (plugin.configs().families().getBoolean("adoption.prevent-adopting-partner", true)
				&& inviterPartnerId != null
				&& inviterPartnerId.equals(player.getUniqueId())) {
			plugin.langManager().send(player, "family.cannot-adopt-partner");
			return;
		}

		int maxMembers = plugin.configs().families().getInt("max-members", 8);

		if (family.members().size() >= maxMembers) {
			plugin.langManager().send(player, "family.full");
			return;
		}

		Set<UUID> parentIds = adoptionParentIds(family, invite.inviter());

		if (parentIds.isEmpty()) {
			plugin.langManager().send(player, "family.not-found");
			return;
		}

		if (plugin.configs().families().getBoolean("adoption.prevent-cycles", true)
				&& adoptionWouldCreateCycle(parentIds, player.getUniqueId())) {
			plugin.langManager().send(player, "family.adoption-would-create-cycle");
			return;
		}

		if (plugin.configs().families().getBoolean("tree.permanent-adoption-links", true)) {
			family.addChild(player.getUniqueId(), parentIds);
		} else {
			family.members().add(player.getUniqueId());
		}

		playerFamilies.put(player.getUniqueId(), family.id());

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "family.joined", Map.of(
				"%family%", family.name()
		));

		broadcastToFamily(family, player.getUniqueId(), "family.member-joined", Map.of(
				"%player%", player.getName(),
				"%family%", family.name()
		));

		autoExportWeb();
	}

	private void denyInvite(Player player) {
		FamilyInvite invite = invites.remove(player.getUniqueId());

		if (invite == null) {
			plugin.langManager().send(player, "family.no-invite");
			return;
		}

		plugin.langManager().send(player, "family.invite-denied");

		Player inviter = Bukkit.getPlayer(invite.inviter());

		if (inviter != null) {
			plugin.langManager().send(inviter, "family.invite-denied-sender", Map.of(
					"%player%", player.getName()
			));
		}
	}
	private void leave(Player player, String[] args) {
		Family family = getFamily(player.getUniqueId());

		if (family == null) {
			plugin.langManager().send(player, "family.not-in-family");
			return;
		}

		if (family.isParent(player.getUniqueId())) {
			plugin.langManager().send(player, "family.parent-cannot-leave");
			return;
		}

		if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
			int seconds = plugin.configs().families().getInt("leave-confirm-seconds", 30);
			leaveConfirmations.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);

			plugin.langManager().send(player, "family.leave-confirm", Map.of(
					"%seconds%", String.valueOf(seconds)
			));
			sendLeaveButtons(player, seconds);
			return;
		}

		long expiresAt = leaveConfirmations.getOrDefault(player.getUniqueId(), 0L);

		if (System.currentTimeMillis() > expiresAt) {
			leaveConfirmations.remove(player.getUniqueId());
			plugin.langManager().send(player, "family.leave-confirm-expired");
			return;
		}

		leaveConfirmations.remove(player.getUniqueId());

		family.removeMember(player.getUniqueId(), plugin.configs().families().getBoolean("keep-history", true));
		playerFamilies.remove(player.getUniqueId());

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "family.left");

		broadcastToFamily(family, player.getUniqueId(), "family.member-left", Map.of(
				"%player%", player.getName(),
				"%family%", family.name()
		));

		autoExportWeb();
	}

	private void sendLeaveButtons(Player player, int seconds) {
		Component confirmButton = legacy(plugin.langManager().get("family.leave-confirm-button"))
				.clickEvent(ClickEvent.runCommand("/family leave confirm"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("family.leave-confirm-hover"))));

		Component cancelButton = legacy(plugin.langManager().get("family.leave-cancel-button"))
				.clickEvent(ClickEvent.runCommand("/family"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("family.leave-cancel-hover"))));

		player.sendMessage(legacy(plugin.langManager().get("family.leave-buttons-prefix"))
				.append(confirmButton)
				.append(legacy(plugin.langManager().get("family.leave-buttons-separator")))
				.append(cancelButton)
				.append(legacy(plugin.langManager().get("family.leave-buttons-expire", Map.of(
						"%seconds%", String.valueOf(seconds)
				)))));
	}


	private void kick(Player parent, String[] args) {
		if (args.length < 2) {
			plugin.langManager().send(parent, "family.usage-kick");
			return;
		}

		Family family = getFamily(parent.getUniqueId());

		if (family == null) {
			plugin.langManager().send(parent, "family.not-in-family");
			return;
		}

		if (!family.isParent(parent.getUniqueId())) {
			plugin.langManager().send(parent, "family.not-parent");
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
		UUID targetId = target.getUniqueId();

		if (!family.isMember(targetId)) {
			plugin.langManager().send(parent, "family.not-family-member");
			return;
		}

		if (family.isParent(targetId)) {
			plugin.langManager().send(parent, "family.cannot-kick-parent");
			return;
		}

		if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
			int seconds = plugin.configs().families().getInt("kick-confirm-seconds", 30);

			kickConfirmations.put(parent.getUniqueId(), new PendingKick(
					targetId,
					family.id(),
					System.currentTimeMillis() + seconds * 1000L
			));

			plugin.langManager().send(parent, "family.kick-confirm", Map.of(
					"%player%", safeName(target),
					"%seconds%", String.valueOf(seconds)
			));

			sendKickButtons(parent, safeName(target), seconds);
			return;
		}

		PendingKick pendingKick = kickConfirmations.get(parent.getUniqueId());

		if (pendingKick == null
				|| !pendingKick.targetId().equals(targetId)
				|| !pendingKick.familyId().equals(family.id())) {
			plugin.langManager().send(parent, "family.kick-confirm-missing");
			return;
		}

		if (pendingKick.expired()) {
			kickConfirmations.remove(parent.getUniqueId());
			plugin.langManager().send(parent, "family.kick-confirm-expired");
			return;
		}

		kickConfirmations.remove(parent.getUniqueId());

		family.removeMember(targetId, plugin.configs().families().getBoolean("keep-history", true));
		playerFamilies.remove(targetId);

		plugin.dataManager().saveData();

		plugin.langManager().send(parent, "family.kicked", Map.of(
				"%player%", safeName(target)
		));

		Player kickedPlayer = Bukkit.getPlayer(targetId);

		if (kickedPlayer != null) {
			plugin.langManager().send(kickedPlayer, "family.you-were-kicked", Map.of(
					"%family%", family.name()
			));
		}

		broadcastToFamily(family, parent.getUniqueId(), "family.member-kicked", Map.of(
				"%player%", safeName(target),
				"%family%", family.name()
		));

		autoExportWeb();
	}

	private void sendKickButtons(Player parent, String targetName, int seconds) {
		Component confirmButton = legacy(plugin.langManager().get("family.kick-confirm-button"))
				.clickEvent(ClickEvent.runCommand("/family kick " + targetName + " confirm"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("family.kick-confirm-hover", Map.of(
						"%player%", targetName
				)))));

		Component cancelButton = legacy(plugin.langManager().get("family.kick-cancel-button"))
				.clickEvent(ClickEvent.runCommand("/family"))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("family.kick-cancel-hover"))));

		parent.sendMessage(legacy(plugin.langManager().get("family.kick-buttons-prefix"))
				.append(confirmButton)
				.append(legacy(plugin.langManager().get("family.kick-buttons-separator")))
				.append(cancelButton)
				.append(legacy(plugin.langManager().get("family.kick-buttons-expire", Map.of(
						"%seconds%", String.valueOf(seconds)
				)))));
	}

	private void rename(Player parent, String[] args) {
		if (args.length < 2) {
			plugin.langManager().send(parent, "family.usage-rename");
			return;
		}

		Family family = getFamily(parent.getUniqueId());

		if (family == null) {
			plugin.langManager().send(parent, "family.not-in-family");
			return;
		}

		if (!family.isParent(parent.getUniqueId())) {
			plugin.langManager().send(parent, "family.not-parent");
			return;
		}

		String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
		int maxLength = plugin.configs().families().getInt("max-name-length", 24);

		if (name.isBlank()) {
			plugin.langManager().send(parent, "family.usage-rename");
			return;
		}

		if (name.length() > maxLength) {
			plugin.langManager().send(parent, "family.name-too-long", Map.of(
					"%max%", String.valueOf(maxLength)
			));
			return;
		}

		family.setName(name);

		plugin.dataManager().saveData();

		plugin.langManager().send(parent, "family.renamed", Map.of(
				"%family%", name
		));

		broadcastToFamily(family, parent.getUniqueId(), "family.family-renamed", Map.of(
				"%player%", parent.getName(),
				"%family%", name
		));

		autoExportWeb();
	}

	private void chat(Player player, String[] args) {
		if (!plugin.configs().families().getBoolean("allow-family-chat", true)) {
			plugin.langManager().send(player, "family.chat-disabled");
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(player, "family.usage-chat");
			return;
		}

		Family family = getFamily(player.getUniqueId());

		if (family == null) {
			plugin.langManager().send(player, "family.not-in-family");
			return;
		}

		String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();

		if (message.isBlank()) {
			plugin.langManager().send(player, "family.usage-chat");
			return;
		}

		String format = plugin.configs().families().getString("chat.format", "&d[Family] &f%player%&7: &f%message%")
				.replace("%player%", player.getName())
				.replace("%message%", message)
				.replace("%family%", family.name());

		for (UUID memberId : family.members()) {
			Player member = Bukkit.getPlayer(memberId);

			if (member != null) {
				member.sendMessage(color(format));
			}
		}
	}

	private void web(Player player) {
		sendTreeLink(player);
	}

	private void sendTreeLink(Player player) {
		Family family = getFamily(player.getUniqueId());

		if (family == null) {
			plugin.langManager().send(player, "family.not-in-family");
			return;
		}

		if (!plugin.configs().families().getBoolean("web.enabled", true)) {
			player.sendMessage(color("&cFamily web trees are disabled."));
			return;
		}

		webExporter.exportFamily(family);

		String publicUrl = configuredPublicUrl();

		if (publicUrl.isBlank()) {
			player.sendMessage(color("&cFamily web public URL is not configured yet."));
			player.sendMessage(color("&7Set &fweb.public-url &7in &fconfigs/families.yml&7."));
			return;
		}

		String url = publicUrl + "/" + webExporter.familyFileName(family);

		Component message = legacy(color("&d&l[OPEN FAMILY TREE] &7Click to view your family tree."))
				.clickEvent(ClickEvent.openUrl(url))
				.hoverEvent(HoverEvent.showText(legacy(color("&7Open &f" + url))));

		player.sendMessage(message);
	}

	private String configuredPublicUrl() {
		return plugin.configs().families()
				.getString("web.public-url", "")
				.trim()
				.replaceAll("/+$", "");
	}

	private void showFamily(Player player) {
		Family family = getFamily(player.getUniqueId());

		if (family == null) {
			plugin.langManager().send(player, "family.not-in-family");
			return;
		}

		plugin.langManager().send(player, "family.profile-header", Map.of(
				"%family%", family.name()
		));

		plugin.langManager().send(player, "family.profile-parents", Map.of(
				"%parent_one%", safeName(Bukkit.getOfflinePlayer(family.parentOne())),
				"%parent_two%", safeName(Bukkit.getOfflinePlayer(family.parentTwo()))
		));

		plugin.langManager().send(player, "family.tree-header");

		List<UUID> rootChildren = rootChildren(family);

		if (rootChildren.isEmpty()) {
			plugin.langManager().send(player, "family.tree-empty");
		} else {
			Set<UUID> visited = new HashSet<>();

			for (UUID childId : rootChildren) {
				sendTreeLine(player, family, childId, 0, visited);
			}
		}

		List<String> otherMembers = otherFamilyMembers(family).stream()
				.map(memberId -> safeName(Bukkit.getOfflinePlayer(memberId)))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		plugin.langManager().send(player, "family.profile-other-members", Map.of(
				"%members%", otherMembers.isEmpty() ? plugin.langManager().get("general.none") : String.join(", ", otherMembers)
		));
	}

	private void sendTreeLine(Player viewer, Family family, UUID playerId, int depth, Set<UUID> visited) {
		if (!visited.add(playerId)) {
			return;
		}

		String indent = "  ".repeat(Math.max(0, depth));
		String prefix = depth == 0 ? "&d- " : "&7- ";
		String name = safeName(Bukkit.getOfflinePlayer(playerId));

		plugin.langManager().send(viewer, "family.tree-line", Map.of(
				"%indent%", indent,
				"%prefix%", color(prefix),
				"%player%", name
		));

		for (UUID childId : childrenOf(family, playerId)) {
			sendTreeLine(viewer, family, childId, depth + 1, visited);
		}
	}

	private List<UUID> rootChildren(Family family) {
		Set<UUID> rootParents = Set.of(family.parentOne(), family.parentTwo());

		return family.childParents().entrySet().stream()
				.filter(entry -> entry.getValue().stream().anyMatch(rootParents::contains))
				.map(Map.Entry::getKey)
				.sorted(this::comparePlayerNames)
				.toList();
	}

	private List<UUID> childrenOf(Family family, UUID parentId) {
		return family.childParents().entrySet().stream()
				.filter(entry -> entry.getValue().contains(parentId))
				.map(Map.Entry::getKey)
				.sorted(this::comparePlayerNames)
				.toList();
	}

	private List<UUID> otherFamilyMembers(Family family) {
		Set<UUID> linkedChildren = family.childParents().keySet();
		Set<UUID> rootParents = Set.of(family.parentOne(), family.parentTwo());

		return family.members().stream()
				.filter(memberId -> !rootParents.contains(memberId))
				.filter(memberId -> !linkedChildren.contains(memberId))
				.sorted(this::comparePlayerNames)
				.toList();
	}

	private int comparePlayerNames(UUID firstId, UUID secondId) {
		String firstName = safeName(Bukkit.getOfflinePlayer(firstId));
		String secondName = safeName(Bukkit.getOfflinePlayer(secondId));

		return firstName.compareToIgnoreCase(secondName);
	}

	private Set<UUID> adoptionParentIds(Family family, UUID adopterId) {
		Set<UUID> parentIds = new HashSet<>();

		if (family.isMember(adopterId)) {
			parentIds.add(adopterId);
		}

		UUID partnerId = marriageManager.getPartnerId(adopterId);

		if (partnerId != null && family.isMember(partnerId)) {
			parentIds.add(partnerId);
		}

		return parentIds;
	}

	private boolean adoptionWouldCreateCycle(Set<UUID> parentIds, UUID childId) {
		if (parentIds.contains(childId)) {
			return true;
		}

		for (UUID parentId : parentIds) {
			if (isAncestorOf(childId, parentId)) {
				return true;
			}
		}

		return false;
	}

	private Family getOrCreateParentFamily(UUID parentOne, UUID parentTwo) {
		Family existing = getFamily(parentOne);

		if (existing != null) {
			return existing;
		}

		String id = UUID.randomUUID().toString();

		OfflinePlayer first = Bukkit.getOfflinePlayer(parentOne);
		OfflinePlayer second = Bukkit.getOfflinePlayer(parentTwo);

		String defaultName = plugin.configs().families().getString("default-family-name", "%parent_one% & %parent_two%'s Family")
				.replace("%parent_one%", safeName(first))
				.replace("%parent_two%", safeName(second));

		Family family = new Family(
				id,
				parentOne,
				parentTwo,
				defaultName,
				new HashSet<>(),
				System.currentTimeMillis()
		);

		families.put(id, family);
		playerFamilies.put(parentOne, id);
		playerFamilies.put(parentTwo, id);

		plugin.dataManager().saveData();

		autoExportWeb();

		return family;
	}

	private void autoExportWeb() {
		if (plugin.configs().families().getBoolean("web.auto-export-on-save", false)) {
			webExporter.export();
		}
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? "Unknown" : player.getName();
	}

	public Family getFamily(UUID playerId) {
		String familyId = playerFamilies.get(playerId);

		if (familyId == null) {
			return null;
		}

		return families.get(familyId);
	}

	public Map<String, Family> families() {
		return families;
	}

	public Map<UUID, String> playerFamilies() {
		return playerFamilies;
	}

	public FamilyMarriageCheckResult checkMarriageAllowed(Player first, Player second) {
		if (!plugin.configs().families().getBoolean("relationships.enforce-marriage-rules", true)) {
			return FamilyMarriageCheckResult.ALLOWED;
		}

		if (plugin.configs().families().getBoolean("relationships.allow-incest", false)) {
			return FamilyMarriageCheckResult.ALLOWED;
		}

		if (plugin.configs().families().getBoolean("relationships.admin-bypass", true)) {
			String bypassPermission = plugin.configs().families().getString(
					"relationships.bypass-permission",
					"marriageplus.family.bypass"
			);

			if (first.hasPermission(bypassPermission) || second.hasPermission(bypassPermission)) {
				return FamilyMarriageCheckResult.ALLOWED;
			}
		}

		if (!areMarriageRestricted(first.getUniqueId(), second.getUniqueId())) {
			return FamilyMarriageCheckResult.ALLOWED;
		}

		String action = plugin.configs().families().getString("relationships.related-marriage-action", "BLOCK");

		if (action.equalsIgnoreCase("WARN")) {
			return FamilyMarriageCheckResult.WARN;
		}

		if (action.equalsIgnoreCase("ALLOW")) {
			return FamilyMarriageCheckResult.ALLOWED;
		}

		return FamilyMarriageCheckResult.BLOCKED;
	}

	private boolean areMarriageRestricted(UUID first, UUID second) {
		if (first.equals(second)) {
			return true;
		}

		Family firstFamily = getFamily(first);
		Family secondFamily = getFamily(second);

		if (firstFamily != null && secondFamily != null && firstFamily.id().equals(secondFamily.id())) {
			Family family = firstFamily;

			if (plugin.configs().families().getBoolean("relationships.block-same-family-marriage", true)) {
				return true;
			}

			if (plugin.configs().families().getBoolean("relationships.block-parent-child-marriage", true)
					&& areParentAndChild(first, second, family)) {
				return true;
			}
		}

		if (!plugin.configs().families().getBoolean("relationships.check-historical-family-links", true)
				&& (firstFamily == null || secondFamily == null || !firstFamily.id().equals(secondFamily.id()))) {
			return false;
		}

		if (plugin.configs().families().getBoolean("relationships.block-ancestor-descendant-marriage", true)
				&& areAncestorAndDescendant(first, second)) {
			return true;
		}

		if (plugin.configs().families().getBoolean("relationships.block-grandparent-grandchild-marriage", true)
				&& areGrandparentAndGrandchild(first, second)) {
			return true;
		}

		if (plugin.configs().families().getBoolean("relationships.block-sibling-marriage", true)
				&& areSiblings(first, second)) {
			return true;
		}

		if (plugin.configs().families().getBoolean("relationships.block-aunt-uncle-niece-nephew-marriage", true)
				&& areAuntUncleAndNieceNephew(first, second)) {
			return true;
		}

		if (plugin.configs().families().getBoolean("relationships.block-cousin-marriage", false)
				&& areFirstCousins(first, second)) {
			return true;
		}

		int maxRelationDistance = plugin.configs().families().getInt("relationships.block-related-within-distance", 0);

		return maxRelationDistance > 0 && areRelatedWithinDistance(first, second, maxRelationDistance);
	}

	private boolean areParentAndChild(UUID first, UUID second, Family family) {
		if (family.isChildOf(first, second) || family.isChildOf(second, first)) {
			return true;
		}

		if (family.isParent(first) && family.adoptedChildren().contains(second)) {
			return true;
		}

		return family.isParent(second) && family.adoptedChildren().contains(first);
	}

	private boolean areAncestorAndDescendant(UUID first, UUID second) {
		return isAncestorOf(first, second) || isAncestorOf(second, first);
	}

	private boolean areGrandparentAndGrandchild(UUID first, UUID second) {
		return getAncestorDistance(first, second) == 2 || getAncestorDistance(second, first) == 2;
	}

	private boolean areSiblings(UUID first, UUID second) {
		Set<UUID> firstParents = getParents(first);
		Set<UUID> secondParents = getParents(second);

		if (firstParents.isEmpty() || secondParents.isEmpty()) {
			return false;
		}

		for (UUID parentId : firstParents) {
			if (secondParents.contains(parentId)) {
				return true;
			}
		}

		return false;
	}

	private boolean areAuntUncleAndNieceNephew(UUID first, UUID second) {
		return isAuntOrUncleOf(first, second) || isAuntOrUncleOf(second, first);
	}

	private boolean isAuntOrUncleOf(UUID possibleAuntOrUncle, UUID possibleNieceOrNephew) {
		for (UUID parentId : getParents(possibleNieceOrNephew)) {
			if (areSiblings(possibleAuntOrUncle, parentId)) {
				return true;
			}
		}

		return false;
	}

	private boolean areFirstCousins(UUID first, UUID second) {
		Set<UUID> firstParents = getParents(first);
		Set<UUID> secondParents = getParents(second);

		if (firstParents.isEmpty() || secondParents.isEmpty()) {
			return false;
		}

		for (UUID firstParent : firstParents) {
			for (UUID secondParent : secondParents) {
				if (areSiblings(firstParent, secondParent)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean areRelatedWithinDistance(UUID first, UUID second, int maxDistance) {
		Map<UUID, Set<UUID>> graph = buildRelationshipGraph();
		Queue<FamilyDistanceNode> queue = new ArrayDeque<>();
		Set<UUID> visited = new HashSet<>();

		queue.add(new FamilyDistanceNode(first, 0));
		visited.add(first);

		while (!queue.isEmpty()) {
			FamilyDistanceNode current = queue.poll();

			if (current.distance() >= maxDistance) {
				continue;
			}

			for (UUID relative : graph.getOrDefault(current.playerId(), Set.of())) {
				if (relative.equals(second)) {
					return true;
				}

				if (visited.add(relative)) {
					queue.add(new FamilyDistanceNode(relative, current.distance() + 1));
				}
			}
		}

		return false;
	}

	private boolean isAncestorOf(UUID possibleAncestor, UUID possibleDescendant) {
		return getAncestorDistance(possibleAncestor, possibleDescendant) > 0;
	}

	private int getAncestorDistance(UUID possibleAncestor, UUID possibleDescendant) {
		Queue<FamilyDistanceNode> queue = new ArrayDeque<>();
		Set<UUID> visited = new HashSet<>();

		queue.add(new FamilyDistanceNode(possibleDescendant, 0));
		visited.add(possibleDescendant);

		while (!queue.isEmpty()) {
			FamilyDistanceNode current = queue.poll();

			for (UUID parentId : getParents(current.playerId())) {
				if (parentId.equals(possibleAncestor)) {
					return current.distance() + 1;
				}

				if (visited.add(parentId)) {
					queue.add(new FamilyDistanceNode(parentId, current.distance() + 1));
				}
			}
		}

		return -1;
	}

	private Set<UUID> getParents(UUID childId) {
		Set<UUID> parents = new HashSet<>();

		for (Family family : families.values()) {
			Set<UUID> familyParents = family.childParents().get(childId);

			if (familyParents != null) {
				parents.addAll(familyParents);
			}
		}

		return parents;
	}

	private Map<UUID, Set<UUID>> buildRelationshipGraph() {
		Map<UUID, Set<UUID>> graph = new HashMap<>();

		for (Family family : families.values()) {
			addGraphEdge(graph, family.parentOne(), family.parentTwo());

			for (UUID memberId : family.members()) {
				addGraphNode(graph, memberId);
			}

			for (UUID formerMemberId : family.formerMembers()) {
				addGraphNode(graph, formerMemberId);
			}

			for (Map.Entry<UUID, Set<UUID>> entry : family.childParents().entrySet()) {
				UUID childId = entry.getKey();
				addGraphNode(graph, childId);

				for (UUID parentId : entry.getValue()) {
					addGraphEdge(graph, childId, parentId);
				}
			}
		}

		return graph;
	}

	private void addGraphNode(Map<UUID, Set<UUID>> graph, UUID playerId) {
		graph.computeIfAbsent(playerId, ignored -> new HashSet<>());
	}

	private void addGraphEdge(Map<UUID, Set<UUID>> graph, UUID first, UUID second) {
		graph.computeIfAbsent(first, ignored -> new HashSet<>()).add(second);
		graph.computeIfAbsent(second, ignored -> new HashSet<>()).add(first);
	}

	public List<UUID> getChildren(UUID parentId) {
		List<UUID> children = new ArrayList<>();

		for (Family family : families.values()) {
			for (Map.Entry<UUID, Set<UUID>> entry : family.childParents().entrySet()) {
				if (entry.getValue().contains(parentId)) {
					children.add(entry.getKey());
				}
			}
		}

		return children;
	}

	public Set<UUID> getAncestors(UUID playerId) {
		Set<UUID> ancestors = new HashSet<>();
		Queue<UUID> queue = new ArrayDeque<>(getParents(playerId));

		while (!queue.isEmpty()) {
			UUID current = queue.poll();

			if (!ancestors.add(current)) {
				continue;
			}

			queue.addAll(getParents(current));
		}

		return ancestors;
	}

	private void broadcastToFamily(Family family, UUID excludedPlayerId, String langPath, Map<String, String> placeholders) {
		for (UUID memberId : family.members()) {
			if (memberId.equals(excludedPlayerId)) {
				continue;
			}

			Player member = Bukkit.getPlayer(memberId);

			if (member != null) {
				plugin.langManager().send(member, langPath, placeholders);
			}
		}
	}

	public Set<UUID> getDescendants(UUID playerId) {
		Set<UUID> descendants = new HashSet<>();
		Queue<UUID> queue = new ArrayDeque<>(getChildren(playerId));

		while (!queue.isEmpty()) {
			UUID current = queue.poll();

			if (!descendants.add(current)) {
				continue;
			}

			queue.addAll(getChildren(current));
		}

		return descendants;
	}

	private record FamilyDistanceNode(UUID playerId, int distance) {
	}

	private record PendingKick(UUID targetId, String familyId, long expiresAt) {

		private boolean expired() {
			return System.currentTimeMillis() > expiresAt;
		}
	}
}
