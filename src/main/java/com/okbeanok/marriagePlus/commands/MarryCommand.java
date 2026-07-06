package com.okbeanok.marriagePlus.commands;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.managers.AchievementManager;
import com.okbeanok.marriagePlus.managers.BackpackManager;
import com.okbeanok.marriagePlus.managers.CooldownManager;
import com.okbeanok.marriagePlus.managers.DataManager;
import com.okbeanok.marriagePlus.managers.HomeManager;
import com.okbeanok.marriagePlus.managers.MailManager;
import com.okbeanok.marriagePlus.managers.MarriageManager;
import com.okbeanok.marriagePlus.managers.MarriageXpManager;
import com.okbeanok.marriagePlus.managers.PronounManager;
import com.okbeanok.marriagePlus.managers.RequestManager;
import com.okbeanok.marriagePlus.managers.SocialManager;
import com.okbeanok.marriagePlus.models.HelpEntry;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;
import static com.okbeanok.marriagePlus.utils.TextUtils.legacy;

public class MarryCommand implements TabExecutor {

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

	private final HashMap<UUID, Long> divorceConfirmations = new HashMap<>();

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
			case "me" -> runWithPermission(sender, "marriageplus.command.me", player -> requestManager.sendMarriageRequest(player, args));
			case "accept" -> requirePlayer(sender, requestManager::acceptProposal);
			case "deny", "decline" -> requirePlayer(sender, requestManager::denyProposal);
			case "divorce" -> handleDivorce(sender, args);
			case "list" -> {
				if (hasPermission(sender, "marriageplus.command.list")) {
					marriageManager.listMarriages(sender);
				}
			}
			case "partner" -> runWithPermission(sender, "marriageplus.command.partner", this::showPartner);
			case "status" -> runWithPermission(sender, "marriageplus.command.status", this::showStatus);
			case "tp" -> runWithPermission(sender, "marriageplus.command.tp", this::teleportToPartner);
			case "sethome" -> runWithPermission(sender, "marriageplus.command.home", player -> homeManager.setHome(player, args));
			case "home" -> runWithPermission(sender, "marriageplus.command.home", player -> homeManager.goHome(player, args));
			case "chat" -> runWithPermission(sender, "marriageplus.command.chat", player -> requestManager.marriageChat(player, args));
			case "listenchat" -> requirePlayer(sender, requestManager::toggleListenChat);
			case "pvpon" -> runWithPermission(sender, "marriageplus.command.pvp", player -> marriageManager.setPartnerPvp(player, true));
			case "pvpoff" -> runWithPermission(sender, "marriageplus.command.pvp", player -> marriageManager.setPartnerPvp(player, false));
			case "announce" -> runWithPermission(sender, "marriageplus.command.announce", player -> announce(player, args));
			case "compliment" -> runWithPermission(sender, "marriageplus.command.compliment", this::compliment);
			case "mail" -> runWithPermission(sender, "marriageplus.command.mail", player -> mailManager.mailCommand(player, args));
			case "level" -> runWithPermission(sender, "marriageplus.command.level", marriageXpManager::levelCommand);
			case "xp" -> runWithPermission(sender, "marriageplus.command.level", marriageXpManager::xpCommand);
			case "achievements" -> runWithPermission(sender, "marriageplus.command.achievements", player -> achievementManager.achievementsCommand(player, args));
			case "gift" -> runWithPermission(sender, "marriageplus.command.gift", this::giftItem);
			case "backpack" -> runWithPermission(sender, "marriageplus.command.backpack", player -> backpackManager.backpackCommand(player, args));
			case "anniversary" -> runWithPermission(sender, "marriageplus.command.anniversary", this::showAnniversary);
			case "pronouns" -> runWithPermission(sender, "marriageplus.command.pronouns", player -> pronounManager.pronounsCommand(player, args));
			case "title" -> runWithPermission(sender, "marriageplus.command.title", player -> socialManager.titleCommand(player, args));
			case "nickname" -> runWithPermission(sender, "marriageplus.command.nickname", player -> socialManager.nicknameCommand(player, args));
			case "requests" -> runWithPermission(sender, "marriageplus.command.requests", player -> requestManager.requestsCommand(player, args));
			case "block" -> runWithPermission(sender, "marriageplus.command.block", player -> requestManager.blockCommand(player, args));
			case "unblock" -> runWithPermission(sender, "marriageplus.command.block", player -> requestManager.unblockCommand(player, args));
			case "blocklist" -> runWithPermission(sender, "marriageplus.command.block", requestManager::showBlocklist);
			case "priest" -> setPriest(sender, args);
			case "reload" -> reloadMarriagePlugin(sender);
			default -> {
				if (isConfiguredAction(subCommand)) {
					handleConfiguredActionCommand(sender, subCommand);
					return true;
				}

				sendHelp(sender, 1);
			}
		}

		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!command.getName().equalsIgnoreCase("marry")) {
			return Collections.emptyList();
		}

		if (args.length == 1) {
			List<String> completions = new ArrayList<>(List.of("help", "accept", "deny"));

			addIfAllowed(sender, completions, "marriageplus.command.me", "me");
			addIfAllowed(sender, completions, "marriageplus.command.divorce", "divorce");
			addIfAllowed(sender, completions, "marriageplus.command.list", "list");
			addIfAllowed(sender, completions, "marriageplus.command.partner", "partner");
			addIfAllowed(sender, completions, "marriageplus.command.status", "status");
			addIfAllowed(sender, completions, "marriageplus.command.tp", "tp");

			if (canUse(sender, "marriageplus.command.home")) {
				completions.add("sethome");
				completions.add("home");
				completions.add("homes");
				completions.add("delhome");
			}

			addIfAllowed(sender, completions, "marriageplus.command.chat", "chat");

			if (canUse(sender, "marriageplus.command.pvp")) {
				completions.add("pvpon");
				completions.add("pvpoff");
			}

			for (String actionName : configuredActionNames()) {
				String actionPermission = plugin.getConfig().getString("actions." + actionName + ".permission", "marriageplus.command.actions");

				if (canUse(sender, actionPermission)) {
					completions.add(actionName);
				}
			}

			addIfAllowed(sender, completions, "marriageplus.command.announce", "announce");
			addIfAllowed(sender, completions, "marriageplus.command.compliment", "compliment");
			addIfAllowed(sender, completions, "marriageplus.command.mail", "mail");
			addIfAllowed(sender, completions, "marriageplus.command.level", "level");
			addIfAllowed(sender, completions, "marriageplus.command.level", "xp");
			addIfAllowed(sender, completions, "marriageplus.command.achievements", "achievements");
			addIfAllowed(sender, completions, "marriageplus.command.gift", "gift");
			addIfAllowed(sender, completions, "marriageplus.command.backpack", "backpack");
			addIfAllowed(sender, completions, "marriageplus.command.anniversary", "anniversary");
			addIfAllowed(sender, completions, "marriageplus.command.pronouns", "pronouns");
			addIfAllowed(sender, completions, "marriageplus.command.title", "title");
			addIfAllowed(sender, completions, "marriageplus.command.nickname", "nickname");
			addIfAllowed(sender, completions, "marriageplus.command.requests", "requests");
			addIfAllowed(sender, completions, "marriageplus.command.block", "block");
			addIfAllowed(sender, completions, "marriageplus.command.block", "unblock");
			addIfAllowed(sender, completions, "marriageplus.command.block", "blocklist");

			if (sender.hasPermission("marriageplus.admin")) {
				completions.addAll(List.of("reload", "priest", "listenchat"));
			}

			if (sender.hasPermission("marriageplus.priest") || sender.hasPermission("marriageplus.admin")) {
				completions.addAll(onlinePlayerNames());
			}

			return filterCompletions(completions, args[0]);
		}

		if (args.length == 2) {
			String firstArg = args[0].toLowerCase(Locale.ROOT);

			if (firstArg.equals("help")) {
				return filterCompletions(List.of("1", "2", "3", "4", "5", "6", "7", "8"), args[1]);
			}

			if (firstArg.equals("divorce")) {
				List<String> completions = new ArrayList<>(List.of("yes", "no"));

				if (sender.hasPermission("marriageplus.priest") || sender.hasPermission("marriageplus.admin")) {
					completions.addAll(onlinePlayerNames());
				}

				return filterCompletions(completions, args[1]);
			}

			if (Set.of("me", "priest", "block", "unblock").contains(firstArg)) {
				return filterCompletions(onlinePlayerNames(), args[1]);
			}

			if (firstArg.equals("mail")) {
				return filterCompletions(List.of("send", "read", "clear"), args[1]);
			}

			if (firstArg.equals("achievements")) {
				return filterCompletions(List.of("partner"), args[1]);
			}

			if (firstArg.equals("backpack")) {
				return filterCompletions(List.of("on", "off"), args[1]);
			}

			if (firstArg.equals("chat")) {
				return filterCompletions(List.of("toggle", "color", "prefix"), args[1]);
			}

			if (firstArg.equals("pronouns")) {
				return filterCompletions(List.of("he/him", "she/her", "they/them", "any", "custom"), args[1]);
			}

			if (firstArg.equals("title")) {
				return filterCompletions(List.of("on", "off"), args[1]);
			}

			if (firstArg.equals("nickname")) {
				return filterCompletions(List.of("clear"), args[1]);
			}

			if (firstArg.equals("requests")) {
				return filterCompletions(List.of("on", "off"), args[1]);
			}
		}

		if (args.length == 3 && args[0].equalsIgnoreCase("chat")) {
			if (args[1].equalsIgnoreCase("color")) {
				return filterCompletions(List.of("&f", "&d", "&c", "&a", "&b", "reset", "<#ff69b4>", "&#ff69b4"), args[2]);
			}

			if (args[1].equalsIgnoreCase("prefix")) {
				return filterCompletions(List.of("on", "off"), args[2]);
			}
		}

		if (args.length == 3 && isPlayerName(args[0])) {
			return filterCompletions(onlinePlayerNames(), args[2]);
		}

		if (args.length >= 3 && args[0].equalsIgnoreCase("pronouns") && args[1].equalsIgnoreCase("custom")) {
			if (args.length == 3) {
				return filterCompletions(List.of("he", "she", "they", "xe"), args[2]);
			}

			if (args.length == 4) {
				return filterCompletions(List.of("him", "her", "them", "xem"), args[3]);
			}

			if (args.length == 5) {
				return filterCompletions(List.of("his", "her", "their", "xyr"), args[4]);
			}
		}

		return Collections.emptyList();
	}

	private void handleConfiguredActionCommand(CommandSender sender, String actionName) {
		String permission = plugin.getConfig().getString("actions." + actionName + ".permission", "marriageplus.command.actions");
		runWithPermission(sender, permission, player -> configuredInteraction(player, actionName));
	}

	private boolean isConfiguredAction(String actionName) {
		return plugin.getConfig().isConfigurationSection("actions." + actionName);
	}

	private List<String> configuredActionNames() {
		ConfigurationSection actionsSection = plugin.getConfig().getConfigurationSection("actions");

		if (actionsSection == null) {
			return Collections.emptyList();
		}

		boolean nsfwEnabled = plugin.getConfig().getBoolean("settings.nsfw-actions-enabled", false);

		return actionsSection.getKeys(false).stream()
				.filter(actionName -> nsfwEnabled || !plugin.getConfig().getBoolean("actions." + actionName + ".nsfw", false))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private void handleDivorce(CommandSender sender, String[] args) {
		if (args.length >= 2
				&& !args[1].equalsIgnoreCase("yes")
				&& !args[1].equalsIgnoreCase("no")) {
			priestDivorce(sender, args[1]);
			return;
		}

		runWithPermission(sender, "marriageplus.command.divorce", player -> selfDivorce(player, args));
	}

	private void sendHelp(CommandSender sender, String[] args) {
		int page = 1;

		if (args.length >= 2) {
			try {
				page = Integer.parseInt(args[1]);
			} catch (NumberFormatException ignored) {
				plugin.langManager().send(sender, "help.usage");
				return;
			}
		}

		sendHelp(sender, page);
	}

	private void sendHelp(CommandSender sender, int page) {
		List<HelpEntry> entries = new ArrayList<>(List.of(
				help("/marry", lang("help.descriptions.main"), "/marry"),
				help("/marry help <page>", lang("help.descriptions.help"), "/marry help "),
				help("/marry me <player>", lang("help.descriptions.me"), "/marry me "),
				help("/marry accept", lang("help.descriptions.accept"), "/marry accept"),
				help("/marry deny", lang("help.descriptions.deny"), "/marry deny"),
				help("/marry divorce", lang("help.descriptions.divorce"), "/marry divorce"),
				help("/marry list", lang("help.descriptions.list"), "/marry list"),
				help("/marry partner", lang("help.descriptions.partner"), "/marry partner"),
				help("/marry status", lang("help.descriptions.status"), "/marry status"),
				help("/marry tp", lang("help.descriptions.tp"), "/marry tp"),
				help("/marry sethome", lang("help.descriptions.sethome"), "/marry sethome"),
				help("/marry sethome <name>", lang("help.descriptions.sethome-named"), "/marry sethome "),
				help("/marry home", lang("help.descriptions.home"), "/marry home"),
				help("/marry home <name>", lang("help.descriptions.home-named"), "/marry home "),
				help("/marry homes", lang("help.descriptions.homes"), "/marry homes"),
				help("/marry delhome <name>", lang("help.descriptions.delhome"), "/marry delhome "),
				help("/marry chat <message>", lang("help.descriptions.chat"), "/marry chat "),
				help("/marry chat toggle", lang("help.descriptions.chat-toggle"), "/marry chat toggle"),
				help("/marry chat color <color>", lang("help.descriptions.chat-color"), "/marry chat color "),
				help("/marry chat color reset", lang("help.descriptions.chat-color-reset"), "/marry chat color reset"),
				help("/marry chat prefix <on|off>", lang("help.descriptions.chat-prefix"), "/marry chat prefix "),
				help("/marry pvpon", lang("help.descriptions.pvpon"), "/marry pvpon"),
				help("/marry pvpoff", lang("help.descriptions.pvpoff"), "/marry pvpoff"),
				help("/marry announce <message>", lang("help.descriptions.announce"), "/marry announce "),
				help("/marry compliment", lang("help.descriptions.compliment"), "/marry compliment"),
				help("/marry mail send <message>", lang("help.descriptions.mail-send"), "/marry mail send "),
				help("/marry mail read", lang("help.descriptions.mail-read"), "/marry mail read"),
				help("/marry mail clear", lang("help.descriptions.mail-clear"), "/marry mail clear"),
				help("/marry level", lang("help.descriptions.level"), "/marry level"),
				help("/marry xp", lang("help.descriptions.xp"), "/marry xp"),
				help("/marry achievements", lang("help.descriptions.achievements"), "/marry achievements"),
				help("/marry achievements partner", lang("help.descriptions.achievements-partner"), "/marry achievements partner"),
				help("/marry gift", lang("help.descriptions.gift"), "/marry gift"),
				help("/marry backpack", lang("help.descriptions.backpack"), "/marry backpack"),
				help("/marry backpack on", lang("help.descriptions.backpack-on"), "/marry backpack on"),
				help("/marry backpack off", lang("help.descriptions.backpack-off"), "/marry backpack off"),
				help("/marry anniversary", lang("help.descriptions.anniversary"), "/marry anniversary"),
				help("/marry pronouns", lang("help.descriptions.pronouns"), "/marry pronouns"),
				help("/marry pronouns <he/him|she/her|they/them|any>", lang("help.descriptions.pronouns-set"), "/marry pronouns "),
				help("/marry pronouns custom <subject> <object> <possessive>", lang("help.descriptions.pronouns-custom"), "/marry pronouns custom "),
				help("/marry title on", lang("help.descriptions.title-on"), "/marry title on"),
				help("/marry title off", lang("help.descriptions.title-off"), "/marry title off"),
				help("/marry title <text>", lang("help.descriptions.title"), "/marry title "),
				help("/marry nickname <name>", lang("help.descriptions.nickname"), "/marry nickname "),
				help("/marry nickname clear", lang("help.descriptions.nickname-clear"), "/marry nickname clear"),
				help("/marry requests on", lang("help.descriptions.requests-on"), "/marry requests on"),
				help("/marry requests off", lang("help.descriptions.requests-off"), "/marry requests off"),
				help("/marry block <player>", lang("help.descriptions.block"), "/marry block "),
				help("/marry unblock <player>", lang("help.descriptions.unblock"), "/marry unblock "),
				help("/marry blocklist", lang("help.descriptions.blocklist"), "/marry blocklist")
		));

		addConfiguredActionHelpEntries(sender, entries);

		if (sender.hasPermission("marriageplus.priest") || sender.hasPermission("marriageplus.admin")) {
			entries.add(help("/marry <player1> <player2>", lang("help.descriptions.priest-marry"), "/marry "));
			entries.add(help("/marry divorce <player>", lang("help.descriptions.priest-divorce"), "/marry divorce "));
		}

		if (sender.hasPermission("marriageplus.admin")) {
			entries.add(help("/marry priest <player>", lang("help.descriptions.priest"), "/marry priest "));
			entries.add(help("/marry listenchat", lang("help.descriptions.listenchat"), "/marry listenchat"));
			entries.add(help("/marry reload", lang("help.descriptions.reload"), "/marry reload"));
		}

		int entriesPerPage = 8;
		int maxPage = Math.max(1, (int) Math.ceil(entries.size() / (double) entriesPerPage));
		page = Math.max(1, Math.min(page, maxPage));

		plugin.langManager().send(sender, "help.header", Map.of(
				"%page%", String.valueOf(page),
				"%max_page%", String.valueOf(maxPage)
		));

		int start = (page - 1) * entriesPerPage;
		int end = Math.min(start + entriesPerPage, entries.size());

		for (int index = start; index < end; index++) {
			sendClickableHelpEntry(sender, entries.get(index));
		}

		sendHelpPageSelector(sender, page, maxPage);
	}

	private void addConfiguredActionHelpEntries(CommandSender sender, List<HelpEntry> entries) {
		for (String actionName : configuredActionNames()) {
			String actionPermission = plugin.getConfig().getString("actions." + actionName + ".permission", "marriageplus.command.actions");

			if (!canUse(sender, actionPermission)) {
				continue;
			}

			String displayName = plugin.getConfig().getString("actions." + actionName + ".display-name", actionName);
			String description = plugin.getConfig().getString(
					"actions." + actionName + ".description",
					plainLang("help.descriptions.configured-action", Map.of("%action%", displayName))
			);

			entries.add(help("/marry " + actionName, description, "/marry " + actionName));
		}
	}

	private void sendClickableHelpEntry(CommandSender sender, HelpEntry entry) {
		if (!(sender instanceof Player player)) {
			plugin.langManager().send(sender, "help.line", Map.of(
					"%command%", entry.command(),
					"%description%", entry.description()
			));
			return;
		}

		Component commandComponent = legacy(plugin.langManager().get("help.clickable-command", Map.of(
				"%command%", entry.command()
		)))
				.clickEvent(ClickEvent.suggestCommand(entry.suggestedCommand()))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("help.hover", Map.of(
						"%description%", entry.description()
				)))));

		player.sendMessage(commandComponent.append(legacy(plugin.langManager().get("help.clickable-description", Map.of(
				"%description%", entry.description()
		)))));
	}

	private void sendHelpPageSelector(CommandSender sender, int currentPage, int maxPage) {
		if (!(sender instanceof Player player)) {
			StringBuilder consoleSelector = new StringBuilder(plugin.langManager().get("help.pages-prefix"));

			for (int page = 1; page <= maxPage; page++) {
				if (page > 1) {
					consoleSelector.append(plugin.langManager().get("help.pages-separator"));
				}

				consoleSelector.append(plugin.langManager().get(page == currentPage ? "help.page-current" : "help.page-other", Map.of(
						"%page%", String.valueOf(page)
				)));
			}

			sender.sendMessage(consoleSelector.toString());
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

		marriageManager.marryPlayers(first, second);
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
			int confirmSeconds = plugin.getConfig().getInt("settings.divorce-confirm-seconds", 30);
			divorceConfirmations.put(player.getUniqueId(), System.currentTimeMillis() + confirmSeconds * 1000L);

			plugin.langManager().send(player, "divorce.confirm-question");
			sendDivorceConfirmationButtons(player, confirmSeconds);
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

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner != null) {
			plugin.langManager().send(partner, "divorce.partner-divorced-you", Map.of(
					"%player%", player.getName()
			));
		}
	}

	private void sendDivorceConfirmationButtons(Player player, int confirmSeconds) {
		Component buttons = legacy(plugin.langManager().get("divorce.buttons-prefix"))
				.append(legacy(plugin.langManager().get("divorce.yes-button"))
						.clickEvent(ClickEvent.runCommand("/marry divorce yes"))
						.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("divorce.yes-hover")))))
				.append(legacy(plugin.langManager().get("divorce.buttons-separator")))
				.append(legacy(plugin.langManager().get("divorce.no-button"))
						.clickEvent(ClickEvent.runCommand("/marry divorce no"))
						.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("divorce.no-hover")))))
				.append(legacy(plugin.langManager().get("divorce.buttons-expire", Map.of(
						"%seconds%", String.valueOf(confirmSeconds)
				))));

		player.sendMessage(buttons);
	}

	private void priestDivorce(CommandSender sender, String playerName) {
		if (!sender.hasPermission("marriageplus.priest") && !sender.hasPermission("marriageplus.admin")) {
			plugin.langManager().send(sender, "priest.only-priests-divorce");
			return;
		}
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
				"%status%", booleanStatus(marriageManager.pvpEnabledCouples().contains(marriageManager.coupleKey(player.getUniqueId(), partnerId)))
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

	private void configuredInteraction(Player player, String actionName) {
		if (cooldownManager.isOnCooldown(player, "action")) {
			return;
		}

		String actionPath = "actions." + actionName;

		if (!plugin.getConfig().isConfigurationSection(actionPath)) {
			plugin.langManager().send(player, "actions.unknown");
			return;
		}

		boolean actionIsNsfw = plugin.getConfig().getBoolean(actionPath + ".nsfw", false);
		boolean nsfwEnabled = plugin.getConfig().getBoolean("settings.nsfw-actions-enabled", false);

		if (actionIsNsfw && !nsfwEnabled) {
			plugin.langManager().send(player, "actions.nsfw-disabled");
			return;
		}

		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		List<String> messages = plugin.getConfig().getStringList(actionPath + ".broadcast-messages");

		if (messages.isEmpty()) {
			plugin.langManager().send(player, "actions.no-messages-configured");
			return;
		}

		String displayName = plugin.getConfig().getString(actionPath + ".display-name", actionName);
		String message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()))
				.replace("%player%", player.getName())
				.replace("%partner%", partner.getName())
				.replace("%partner_nickname%", socialManager.getPartnerDisplayName(player.getUniqueId(), partner.getUniqueId()))
				.replace("%action%", displayName);

		message = pronounManager.applyPronounPlaceholders(message, player, partner);

		playActionEffects(player, partner, actionPath);
		cooldownManager.setCooldown(player, "action", plugin.getConfig().getInt("settings.cooldowns.action-seconds", 5));
		marriageXpManager.addXp(player, "actions");
		achievementManager.unlockByTrigger(player, "action", actionName);

		Bukkit.broadcastMessage(color(message));
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

		String announcement = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();

		if (announcement.isBlank()) {
			plugin.langManager().send(player, "announce.usage");
			return;
		}

		int maxLength = plugin.getConfig().getInt("announcements.max-length", 120);

		if (announcement.length() > maxLength) {
			plugin.langManager().send(player, "announce.too-long", Map.of(
					"%max%", String.valueOf(maxLength)
			));
			return;
		}

		String format = plugin.getConfig().getString("announcements.format", "&c❤ &d%player% &7& &d%partner%&7: &f%message%");
		String message = applyCouplePlaceholders(format, player, partner)
				.replace("%message%", announcement);

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

		String message = compliments.get(ThreadLocalRandom.current().nextInt(compliments.size()));
		message = applyCouplePlaceholders(message, player, partner);

		cooldownManager.setCooldown(player, "compliment", plugin.getConfig().getInt("settings.cooldowns.compliment-seconds", 30));
		marriageXpManager.addXp(player, "actions");

		String mode = plugin.getConfig().getString("compliments.mode", "PUBLIC");

		if (mode != null && mode.equalsIgnoreCase("PRIVATE")) {
			player.sendMessage(color(message));
			partner.sendMessage(color(message));
			return;
		}

		Bukkit.broadcastMessage(color(message));
	}

	private String applyCouplePlaceholders(String message, Player player, Player partner) {
		String replaced = message
				.replace("%player%", player.getName())
				.replace("%partner%", partner.getName())
				.replace("%partner_nickname%", socialManager.getPartnerDisplayName(player.getUniqueId(), partner.getUniqueId()));

		return pronounManager.applyPronounPlaceholders(replaced, player, partner);
	}

	private void playActionEffects(Player player, Player partner, String actionPath) {
		String particleName = plugin.getConfig().getString(actionPath + ".particle", "");
		String soundName = plugin.getConfig().getString(actionPath + ".sound", "");

		Particle particle = getParticleFromConfig(particleName);

		if (particle != null) {
			player.getWorld().spawnParticle(
					particle,
					player.getLocation().add(0.0, 1.2, 0.0),
					12,
					0.4,
					0.5,
					0.4,
					0.02
			);

			partner.getWorld().spawnParticle(
					particle,
					partner.getLocation().add(0.0, 1.2, 0.0),
					12,
					0.4,
					0.5,
					0.4,
					0.02
			);
		} else if (!particleName.isBlank()) {
			plugin.getLogger().warning(plainLang("logs.invalid-particle", Map.of("%particle%", particleName)));
		}

		Sound sound = getSoundFromConfig(soundName);

		if (sound != null) {
			player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
			partner.playSound(partner.getLocation(), sound, 1.0F, 1.0F);
		} else if (!soundName.isBlank()) {
			plugin.getLogger().warning(plainLang("logs.invalid-sound", Map.of("%sound%", soundName)));
		}
	}

	private Particle getParticleFromConfig(String particleName) {
		if (particleName == null || particleName.isBlank()) {
			return null;
		}

		try {
			return Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private Sound getSoundFromConfig(String soundName) {
		if (soundName == null || soundName.isBlank()) {
			return null;
		}

		String normalizedName = soundName.toLowerCase(Locale.ROOT);

		Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(normalizedName));

		if (sound != null) {
			return sound;
		}

		String convertedEnumName = normalizedName.replace('_', '.');
		return Registry.SOUNDS.get(NamespacedKey.minecraft(convertedEnumName));
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

		HashMap<Integer, ItemStack> leftovers = partner.getInventory().addItem(item);

		for (ItemStack leftover : leftovers.values()) {
			player.getWorld().dropItemNaturally(partner.getLocation(), leftover);
		}

		plugin.langManager().send(player, "gift.sent", Map.of(
				"%partner%", partner.getName()
		));
		plugin.langManager().send(partner, "gift.received", Map.of(
				"%player%", player.getName()
		));

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
		plugin.langManager().send(player, "anniversary.married-for", Map.of(
				"%days%", String.valueOf(days)
		));
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

	private void reloadMarriagePlugin(CommandSender sender) {
		if (!sender.hasPermission("marriageplus.admin")) {
			plugin.langManager().send(sender, "general.no-permission");
			return;
		}

		plugin.reloadConfig();
		plugin.langManager().reloadLang();

		plugin.langManager().send(sender, "general.reload");
	}

	private void runWithPermission(CommandSender sender, String permission, PlayerAction action) {
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

	private boolean canUse(CommandSender sender, String permission) {
		return sender.hasPermission(permission) || sender.hasPermission("marriageplus.admin");
	}

	private boolean hasPermission(CommandSender sender, String permission) {
		if (canUse(sender, permission)) {
			return true;
		}

		plugin.langManager().send(sender, "general.no-permission");
		return false;
	}

	private void addIfAllowed(CommandSender sender, List<String> completions, String permission, String completion) {
		if (canUse(sender, permission)) {
			completions.add(completion);
		}
	}

	private List<String> onlinePlayerNames() {
		return Bukkit.getOnlinePlayers().stream()
				.map(Player::getName)
				.toList();
	}

	private List<String> filterCompletions(List<String> options, String input) {
		String lowerInput = input.toLowerCase(Locale.ROOT);

		return options.stream()
				.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowerInput))
				.distinct()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private HelpEntry help(String command, String description, String suggestedCommand) {
		return new HelpEntry(command, description, suggestedCommand);
	}

	private boolean isPlayerName(String value) {
		String normalizedValue = value.toLowerCase(Locale.ROOT);

		if (isConfiguredAction(normalizedValue)) {
			return false;
		}

		return !Set.of(
				"help", "me", "accept", "deny", "decline", "divorce", "list", "partner", "status",
				"tp", "sethome", "home", "homes", "delhome", "chat", "listenchat", "pvpon", "pvpoff",
				"announce", "compliment", "mail", "level", "xp", "achievements", "gift", "backpack",
				"anniversary", "pronouns", "title", "nickname", "requests", "block", "unblock", "blocklist",
				"priest", "reload"
		).contains(normalizedValue);
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