package com.okbeanok.marriagePlus.commands;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.HelpEntry;
import com.okbeanok.marriagePlus.services.CooldownManager;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.achievement.AchievementManager;
import com.okbeanok.marriagePlus.services.backpacks.BackpackManager;
import com.okbeanok.marriagePlus.services.families.FamilyMarriageCheckResult;
import com.okbeanok.marriagePlus.services.homes.HomeManager;
import com.okbeanok.marriagePlus.services.mail.MailManager;
import com.okbeanok.marriagePlus.services.profiles.ProfileManager;
import com.okbeanok.marriagePlus.services.pronouns.PronounManager;
import com.okbeanok.marriagePlus.services.quests.QuestManager;
import com.okbeanok.marriagePlus.services.request.RequestManager;
import com.okbeanok.marriagePlus.services.social.SocialManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import com.okbeanok.marriagePlus.utils.DataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;
import static com.okbeanok.marriagePlus.utils.TextUtils.legacy;

public class MarryCommand implements TabExecutor {

	private static final int HELP_ENTRIES_PER_PAGE = 8;

	private static final Set<String> RESERVED_COMMANDS = Set.of(
			"help", "menu", "gui", "me", "accept", "deny", "decline", "divorce", "list", "partner", "profile", "status",
			"quests", "tp", "sethome", "home", "homes", "delhome", "chat", "listenchat", "pvpon", "pvpoff",
			"announce", "compliment", "mail", "note", "lovenote", "lovenotes", "ring", "ceremony", "family",
			"leaderboard", "leaderboards", "top", "level", "xp", "achievements", "gift", "backpack",
			"anniversary", "pronouns", "title", "nickname", "requests", "block", "unblock", "blocklist",
			"priest", "reload"
	);

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final RequestManager requestManager;
	private final HomeManager homeManager;
	private final BackpackManager backpackManager;
	private final PronounManager pronounManager;
	private final SocialManager socialManager;
	private final CooldownManager cooldownManager;
	private final DataManager dataManager;
	private final MailManager mailManager;
	private final MarriageXpManager marriageXpManager;
	private final AchievementManager achievementManager;
	private final ProfileManager profileManager;
	private final QuestManager questManager;

	private final Map<UUID, Long> divorceConfirmations = new HashMap<>();

	public MarryCommand(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			RequestManager requestManager,
			HomeManager homeManager,
			BackpackManager backpackManager,
			PronounManager pronounManager,
			SocialManager socialManager,
			CooldownManager cooldownManager,
			DataManager dataManager,
			MailManager mailManager,
			MarriageXpManager marriageXpManager,
			AchievementManager achievementManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.requestManager = requestManager;
		this.homeManager = homeManager;
		this.backpackManager = backpackManager;
		this.pronounManager = pronounManager;
		this.socialManager = socialManager;
		this.cooldownManager = cooldownManager;
		this.dataManager = dataManager;
		this.mailManager = mailManager;
		this.marriageXpManager = marriageXpManager;
		this.achievementManager = achievementManager;
		this.profileManager = plugin.profileManager();
		this.questManager = plugin.questManager();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("marry")) {
			return false;
		}

		if (args.length == 0) {
			sendHelp(sender, 1);
			return true;
		}

		if (args.length == 2 && isPlayerName(args[0]) && isPlayerName(args[1])) {
			marryTwoPlayers(sender, args[0], args[1]);
			return true;
		}

		String subCommand = args[0].toLowerCase(Locale.ROOT);

