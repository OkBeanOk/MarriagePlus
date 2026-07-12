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
			case "leave" -> leave(player);
			case "kick" -> kick(player, args);
			case "rename" -> rename(player, args);
			case "chat" -> chat(player, args);
			case "tree" -> sendTreeLink(player);
			case "web" -> web(player, args);
			default -> {
				plugin.langManager().send(player, "family.usage");
				sendFamilyHelp(player);
			}
		}
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
		sendCommandHelp(player, "/family leave", "Leave your family.");
		sendCommandHelp(player, "/family kick <player>", "Kick a family member.");
		sendCommandHelp(player, "/family rename <name>", "Rename your family.");
		sendCommandHelp(player, "/family chat <message>", "Send a family chat message.");
		sendCommandHelp(player, "/family tree", "Open your family tree webpage.");
		sendCommandHelp(player, "/family web", "Open your family tree webpage.");

		if (player.hasPermission("marriageplus.admin")) {
			sendCommandHelp(player, "/family web export", "Export all family webpages.");
		}

		player.sendMessage(color("&d&m                              "));
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

		UUID partnerId = marriageManager.getPartnerId(parent.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(parent, "marriage.not-married");
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

		if (playerFamilies.containsKey(target.getUniqueId())) {
			plugin.langManager().send(parent, "family.already-in-family");
			return;
		}

		Family family = getOrCreateParentFamily(parent.getUniqueId(), partnerId);

		if (!family.isParent(parent.getUniqueId())) {
			plugin.langManager().send(parent, "family.not-parent");
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

		int maxMembers = plugin.configs().families().getInt("max-members", 8);

		if (family.members().size() >= maxMembers) {
			plugin.langManager().send(player, "family.full");
			return;
		}

		if (plugin.configs().families().getBoolean("tree.permanent-adoption-links", true)) {
			family.addChild(player.getUniqueId());
		} else {
			family.members().add(player.getUniqueId());
		}

		playerFamilies.put(player.getUniqueId(), family.id());

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "family.joined", Map.of(
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

		player.sendMessage(color("&cYou denied the family invite."));

		Player inviter = Bukkit.getPlayer(invite.inviter());

		if (inviter != null) {
			inviter.sendMessage(color("&c" + player.getName() + " denied your family invite."));
		}
	}

	private void leave(Player player) {
		Family family = getFamily(player.getUniqueId());

		if (family == null) {
			plugin.langManager().send(player, "family.not-in-family");
			return;
		}

		if (family.isParent(player.getUniqueId())) {
			plugin.langManager().send(player, "family.parent-cannot-leave");
			return;
		}

		boolean preserveHistory = plugin.configs().families().getBoolean("tree.preserve-family-history-on-leave", true);
		family.removeMember(player.getUniqueId(), preserveHistory);
		playerFamilies.remove(player.getUniqueId());

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "family.left");

		autoExportWeb();
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

		boolean preserveHistory = plugin.configs().families().getBoolean("tree.preserve-family-history-on-kick", true);
		family.removeMember(targetId, preserveHistory);
		playerFamilies.remove(targetId);

		plugin.dataManager().saveData();

		plugin.langManager().send(parent, "family.kicked", Map.of(
				"%player%", target.getName() == null ? args[1] : target.getName()
		));

		autoExportWeb();
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

	private void web(Player player, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("export")) {
			if (!player.hasPermission("marriageplus.admin")) {
				plugin.langManager().send(player, "general.no-permission");
				return;
			}

			webExporter.export();
			plugin.langManager().send(player, "family.web-exported");

			String publicUrl = configuredPublicUrl();

			if (!publicUrl.isBlank()) {
				Component message = legacy(color("&d&l[OPEN FAMILY WEB] &7Click to open the public family page."))
						.clickEvent(ClickEvent.openUrl(publicUrl))
						.hoverEvent(HoverEvent.showText(legacy(color("&7Open &f" + publicUrl))));

				player.sendMessage(message);
			}

			return;
		}

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
		player.sendMessage(color("&8" + url));
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
			sendCommandHelp(player, "/family help", "View family commands.");
			return;
		}

		OfflinePlayer parentOne = Bukkit.getOfflinePlayer(family.parentOne());
		OfflinePlayer parentTwo = Bukkit.getOfflinePlayer(family.parentTwo());

		player.sendMessage(color("&d&m                              "));
		player.sendMessage(color("&d&l" + family.name()));
		player.sendMessage(color("&7Parents: &f" + safeName(parentOne) + " &d❤ &f" + safeName(parentTwo)));
		player.sendMessage(color("&7Members: &f" + family.members().size()));

		Component actions = legacy(color("&7Actions: "))
				.append(actionButton("&d[TREE]", "/family tree", "&7Open your family tree"))
				.append(legacy(color(" ")))
				.append(actionButton("&b[CHAT]", "/family chat ", "&7Send a family chat message"))
				.append(legacy(color(" ")))
				.append(actionButton("&e[HELP]", "/family help", "&7View family commands"));

		player.sendMessage(actions);

		if (family.isParent(player.getUniqueId())) {
			Component parentActions = legacy(color("&7Parent Actions: "))
					.append(actionButton("&a[INVITE]", "/family invite ", "&7Invite/adopt a player"))
					.append(legacy(color(" ")))
					.append(actionButton("&6[RENAME]", "/family rename ", "&7Rename your family"))
					.append(legacy(color(" ")))
					.append(actionButton("&c[KICK]", "/family kick ", "&7Kick a family member"));

			player.sendMessage(parentActions);
		}

		player.sendMessage(color("&d&m                              "));
	}

	private Component actionButton(String text, String command, String hover) {
		return legacy(color(text))
				.clickEvent(ClickEvent.suggestCommand(command))
				.hoverEvent(HoverEvent.showText(legacy(color(hover))));
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
}