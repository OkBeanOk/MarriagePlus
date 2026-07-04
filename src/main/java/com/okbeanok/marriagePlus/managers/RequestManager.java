package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
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
			player.sendMessage(color("&cUsage: /marry me <player>"));
			return;
		}

		if (marriageManager.isMarried(player.getUniqueId())) {
			player.sendMessage(color("&cYou are already married."));
			return;
		}

		Player target = Bukkit.getPlayerExact(args[1]);

		if (target == null) {
			player.sendMessage(color("&cThat player is not online."));
			return;
		}

		if (target.getUniqueId().equals(player.getUniqueId())) {
			player.sendMessage(color("&cYou cannot marry yourself."));
			return;
		}

		if (marriageManager.isMarried(target.getUniqueId())) {
			player.sendMessage(color("&cThat player is already married."));
			return;
		}

		if (marriageRequestsDisabled.contains(target.getUniqueId())) {
			player.sendMessage(color("&cThat player is not accepting marriage requests."));
			return;
		}

		if (blockedMarriageRequests.contains(blockKey(target.getUniqueId(), player.getUniqueId()))) {
			player.sendMessage(color("&cThat player has blocked marriage requests from you."));
			return;
		}

		proposals.put(target.getUniqueId(), player.getUniqueId());

		int expireSeconds = plugin.getConfig().getInt("settings.proposal-expire-seconds", 60);

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			UUID proposerId = proposals.get(target.getUniqueId());

			if (proposerId != null && proposerId.equals(player.getUniqueId())) {
				proposals.remove(target.getUniqueId());

				if (player.isOnline()) {
					player.sendMessage(color("&eYour marriage request to &f" + target.getName() + " &eexpired."));
				}

				if (target.isOnline()) {
					target.sendMessage(color("&eThe marriage request from &f" + player.getName() + " &eexpired."));
				}
			}
		}, Math.max(1L, expireSeconds) * 20L);

		player.sendMessage(color("&aYou sent a marriage request to &f" + target.getName() + "&a."));
		target.sendMessage(color("&d" + player.getName() + " wants to marry you!"));
		sendProposalButtons(target, player, expireSeconds);
		target.sendMessage(color("&7This request expires in &f" + expireSeconds + " seconds&7."));
	}

	private void sendProposalButtons(Player target, Player proposer, int expireSeconds) {
		Component acceptButton = legacy("&a[Accept]")
				.clickEvent(ClickEvent.runCommand("/marry accept"))
				.hoverEvent(HoverEvent.showText(legacy("&aAccept " + proposer.getName() + "'s marriage request")));

		Component denyButton = legacy("&c[Deny]")
				.clickEvent(ClickEvent.runCommand("/marry deny"))
				.hoverEvent(HoverEvent.showText(legacy("&cDeny " + proposer.getName() + "'s marriage request")));

		target.sendMessage(legacy("&7Click: ")
				.append(acceptButton)
				.append(legacy(" "))
				.append(denyButton)
				.append(legacy(" &8| &7Expires in &f" + expireSeconds + "s")));
	}

	public void acceptProposal(Player player) {
		UUID proposerId = proposals.remove(player.getUniqueId());

		if (proposerId == null) {
			player.sendMessage(color("&cYou do not have a marriage request."));
			return;
		}

		Player proposer = Bukkit.getPlayer(proposerId);

		if (proposer == null) {
			player.sendMessage(color("&cThat player is no longer online."));
			return;
		}

		marriageManager.marryPlayers(proposer, player);
	}

	public void denyProposal(Player player) {
		UUID proposerId = proposals.remove(player.getUniqueId());

		if (proposerId == null) {
			player.sendMessage(color("&cYou do not have a marriage request."));
			return;
		}

		Player proposer = Bukkit.getPlayer(proposerId);
		player.sendMessage(color("&eYou denied the marriage request."));

		if (proposer != null) {
			proposer.sendMessage(color("&e" + player.getName() + " denied your marriage request."));
		}
	}

	public void marriageChat(Player player, String[] args) {
		if (!marriageManager.isMarried(player.getUniqueId())) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("toggle")) {
			if (coupleChatToggled.remove(player.getUniqueId())) {
				player.sendMessage(color("&eMarriage chat toggle disabled."));
			} else {
				coupleChatToggled.add(player.getUniqueId());
				player.sendMessage(color("&aMarriage chat toggle enabled."));
			}

			return;
		}

		if (args.length < 2) {
			player.sendMessage(color("&cUsage: /marry chat <message>"));
			return;
		}

		sendCoupleChat(player, String.join(" ", List.of(args).subList(1, args.length)));
	}

	public void sendCoupleChat(Player player, String message) {
		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		String formatted = color("&d[Marriage Chat] &f" + player.getName() + "&7: &f" + message);

		player.sendMessage(formatted);
		partner.sendMessage(formatted);

		for (UUID listenerId : listeningToMarriageChat) {
			Player listener = Bukkit.getPlayer(listenerId);

			if (listener != null
					&& !listener.getUniqueId().equals(player.getUniqueId())
					&& !listener.getUniqueId().equals(partner.getUniqueId())) {
				listener.sendMessage(color("&8[Marriage Spy] &f" + player.getName() + " -> " + partner.getName() + "&7: &f" + message));
			}
		}
	}

	public void toggleListenChat(Player player) {
		if (!player.hasPermission("marriageplus.admin")) {
			player.sendMessage(color("&cYou do not have permission."));
			return;
		}

		if (listeningToMarriageChat.remove(player.getUniqueId())) {
			player.sendMessage(color("&eMarriage chat spy disabled."));
		} else {
			listeningToMarriageChat.add(player.getUniqueId());
			player.sendMessage(color("&aMarriage chat spy enabled."));
		}
	}

	public void requestsCommand(Player player, String[] args) {
		if (args.length < 2) {
			player.sendMessage(color("&dMarriage requests are currently "
					+ (marriageRequestsDisabled.contains(player.getUniqueId()) ? "&cdisabled" : "&aenabled")
					+ "&d."));
			player.sendMessage(color("&7Use &f/marry requests on &7or &f/marry requests off&7."));
			return;
		}

		if (args[1].equalsIgnoreCase("off")) {
			marriageRequestsDisabled.add(player.getUniqueId());
			plugin.dataManager().saveData();
			player.sendMessage(color("&eYou are no longer accepting marriage requests."));
			return;
		}

		if (args[1].equalsIgnoreCase("on")) {
			marriageRequestsDisabled.remove(player.getUniqueId());
			plugin.dataManager().saveData();
			player.sendMessage(color("&aYou are now accepting marriage requests."));
			return;
		}

		player.sendMessage(color("&cUsage: /marry requests <on|off>"));
	}

	public void blockCommand(Player player, String[] args) {
		if (args.length < 2) {
			player.sendMessage(color("&cUsage: /marry block <player>"));
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

		if (target.getUniqueId().equals(player.getUniqueId())) {
			player.sendMessage(color("&cYou cannot block yourself."));
			return;
		}

		blockedMarriageRequests.add(blockKey(player.getUniqueId(), target.getUniqueId()));
		plugin.dataManager().saveData();

		player.sendMessage(color("&aBlocked marriage requests from &f" + args[1] + "&a."));
	}

	public void unblockCommand(Player player, String[] args) {
		if (args.length < 2) {
			player.sendMessage(color("&cUsage: /marry unblock <player>"));
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

		blockedMarriageRequests.remove(blockKey(player.getUniqueId(), target.getUniqueId()));
		plugin.dataManager().saveData();

		player.sendMessage(color("&eUnblocked marriage requests from &f" + args[1] + "&e."));
	}

	public void showBlocklist(Player player) {
		List<String> blockedNames = blockedMarriageRequests.stream()
				.filter(key -> key.startsWith(player.getUniqueId() + ":"))
				.map(key -> UUID.fromString(key.substring(key.indexOf(':') + 1)))
				.map(Bukkit::getOfflinePlayer)
				.map(offlinePlayer -> offlinePlayer.getName() == null ? offlinePlayer.getUniqueId().toString() : offlinePlayer.getName())
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		player.sendMessage(color("&dBlocked Marriage Requests:"));

		if (blockedNames.isEmpty()) {
			player.sendMessage(color("&7You have not blocked anyone."));
			return;
		}

		for (String blockedName : blockedNames) {
			player.sendMessage(color("&f- &d" + blockedName));
		}
	}

	public String blockKey(UUID blocker, UUID blocked) {
		return blocker + ":" + blocked;
	}
}