		switch (subCommand) {
			case "help" -> sendHelp(sender, args);
			case "me" -> playerCommand(sender, "marriageplus.command.me", player -> requestManager.sendMarriageRequest(player, args));
			case "accept" -> requirePlayer(sender, requestManager::acceptProposal);
			case "deny", "decline" -> requirePlayer(sender, requestManager::denyProposal);
			case "divorce" -> handleDivorce(sender, args);
			case "list" -> command(sender, "marriageplus.command.list", () -> marriageManager.listMarriages(sender));
			case "partner" -> playerCommand(sender, "marriageplus.command.partner", this::showPartner);
			case "status" -> playerCommand(sender, "marriageplus.command.status", player -> handleStatus(player, args));
			case "quests" -> playerCommand(sender, "marriageplus.command.quests", player -> questManager.questsCommand(player, args));
			case "tp" -> playerCommand(sender, "marriageplus.command.tp", this::teleportToPartner);
			case "ceremony" -> playerCommand(sender, "marriageplus.command.ceremony", player -> plugin.ceremonyManager().ceremonyCommand(player, args));
			case "sethome" -> playerCommand(sender, "marriageplus.command.home", player -> homeManager.setHome(player, args));
			case "home" -> playerCommand(sender, "marriageplus.command.home", player -> homeManager.goHome(player, args));
			case "homes" -> playerCommand(sender, "marriageplus.command.home", player -> plugin.marriageGuiManager().openHomesMenu(player));
			case "chat" -> playerCommand(sender, "marriageplus.command.chat", player -> requestManager.marriageChat(player, args));
			case "listenchat" -> requirePlayer(sender, requestManager::toggleListenChat);
			case "profile" -> playerCommand(sender, "marriageplus.command.profile", player -> profileManager.profileCommand(player, args));
			case "pvpon" -> playerCommand(sender, "marriageplus.command.pvp", player -> marriageManager.setPartnerPvp(player, true));
			case "pvpoff" -> playerCommand(sender, "marriageplus.command.pvp", player -> marriageManager.setPartnerPvp(player, false));
			case "announce" -> playerCommand(sender, "marriageplus.command.announce", player -> announce(player, args));
			case "compliment" -> playerCommand(sender, "marriageplus.command.compliment", this::compliment);
			case "family" -> playerCommand(sender, "marriageplus.command.family", player -> plugin.familyManager().familyCommand(player, args));
			case "mail" -> playerCommand(sender, "marriageplus.command.mail", player -> mailManager.mailCommand(player, args));
			case "level" -> playerCommand(sender, "marriageplus.command.level", marriageXpManager::levelCommand);
			case "xp" -> playerCommand(sender, "marriageplus.command.level", marriageXpManager::xpCommand);
			case "leaderboard", "leaderboards", "top" -> playerCommand(sender, "marriageplus.command.leaderboard", player -> plugin.leaderboardManager().leaderboardCommand(player, args));
			case "achievements" -> playerCommand(sender, "marriageplus.command.achievements", player -> achievementManager.achievementsCommand(player, args));
			case "gift" -> playerCommand(sender, "marriageplus.command.gift", this::giftItem);
			case "backpack" -> playerCommand(sender, "marriageplus.command.backpack", player -> backpackManager.backpackCommand(player, args));
			case "anniversary" -> playerCommand(sender, "marriageplus.command.anniversary", this::showAnniversary);
			case "pronouns" -> playerCommand(sender, "marriageplus.command.pronouns", player -> pronounManager.pronounsCommand(player, args));
			case "title" -> playerCommand(sender, "marriageplus.command.title", player -> socialManager.titleCommand(player, args));
			case "ring" -> playerCommand(sender, "marriageplus.command.ring", player -> plugin.ringManager().ringCommand(player, args));
			case "note", "lovenote", "lovenotes" -> playerCommand(sender, "marriageplus.command.lovenotes", player -> plugin.loveNoteManager().noteCommand(player, args));
			case "nickname" -> playerCommand(sender, "marriageplus.command.nickname", player -> socialManager.nicknameCommand(player, args));
			case "requests" -> playerCommand(sender, "marriageplus.command.requests", player -> requestManager.requestsCommand(player, args));
			case "block" -> playerCommand(sender, "marriageplus.command.block", player -> requestManager.blockCommand(player, args));
			case "unblock" -> playerCommand(sender, "marriageplus.command.block", player -> requestManager.unblockCommand(player, args));
			case "blocklist" -> playerCommand(sender, "marriageplus.command.block", requestManager::showBlocklist);
			case "menu", "gui" -> playerCommand(sender, "marriageplus.command.menu", player -> plugin.marriageGuiManager().openMainMenu(player));
			case "priest" -> setPriest(sender, args);
			case "reload" -> reload(sender);
			default -> handleFallback(sender, subCommand);
		}

		return true;
	}

	private void handleFallback(CommandSender sender, String subCommand) {
		if (isConfiguredAction(subCommand)) {
			String permission = plugin.configs().actions().getString(subCommand + ".permission", "marriageplus.command.actions");
			playerCommand(sender, permission, player -> configuredInteraction(player, subCommand));
			return;
		}

		sendHelp(sender, 1);
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!command.getName().equalsIgnoreCase("marry")) {
			return Collections.emptyList();
		}

		return switch (args.length) {
			case 1 -> completeFirstArg(sender, args[0]);
			case 2 -> completeSecondArg(sender, args[0], args[1]);
			case 3 -> completeThirdArg(args[0], args[1], args[2]);
			case 4 -> completeFourthArg(args[0], args[1], args[3]);
			case 5 -> completeFifthArg(args[0], args[1], args[4]);
			default -> Collections.emptyList();
		};
	}

	private List<String> completeFirstArg(CommandSender sender, String input) {
		List<String> completions = new ArrayList<>(List.of("help", "accept", "deny"));

		Map<String, String> permissionCompletions = new LinkedHashMap<>();
		permissionCompletions.put("marriageplus.command.me", "me");
		permissionCompletions.put("marriageplus.command.menu", "menu");
		permissionCompletions.put("marriageplus.command.divorce", "divorce");
		permissionCompletions.put("marriageplus.command.list", "list");
		permissionCompletions.put("marriageplus.command.partner", "partner");
		permissionCompletions.put("marriageplus.command.profile", "profile");
		permissionCompletions.put("marriageplus.command.status", "status");
		permissionCompletions.put("marriageplus.command.quests", "quests");
		permissionCompletions.put("marriageplus.command.tp", "tp");
		permissionCompletions.put("marriageplus.command.chat", "chat");
		permissionCompletions.put("marriageplus.command.announce", "announce");
		permissionCompletions.put("marriageplus.command.compliment", "compliment");
		permissionCompletions.put("marriageplus.command.family", "family");
		permissionCompletions.put("marriageplus.command.mail", "mail");
		permissionCompletions.put("marriageplus.command.lovenotes", "note");
		permissionCompletions.put("marriageplus.command.ring", "ring");
		permissionCompletions.put("marriageplus.command.ceremony", "ceremony");
		permissionCompletions.put("marriageplus.command.level", "level");
		permissionCompletions.put("marriageplus.command.level", "xp");
		permissionCompletions.put("marriageplus.command.leaderboard", "leaderboard");
		permissionCompletions.put("marriageplus.command.achievements", "achievements");
		permissionCompletions.put("marriageplus.command.gift", "gift");
		permissionCompletions.put("marriageplus.command.backpack", "backpack");
		permissionCompletions.put("marriageplus.command.anniversary", "anniversary");
		permissionCompletions.put("marriageplus.command.pronouns", "pronouns");
		permissionCompletions.put("marriageplus.command.title", "title");
		permissionCompletions.put("marriageplus.command.nickname", "nickname");
		permissionCompletions.put("marriageplus.command.requests", "requests");
		permissionCompletions.put("marriageplus.command.block", "block");
		permissionCompletions.put("marriageplus.command.block", "unblock");
		permissionCompletions.put("marriageplus.command.block", "blocklist");

		permissionCompletions.forEach((permission, completion) -> addIfAllowed(sender, completions, permission, completion));

		if (canUse(sender, "marriageplus.command.home")) {
			completions.addAll(List.of("sethome", "home", "homes", "delhome"));
		}

		if (canUse(sender, "marriageplus.command.pvp")) {
			completions.addAll(List.of("pvpon", "pvpoff"));
		}

		for (String actionName : configuredActionNames()) {
			String permission = plugin.configs().actions().getString(actionName + ".permission", "marriageplus.command.actions");

			if (canUse(sender, permission)) {
				completions.add(actionName);
			}
		}

		if (sender.hasPermission("marriageplus.admin")) {
			completions.addAll(List.of("reload", "priest", "listenchat"));
		}

		if (sender.hasPermission("marriageplus.priest") || sender.hasPermission("marriageplus.admin")) {
			completions.addAll(onlinePlayerNames());
		}

		return filter(completions, input);
	}

	private List<String> completeSecondArg(CommandSender sender, String firstArg, String input) {
		firstArg = firstArg.toLowerCase(Locale.ROOT);

		return switch (firstArg) {
			case "help" -> filter(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), input);
			case "divorce" -> completeDivorceSecondArg(sender, input);
			case "me", "priest", "block", "unblock", "profile" -> filter(onlinePlayerNames(), input);
			case "ring" -> filter(List.of("replace"), input);
			case "mail" -> filter(List.of("send", "read", "clear"), input);
			case "ceremony" -> filter(List.of("start", "accept", "cancel"), input);
			case "leaderboard", "leaderboards", "top" -> filter(List.of("level", "xp", "longest", "achievements"), input);
			case "note", "lovenote", "lovenotes" -> filter(List.of("send"), input);
			case "anniversary", "quests" -> filter(List.of("claim"), input);
			case "status" -> filter(List.of("set", "clear"), input);
			case "achievements" -> filter(List.of("partner"), input);
			case "backpack" -> filter(List.of("on", "off"), input);
			case "chat" -> filter(List.of("toggle", "color", "prefix"), input);
			case "pronouns" -> filter(List.of("he/him", "she/her", "they/them", "any", "custom"), input);
			case "title" -> filter(List.of("on", "off"), input);
			case "nickname" -> filter(List.of("clear"), input);
			case "requests" -> filter(List.of("on", "off"), input);
			case "family" -> filter(List.of("help", "invite", "adopt", "accept", "deny", "decline", "leave", "kick", "rename", "chat", "tree", "web"), input);
			default -> Collections.emptyList();
		};
	}

	private List<String> completeDivorceSecondArg(CommandSender sender, String input) {
		List<String> completions = new ArrayList<>(List.of("yes", "no"));

		if (sender.hasPermission("marriageplus.priest") || sender.hasPermission("marriageplus.admin")) {
			completions.addAll(onlinePlayerNames());
		}

		return filter(completions, input);
	}

	private List<String> completeThirdArg(String firstArg, String secondArg, String input) {
		if (firstArg.equalsIgnoreCase("family")) {
			if (secondArg.equalsIgnoreCase("invite") || secondArg.equalsIgnoreCase("adopt") || secondArg.equalsIgnoreCase("kick")) {
				return filter(onlinePlayerNames(), input);
			}

			if (secondArg.equalsIgnoreCase("web")) {
				return filter(List.of("export"), input);
			}
		}

		if (firstArg.equalsIgnoreCase("chat")) {
			if (secondArg.equalsIgnoreCase("color")) {
				return filter(List.of("&f", "&d", "&c", "&a", "&b", "reset", "<#ff69b4>", "&#ff69b4"), input);
			}

			if (secondArg.equalsIgnoreCase("prefix")) {
				return filter(List.of("on", "off"), input);
			}
		}

		if (firstArg.equalsIgnoreCase("pronouns") && secondArg.equalsIgnoreCase("custom")) {
			return filter(List.of("he", "she", "they", "xe"), input);
		}

		if (isPlayerName(firstArg)) {
			return filter(onlinePlayerNames(), input);
		}

		return Collections.emptyList();
	}

	private List<String> completeFourthArg(String firstArg, String secondArg, String input) {
		if (firstArg.equalsIgnoreCase("pronouns") && secondArg.equalsIgnoreCase("custom")) {
			return filter(List.of("him", "her", "them", "xem"), input);
		}

		return Collections.emptyList();
	}

	private List<String> completeFifthArg(String firstArg, String secondArg, String input) {
		if (firstArg.equalsIgnoreCase("pronouns") && secondArg.equalsIgnoreCase("custom")) {
			return filter(List.of("his", "her", "their", "xyr"), input);
		}

		return Collections.emptyList();
	}

	private void handleStatus(Player player, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("set")) {
			profileManager.setStatus(player, args);
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
			profileManager.clearStatus(player);
			return;
		}

		showStatus(player);
	}

	private void handleDivorce(CommandSender sender, String[] args) {
		if (args.length >= 2 && !args[1].equalsIgnoreCase("yes") && !args[1].equalsIgnoreCase("no")) {
			priestDivorce(sender, args[1]);
			return;
		}

		playerCommand(sender, "marriageplus.command.divorce", player -> selfDivorce(player, args));
	}

	private void selfDivorce(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("no")) {
			divorceConfirmations.remove(player.getUniqueId());
			plugin.langManager().send(player, "divorce.cancelled");
			return;
		}

		if (args.length < 2 || !args[1].equalsIgnoreCase("yes")) {
			int seconds = plugin.getConfig().getInt("settings.divorce-confirm-seconds", 30);
			divorceConfirmations.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
			plugin.langManager().send(player, "divorce.confirm-question");
			sendDivorceButtons(player, seconds);
			return;
		}

		long expiresAt = divorceConfirmations.getOrDefault(player.getUniqueId(), 0L);

		if (System.currentTimeMillis() > expiresAt) {
			divorceConfirmations.remove(player.getUniqueId());
			plugin.langManager().send(player, "divorce.expired");
			return;
		}

		divorceConfirmations.remove(player.getUniqueId());
		marriageManager.divorceCouple(player.getUniqueId(), partnerId);

		plugin.langManager().send(player, "divorce.success-self");
		sendIfOnline(partnerId, "divorce.partner-divorced-you", Map.of("%player%", player.getName()));
	}

	private void priestDivorce(CommandSender sender, String playerName) {
		if (!sender.hasPermission("marriageplus.priest") && !sender.hasPermission("marriageplus.admin")) {
			plugin.langManager().send(sender, "priest.only-priests-divorce");
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
		UUID partnerId = marriageManager.getPartnerId(target.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(sender, "marriage.target-not-married");
			return;
		}

		marriageManager.divorceCouple(target.getUniqueId(), partnerId);

		plugin.langManager().send(sender, "priest.divorced-marriage", Map.of(
				"%player%", target.getName() == null ? playerName : target.getName()
		));

		sendIfOnline(target.getUniqueId(), "divorce.success-self");
		sendIfOnline(partnerId, "divorce.partner-divorced-you", Map.of("%player%", sender.getName()));
	}

	private void marryTwoPlayers(CommandSender sender, String firstName, String secondName) {
		if (!sender.hasPermission("marriageplus.priest") && !sender.hasPermission("marriageplus.admin")) {
			plugin.langManager().send(sender, "priest.only-priests-marry");
			return;
		}

		Player first = Bukkit.getPlayerExact(firstName);
		Player second = Bukkit.getPlayerExact(secondName);

		if (first == null || second == null) {
			plugin.langManager().send(sender, "priest.both-players-online");
			return;
		}

		if (first.getUniqueId().equals(second.getUniqueId())) {
			plugin.langManager().send(sender, "marriage.cannot-marry-yourself");
			return;
		}

		if (marriageManager.isMarried(first.getUniqueId()) || marriageManager.isMarried(second.getUniqueId())) {
			plugin.langManager().send(sender, "marriage.one-already-married");
			return;
		}

		FamilyMarriageCheckResult result = plugin.familyManager().checkMarriageAllowed(first, second);

		if (result == FamilyMarriageCheckResult.BLOCKED) {
			plugin.langManager().send(sender, "family.marriage-blocked-related");
			return;
		}

		if (result == FamilyMarriageCheckResult.WARN) {
			plugin.langManager().send(first, "family.marriage-warning-related");
			plugin.langManager().send(second, "family.marriage-warning-related");
		}

		marriageManager.marryPlayers(first, second);
	}

	private void showPartner(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		plugin.langManager().send(player, "partner.info", Map.of(
				"%partner%", socialManager.getPartnerDisplayName(player.getUniqueId(), partnerId),
				"%pronouns%", pronounManager.getPronouns(partnerId).display()
		));
	}

	private void showStatus(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		plugin.langManager().send(player, "status.header");

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			plugin.langManager().send(player, "status.pronouns", Map.of(
					"%pronouns%", pronounManager.getPronouns(player.getUniqueId()).display()
			));
			return;
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		long date = dataManager.dataConfig().getLong("marriage-dates." + player.getUniqueId(), 0L);
		long days = date <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - date) / 86_400_000L);
		String coupleKey = marriageManager.coupleKey(player.getUniqueId(), partnerId);

		plugin.langManager().send(player, "status.partner", Map.of("%partner%", safeName(partner)));
		plugin.langManager().send(player, "status.partner-nickname", Map.of("%nickname%", socialManager.getPartnerDisplayName(player.getUniqueId(), partnerId)));
		plugin.langManager().send(player, "status.marriage-title", Map.of("%title%", socialManager.getMarriageTitle(player.getUniqueId(), partnerId)));
		plugin.langManager().send(player, "status.your-pronouns", Map.of("%pronouns%", pronounManager.getPronouns(player.getUniqueId()).display()));
		plugin.langManager().send(player, "status.partner-pronouns", Map.of("%pronouns%", pronounManager.getPronouns(partnerId).display()));
		plugin.langManager().send(player, "status.married-for", Map.of("%days%", String.valueOf(days)));
		plugin.langManager().send(player, "status.marriage-level", Map.of("%level%", String.valueOf(marriageXpManager.getLevel(player.getUniqueId()))));
		plugin.langManager().send(player, "status.marriage-xp", Map.of(
				"%xp%", String.valueOf(marriageXpManager.getXp(player.getUniqueId())),
				"%required%", String.valueOf(marriageXpManager.getXpRequired(player.getUniqueId()))
		));
		plugin.langManager().send(player, "status.couple-homes", Map.of(
				"%homes%", String.valueOf(homeManager.getHomeCount(player.getUniqueId())),
				"%max%", String.valueOf(homeManager.getMaxHomes(player))
		));
		plugin.langManager().send(player, "status.partner-pvp", Map.of(
				"%status%", booleanStatus(marriageManager.pvpEnabledCouples().contains(coupleKey))
		));
		plugin.langManager().send(player, "status.your-backpack-access", Map.of(
				"%status%", accessStatus(backpackManager.backpackAllowed().contains(player.getUniqueId()))
		));
		plugin.langManager().send(player, "status.partner-backpack-access", Map.of(
				"%status%", accessStatus(backpackManager.backpackAllowed().contains(partnerId))
		));
		plugin.langManager().send(player, "status.marriage-chat-toggle", Map.of(
				"%status%", booleanStatus(requestManager.coupleChatToggled().contains(player.getUniqueId()))
		));
	}

	private void teleportToPartner(Player player) {
		if (cooldownManager.isOnCooldown(player, "tp")) {
			return;
		}

		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		cooldownManager.setCooldown(player, "tp", plugin.getConfig().getInt("settings.cooldowns.tp-seconds", 30));
		player.teleport(partner.getLocation());
		plugin.langManager().send(player, "teleport.success");
	}

	private void announce(Player player, String[] args) {
		if (cooldownManager.isOnCooldown(player, "announce")) {
			return;
		}

		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(player, "announce.usage");
			return;
		}

		String announcement = joinArgs(args, 1);

		if (announcement.isBlank()) {
			plugin.langManager().send(player, "announce.usage");
			return;
		}

		int maxLength = plugin.getConfig().getInt("announcements.max-length", 120);

		if (announcement.length() > maxLength) {
			plugin.langManager().send(player, "announce.too-long", Map.of("%max%", String.valueOf(maxLength)));
			return;
		}

		String format = plugin.getConfig().getString("announcements.format", "&c❤ &d%player% &7& &d%partner%&7: &f%message%");
		String message = applyCouplePlaceholders(format, player, partner).replace("%message%", announcement);

		cooldownManager.setCooldown(player, "announce", plugin.getConfig().getInt("settings.cooldowns.announce-seconds", 60));
		marriageXpManager.addXp(player, "actions");
		Bukkit.broadcastMessage(color(message));
	}

	private void compliment(Player player) {
		if (cooldownManager.isOnCooldown(player, "compliment")) {
			return;
		}

		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		List<String> compliments = plugin.getConfig().getStringList("compliments.messages");

		if (compliments.isEmpty()) {
			plugin.langManager().send(player, "compliment.none-configured");
			return;
		}

		String message = applyCouplePlaceholders(random(compliments), player, partner);

		cooldownManager.setCooldown(player, "compliment", plugin.getConfig().getInt("settings.cooldowns.compliment-seconds", 30));
		marriageXpManager.addXp(player, "actions");

		if (plugin.getConfig().getString("compliments.mode", "PUBLIC").equalsIgnoreCase("PRIVATE")) {
			player.sendMessage(color(message));
			partner.sendMessage(color(message));
			return;
		}

		Bukkit.broadcastMessage(color(message));
	}

	private void configuredInteraction(Player player, String actionName) {
		if (cooldownManager.isOnCooldown(player, "action")) {
			return;
		}

		ConfigurationSection section = plugin.configs().actions().getConfigurationSection(actionName);

		if (section == null) {
			plugin.langManager().send(player, "actions.unknown");
			return;
		}

		if (section.getBoolean("nsfw", false) && !plugin.getConfig().getBoolean("settings.nsfw-actions-enabled", false)) {
			plugin.langManager().send(player, "actions.nsfw-disabled");
			return;
		}

		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		List<String> messages = section.getStringList("broadcast-messages");

		if (messages.isEmpty()) {
			plugin.langManager().send(player, "actions.no-messages-configured");
			return;
		}

		String displayName = section.getString("display-name", actionName);
		String message = random(messages)
				.replace("%player%", player.getName())
				.replace("%partner%", partner.getName())
				.replace("%partner_nickname%", socialManager.getPartnerDisplayName(player.getUniqueId(), partner.getUniqueId()))
				.replace("%action%", displayName);

		playActionEffects(player, partner, actionName);

		cooldownManager.setCooldown(player, "action", plugin.getConfig().getInt("settings.cooldowns.action-seconds", 5));
		marriageXpManager.addXp(player, "actions");
		achievementManager.unlockByTrigger(player, "action", actionName);

		Bukkit.broadcastMessage(color(pronounManager.applyPronounPlaceholders(message, player, partner)));
	}

	private void playActionEffects(Player player, Player partner, String actionName) {
		Particle particle = particle(plugin.configs().actions().getString(actionName + ".particle", ""));
		Sound sound = sound(plugin.configs().actions().getString(actionName + ".sound", ""));

		if (particle != null) {
			player.getWorld().spawnParticle(particle, player.getLocation().add(0.0, 1.2, 0.0), 12, 0.4, 0.5, 0.4, 0.02);
			partner.getWorld().spawnParticle(particle, partner.getLocation().add(0.0, 1.2, 0.0), 12, 0.4, 0.5, 0.4, 0.02);
		}

		if (sound != null) {
			player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
			partner.playSound(partner.getLocation(), sound, 1.0F, 1.0F);
		}
	}

	private void giftItem(Player player) {
		if (cooldownManager.isOnCooldown(player, "gift")) {
			return;
		}

		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		ItemStack item = player.getInventory().getItemInMainHand();

		if (item.getType().isAir()) {
			plugin.langManager().send(player, "gift.must-hold-item");
			return;
		}

		cooldownManager.setCooldown(player, "gift", plugin.getConfig().getInt("settings.cooldowns.gift-seconds", 2));
		player.getInventory().setItemInMainHand(null);

		for (ItemStack leftover : partner.getInventory().addItem(item).values()) {
			player.getWorld().dropItemNaturally(partner.getLocation(), leftover);
		}

		plugin.langManager().send(player, "gift.sent", Map.of("%partner%", partner.getName()));
		plugin.langManager().send(partner, "gift.received", Map.of("%player%", player.getName()));

		marriageXpManager.addXp(player, "gifts");
		achievementManager.unlockByTrigger(player, "gift");
	}

	private void showAnniversary(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		long date = dataManager.dataConfig().getLong("marriage-dates." + player.getUniqueId(), 0L);

		if (date <= 0L) {
			plugin.langManager().send(player, "anniversary.no-date");
			return;
		}

		long days = Math.max(0L, (System.currentTimeMillis() - date) / 86_400_000L);

		plugin.langManager().send(player, "anniversary.married-for", Map.of("%days%", String.valueOf(days)));
		achievementManager.checkAnniversaryAchievements(player);
	}

	private void setPriest(CommandSender sender, String[] args) {
		if (!sender.hasPermission("marriageplus.admin")) {
			plugin.langManager().send(sender, "general.no-permission");
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(sender, "priest.usage");
			return;
		}

		plugin.langManager().send(sender, "priest.permission-info", Map.of(
				"%player%", args[1],
				"%permission%", "marriageplus.priest"
		));
		plugin.langManager().send(sender, "priest.luckperms-info", Map.of(
				"%player%", args[1],
				"%permission%", "marriageplus.priest"
		));
	}

	private void reload(CommandSender sender) {
		if (!sender.hasPermission("marriageplus.admin")) {
			plugin.langManager().send(sender, "general.no-permission");
			return;
		}

		plugin.configs().reload();
		plugin.langManager().reloadLang();

		if (plugin.questManager() != null) {
			plugin.questManager().loadQuestDefinitions();
		}

		plugin.langManager().send(sender, "general.reload");
	}

	private void sendHelp(CommandSender sender, String[] args) {
		try {
			sendHelp(sender, args.length >= 2 ? Integer.parseInt(args[1]) : 1);
		} catch (NumberFormatException exception) {
			plugin.langManager().send(sender, "help.usage");
		}
	}

	private void sendHelp(CommandSender sender, int page) {
		List<HelpEntry> entries = helpEntries(sender);
		int maxPage = Math.max(1, (int) Math.ceil(entries.size() / (double) HELP_ENTRIES_PER_PAGE));
		page = Math.max(1, Math.min(page, maxPage));

		plugin.langManager().send(sender, "help.header", Map.of(
				"%page%", String.valueOf(page),
				"%max_page%", String.valueOf(maxPage)
		));

		int start = (page - 1) * HELP_ENTRIES_PER_PAGE;
		int end = Math.min(start + HELP_ENTRIES_PER_PAGE, entries.size());

		for (int index = start; index < end; index++) {
			sendHelpLine(sender, entries.get(index));
		}

		sendHelpPages(sender, page, maxPage);
	}

	private List<HelpEntry> helpEntries(CommandSender sender) {
		List<HelpEntry> entries = new ArrayList<>(List.of(
				help("/marry", "main"),
				help("/marry help <page>", "help", "/marry help "),
				help("/marry me <player>", "me", "/marry me "),
				help("/marry accept", "accept"),
				help("/marry deny", "deny"),
				help("/marry divorce", "divorce"),
				help("/marry list", "list"),
				help("/marry partner", "partner"),
				help("/marry status", "status"),
				help("/marry status set <message>", "status-set", "/marry status set "),
				help("/marry status clear", "status-clear"),
				help("/marry quests", "quests"),
				help("/marry quests claim", "quests-claim"),
				help("/marry profile", "profile"),
				help("/marry profile <player>", "profile-other", "/marry profile "),
				help("/marry tp", "tp"),
				help("/marry sethome", "sethome"),
				help("/marry sethome <name>", "sethome-named", "/marry sethome "),
				help("/marry home", "home"),
				help("/marry home <name>", "home-named", "/marry home "),
				help("/marry homes", "homes"),
				help("/marry delhome <name>", "delhome", "/marry delhome "),
				help("/marry chat <message>", "chat", "/marry chat "),
				help("/marry chat toggle", "chat-toggle"),
				help("/marry chat color <color>", "chat-color", "/marry chat color "),
				help("/marry chat color reset", "chat-color-reset"),
				help("/marry chat prefix <on|off>", "chat-prefix", "/marry chat prefix "),
				help("/family", "family", "/family"),
				help("/family help", "family", "/family help"),
				help("/family invite <player>", "family-invite", "/family invite "),
				help("/family adopt <player>", "family-adopt", "/family adopt "),
				help("/family accept", "family-accept", "/family accept"),
				help("/family deny", "family-deny", "/family deny"),
				help("/family leave", "family-leave", "/family leave"),
				help("/family kick <player>", "family-kick", "/family kick "),
				help("/family rename <name>", "family-rename", "/family rename "),
				help("/family chat <message>", "family-chat", "/family chat "),
				help("/family tree", "family-web", "/family tree"),
				help("/family web", "family-web", "/family web"),
				help("/marry pvpon", "pvpon"),
				help("/marry pvpoff", "pvpoff"),
				help("/marry announce <message>", "announce", "/marry announce "),
				help("/marry compliment", "compliment"),
				help("/marry mail send <message>", "mail-send", "/marry mail send "),
				help("/marry mail read", "mail-read"),
				help("/marry mail clear", "mail-clear"),
				help("/marry note", "love-note-list"),
				help("/marry note send <message>", "love-note-send", "/marry note send "),
				help("/marry menu", "menu"),
				help("/marry ring", "ring"),
				help("/marry ring replace", "ring-replace"),
				help("/marry leaderboard", "leaderboard"),
				help("/marry leaderboard level", "leaderboard-level"),
				help("/marry leaderboard xp", "leaderboard-xp"),
				help("/marry leaderboard longest", "leaderboard-longest"),
				help("/marry level", "level"),
				help("/marry xp", "xp"),
				help("/marry achievements", "achievements"),
				help("/marry achievements partner", "achievements-partner"),
				help("/marry gift", "gift"),
				help("/marry backpack", "backpack"),
				help("/marry backpack on", "backpack-on"),
				help("/marry backpack off", "backpack-off"),
				help("/marry anniversary", "anniversary"),
				help("/marry anniversary claim", "anniversary-claim"),
				help("/marry pronouns", "pronouns"),
				help("/marry pronouns <he/him|she/her|they/them|any>", "pronouns-set", "/marry pronouns "),
				help("/marry pronouns custom <subject> <object> <possessive>", "pronouns-custom", "/marry pronouns custom "),
				help("/marry title on", "title-on"),
				help("/marry title off", "title-off"),
				help("/marry title <text>", "title", "/marry title "),
				help("/marry nickname <name>", "nickname", "/marry nickname "),
				help("/marry nickname clear", "nickname-clear"),
				help("/marry requests on", "requests-on"),
				help("/marry requests off", "requests-off"),
				help("/marry ceremony start", "ceremony-start"),
				help("/marry ceremony accept", "ceremony-accept"),
				help("/marry ceremony cancel", "ceremony-cancel"),
				help("/marry block <player>", "block", "/marry block "),
				help("/marry unblock <player>", "unblock", "/marry unblock "),
				help("/marry blocklist", "blocklist")
		));

		addConfiguredActionHelp(sender, entries);

		if (sender.hasPermission("marriageplus.priest") || sender.hasPermission("marriageplus.admin")) {
			entries.add(help("/marry <player1> <player2>", "priest-marry", "/marry "));
			entries.add(help("/marry divorce <player>", "priest-divorce", "/marry divorce "));
		}

		if (sender.hasPermission("marriageplus.admin")) {
			entries.add(help("/family web export", "family-web-export", "/family web export"));
			entries.add(help("/marry priest <player>", "priest", "/marry priest "));
			entries.add(help("/marry listenchat", "listenchat"));
			entries.add(help("/marry reload", "reload"));
		}

		return entries;
	}

	private void addConfiguredActionHelp(CommandSender sender, List<HelpEntry> entries) {
		for (String actionName : configuredActionNames()) {
			String permission = plugin.configs().actions().getString(actionName + ".permission", "marriageplus.command.actions");

			if (!canUse(sender, permission)) {
				continue;
			}

			String displayName = plugin.configs().actions().getString(actionName + ".display-name", actionName);
			String description = plugin.configs().actions().getString(
					actionName + ".description",
					plainLang("help.descriptions.configured-action", Map.of("%action%", displayName))
			);

			entries.add(new HelpEntry("/marry " + actionName, description, "/marry " + actionName));
		}
	}

	private void sendHelpLine(CommandSender sender, HelpEntry entry) {
		if (!(sender instanceof Player player)) {
			plugin.langManager().send(sender, "help.line", Map.of(
					"%command%", entry.command(),
					"%description%", entry.description()
			));
			return;
		}

		Component commandPart = legacy(plugin.langManager().get("help.clickable-command", Map.of("%command%", entry.command())))
				.clickEvent(ClickEvent.suggestCommand(entry.suggestedCommand()))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("help.hover", Map.of("%description%", entry.description())))));

		player.sendMessage(commandPart.append(legacy(plugin.langManager().get(
				"help.clickable-description",
				Map.of("%description%", entry.description())
		))));
	}

	private void sendHelpPages(CommandSender sender, int currentPage, int maxPage) {
		if (!(sender instanceof Player player)) {
			StringBuilder text = new StringBuilder(plugin.langManager().get("help.pages-prefix"));

			for (int page = 1; page <= maxPage; page++) {
				if (page > 1) {
					text.append(plugin.langManager().get("help.pages-separator"));
				}

				text.append(plugin.langManager().get(page == currentPage ? "help.page-current" : "help.page-other", Map.of(
						"%page%", String.valueOf(page)
				)));
			}

			sender.sendMessage(text.toString());
			return;
		}

		Component selector = legacy(plugin.langManager().get("help.pages-prefix"));

		for (int page = 1; page <= maxPage; page++) {
			if (page > 1) {
				selector = selector.append(legacy(plugin.langManager().get("help.pages-separator")));
			}

			selector = selector.append(legacy(plugin.langManager().get(page == currentPage ? "help.page-current" : "help.page-other", Map.of(
					"%page%", String.valueOf(page)
			)))
					.clickEvent(ClickEvent.runCommand("/marry help " + page))
					.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("help.page-hover", Map.of(
							"%page%", String.valueOf(page)
					))))));
		}

		player.sendMessage(selector);
	}

	private void sendDivorceButtons(Player player, int seconds) {
		Component buttons = legacy(plugin.langManager().get("divorce.buttons-prefix"))
				.append(legacy(plugin.langManager().get("divorce.yes-button"))
						.clickEvent(ClickEvent.runCommand("/marry divorce yes"))
						.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("divorce.yes-hover")))))
				.append(legacy(plugin.langManager().get("divorce.buttons-separator")))
				.append(legacy(plugin.langManager().get("divorce.no-button"))
						.clickEvent(ClickEvent.runCommand("/marry divorce no"))
						.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("divorce.no-hover")))))
				.append(legacy(plugin.langManager().get("divorce.buttons-expire", Map.of("%seconds%", String.valueOf(seconds)))));

		player.sendMessage(buttons);
	}

	private void command(CommandSender sender, String permission, Runnable action) {
		if (!hasPermission(sender, permission)) {
			return;
		}

		action.run();
	}

	private void playerCommand(CommandSender sender, String permission, PlayerAction action) {
		if (!hasPermission(sender, permission)) {
			return;
		}

		requirePlayer(sender, action);
	}

	private void requirePlayer(CommandSender sender, PlayerAction action) {
		if (!(sender instanceof Player player)) {
			plugin.langManager().send(sender, "general.players-only");
			return;
		}

		action.run(player);
	}

	private boolean hasPermission(CommandSender sender, String permission) {
		if (canUse(sender, permission)) {
			return true;
		}

		plugin.langManager().send(sender, "general.no-permission");
		return false;
	}

	private boolean canUse(CommandSender sender, String permission) {
		return sender.hasPermission(permission) || sender.hasPermission("marriageplus.admin");
	}

	private void addIfAllowed(CommandSender sender, List<String> completions, String permission, String completion) {
		if (canUse(sender, permission)) {
			completions.add(completion);
		}
	}

	private void sendIfOnline(UUID playerId, String langPath) {
		Player player = Bukkit.getPlayer(playerId);

		if (player != null) {
			plugin.langManager().send(player, langPath);
		}
	}

	private void sendIfOnline(UUID playerId, String langPath, Map<String, String> placeholders) {
		Player player = Bukkit.getPlayer(playerId);

		if (player != null) {
			plugin.langManager().send(player, langPath, placeholders);
		}
	}

	private boolean isConfiguredAction(String actionName) {
		return plugin.configs().actions().isConfigurationSection(actionName);
	}

	private List<String> configuredActionNames() {
		ConfigurationSection section = plugin.configs().actions();

		if (section == null) {
			return Collections.emptyList();
		}

		boolean nsfwEnabled = plugin.getConfig().getBoolean("settings.nsfw-actions-enabled", false);

		return section.getKeys(false).stream()
				.filter(actionName -> nsfwEnabled || !plugin.configs().actions().getBoolean(actionName + ".nsfw", false))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private boolean isPlayerName(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		return !isConfiguredAction(normalized) && !RESERVED_COMMANDS.contains(normalized);
	}

	private List<String> onlinePlayerNames() {
		return Bukkit.getOnlinePlayers().stream()
				.map(Player::getName)
				.toList();
	}

	private List<String> filter(List<String> options, String input) {
		String normalized = input.toLowerCase(Locale.ROOT);

		return options.stream()
				.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
				.distinct()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private HelpEntry help(String command, String key) {
		return help(command, key, command);
	}

	private HelpEntry help(String command, String key, String suggestedCommand) {
		return new HelpEntry(command, lang("help.descriptions." + key), suggestedCommand);
	}

	private String applyCouplePlaceholders(String message, Player player, Player partner) {
		String replaced = message
				.replace("%player%", player.getName())
				.replace("%partner%", partner.getName())
				.replace("%partner_nickname%", socialManager.getPartnerDisplayName(player.getUniqueId(), partner.getUniqueId()));

		return pronounManager.applyPronounPlaceholders(replaced, player, partner);
	}

	private String joinArgs(String[] args, int startIndex) {
		if (startIndex >= args.length) {
			return "";
		}

		return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length)).trim();
	}

	private String random(List<String> values) {
		return values.get(ThreadLocalRandom.current().nextInt(values.size()));
	}

	private Particle particle(String particleName) {
		if (particleName == null || particleName.isBlank()) {
			return null;
		}

		try {
			return Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			plugin.getLogger().warning(plainLang("logs.invalid-particle", Map.of("%particle%", particleName)));
			return null;
		}
	}

	private Sound sound(String soundName) {
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
			plugin.getLogger().warning(plainLang("logs.invalid-sound", Map.of("%sound%", soundName)));
		}

		return sound;
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plainLang("general.unknown") : player.getName();
	}

	private String booleanStatus(boolean enabled) {
		return plugin.langManager().get(enabled ? "status.values.on" : "status.values.off");
	}

	private String accessStatus(boolean allowed) {
		return plugin.langManager().get(allowed ? "status.values.allowed" : "status.values.blocked");
	}

	private String lang(String path) {
		return plugin.langManager().get(path);
	}

	private String plainLang(String path) {
		return color(plugin.langManager().getRaw(path));
	}

	private String plainLang(String path, Map<String, String> placeholders) {
		String message = plugin.langManager().getRaw(path);

		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			message = message.replace(entry.getKey(), entry.getValue());
		}

		return color(message);
	}

	@FunctionalInterface
	private interface PlayerAction {
		void run(Player player);
	}
}