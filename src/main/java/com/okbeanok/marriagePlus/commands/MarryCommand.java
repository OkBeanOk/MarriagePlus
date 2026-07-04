package com.okbeanok.marriagePlus.commands;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.managers.BackpackManager;
import com.okbeanok.marriagePlus.managers.CooldownManager;
import com.okbeanok.marriagePlus.managers.DataManager;
import com.okbeanok.marriagePlus.managers.HomeManager;
import com.okbeanok.marriagePlus.managers.MarriageManager;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
			DataManager dataManager
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

		switch (args[0].toLowerCase(Locale.ROOT)) {
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
			case "sethome" -> runWithPermission(sender, "marriageplus.command.home", homeManager::setHome);
			case "home" -> runWithPermission(sender, "marriageplus.command.home", homeManager::goHome);
			case "chat" -> runWithPermission(sender, "marriageplus.command.chat", player -> requestManager.marriageChat(player, args));
			case "listenchat" -> requirePlayer(sender, requestManager::toggleListenChat);
			case "pvpon" -> runWithPermission(sender, "marriageplus.command.pvp", player -> marriageManager.setPartnerPvp(player, true));
			case "pvpoff" -> runWithPermission(sender, "marriageplus.command.pvp", player -> marriageManager.setPartnerPvp(player, false));
			case "kiss", "hug", "cuddle", "highfive", "fuck" -> runWithPermission(sender, "marriageplus.command.actions", player -> configuredInteraction(player, args[0].toLowerCase(Locale.ROOT)));
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
			default -> sendHelp(sender, 1);
		}

		return true;
	}

	private void handleDivorce(CommandSender sender, String[] args) {
		if (args.length >= 2 && !args[1].equalsIgnoreCase("confirm")) {
			priestDivorce(sender, args[1]);
			return;
		}

		runWithPermission(sender, "marriageplus.command.divorce", player -> selfDivorce(player, args));
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
			}

			addIfAllowed(sender, completions, "marriageplus.command.chat", "chat");

			if (canUse(sender, "marriageplus.command.pvp")) {
				completions.add("pvpon");
				completions.add("pvpoff");
			}

			if (canUse(sender, "marriageplus.command.actions")) {
				completions.addAll(List.of("kiss", "hug", "cuddle", "highfive"));

				if (plugin.getConfig().getBoolean("settings.nsfw-actions-enabled", false)) {
					completions.add("fuck");
				}
			}

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
			if (args[0].equalsIgnoreCase("help")) {
				return filterCompletions(List.of("1", "2", "3", "4", "5", "6"), args[1]);
			}

			if (args[0].equalsIgnoreCase("me")
					|| args[0].equalsIgnoreCase("priest")
					|| args[0].equalsIgnoreCase("divorce")
					|| args[0].equalsIgnoreCase("block")
					|| args[0].equalsIgnoreCase("unblock")) {
				return filterCompletions(onlinePlayerNames(), args[1]);
			}

			if (args[0].equalsIgnoreCase("backpack")) {
				return filterCompletions(List.of("on", "off"), args[1]);
			}

			if (args[0].equalsIgnoreCase("chat")) {
				return filterCompletions(List.of("toggle"), args[1]);
			}

			if (args[0].equalsIgnoreCase("pronouns")) {
				return filterCompletions(List.of("he/him", "she/her", "they/them", "any", "custom"), args[1]);
			}

			if (args[0].equalsIgnoreCase("title")) {
				return filterCompletions(List.of("on", "off"), args[1]);
			}

			if (args[0].equalsIgnoreCase("nickname")) {
				return filterCompletions(List.of("clear"), args[1]);
			}

			if (args[0].equalsIgnoreCase("requests")) {
				return filterCompletions(List.of("on", "off"), args[1]);
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

	private void sendHelp(CommandSender sender, String[] args) {
		int page = 1;

		if (args.length >= 2) {
			try {
				page = Integer.parseInt(args[1]);
			} catch (NumberFormatException ignored) {
				sender.sendMessage(color("&cUsage: /marry help <page>"));
				return;
			}
		}

		sendHelp(sender, page);
	}

	private void sendHelp(CommandSender sender, int page) {
		List<HelpEntry> entries = new ArrayList<>(List.of(
				help("/marry", "List all marry commands", "/marry"),
				help("/marry help <page>", "Shows a help page", "/marry help "),
				help("/marry me <player>", "Sends a marry request", "/marry me "),
				help("/marry accept", "Accept a marry request", "/marry accept"),
				help("/marry deny", "Deny a marry request", "/marry deny"),
				help("/marry divorce", "Divorce your partner", "/marry divorce"),
				help("/marry divorce confirm", "Confirms divorce", "/marry divorce confirm"),
				help("/marry list", "Shows all married players", "/marry list"),
				help("/marry partner", "Shows your partner", "/marry partner"),
				help("/marry status", "Shows your marriage status", "/marry status"),
				help("/marry tp", "Teleports to your partner", "/marry tp"),
				help("/marry sethome", "Sets your couple home", "/marry sethome"),
				help("/marry home", "Teleports to your couple home", "/marry home"),
				help("/marry chat <message>", "Sends private couple chat", "/marry chat "),
				help("/marry chat toggle", "Toggles couple chat mode", "/marry chat toggle"),
				help("/marry pvpon", "Enables PvP with your partner", "/marry pvpon"),
				help("/marry pvpoff", "Disables PvP with your partner", "/marry pvpoff"),
				help("/marry kiss", "Kiss your partner", "/marry kiss"),
				help("/marry hug", "Hug your partner", "/marry hug"),
				help("/marry cuddle", "Cuddle your partner", "/marry cuddle"),
				help("/marry highfive", "High-five your partner", "/marry highfive"),
				help("/marry gift", "Gift held item to your partner", "/marry gift"),
				help("/marry backpack", "Opens partner backpack if allowed", "/marry backpack"),
				help("/marry backpack on", "Allows partner backpack access", "/marry backpack on"),
				help("/marry backpack off", "Blocks partner backpack access", "/marry backpack off"),
				help("/marry anniversary", "Shows your marriage date", "/marry anniversary"),
				help("/marry pronouns", "Shows your pronouns", "/marry pronouns"),
				help("/marry pronouns <he/him|she/her|they/them|any>", "Sets your pronouns", "/marry pronouns "),
				help("/marry pronouns custom <subject> <object> <possessive>", "Sets custom pronouns", "/marry pronouns custom "),
				help("/marry title on", "Enables your marriage title", "/marry title on"),
				help("/marry title off", "Disables your marriage title", "/marry title off"),
				help("/marry title <text>", "Sets a custom marriage title", "/marry title "),
				help("/marry nickname <name>", "Sets your nickname for your partner", "/marry nickname "),
				help("/marry nickname clear", "Clears your partner nickname", "/marry nickname clear"),
				help("/marry requests on", "Allows marriage requests", "/marry requests on"),
				help("/marry requests off", "Blocks marriage requests", "/marry requests off"),
				help("/marry block <player>", "Blocks marriage requests from a player", "/marry block "),
				help("/marry unblock <player>", "Unblocks marriage requests from a player", "/marry unblock "),
				help("/marry blocklist", "Shows your blocked players", "/marry blocklist")
		));

		if (plugin.getConfig().getBoolean("settings.nsfw-actions-enabled", false)) {
			entries.add(help("/marry fuck", "Adult marriage action", "/marry fuck"));
		}

		if (sender.hasPermission("marriageplus.priest") || sender.hasPermission("marriageplus.admin")) {
			entries.add(help("/marry <player1> <player2>", "Marries two players", "/marry "));
			entries.add(help("/marry divorce <player>", "Divorces that player's marriage", "/marry divorce "));
		}

		if (sender.hasPermission("marriageplus.admin")) {
			entries.add(help("/marry priest <player>", "Shows priest permission command", "/marry priest "));
			entries.add(help("/marry listenchat", "Staff spy for couple chat", "/marry listenchat"));
			entries.add(help("/marry reload", "Reloads config/data", "/marry reload"));
		}

		int entriesPerPage = 8;
		int maxPage = Math.max(1, (int) Math.ceil(entries.size() / (double) entriesPerPage));
		page = Math.max(1, Math.min(page, maxPage));

		sender.sendMessage(color("&d&m-----&r &dMarriage Commands &7(Page " + page + "/" + maxPage + ") &d&m-----"));

		int start = (page - 1) * entriesPerPage;
		int end = Math.min(start + entriesPerPage, entries.size());

		for (int index = start; index < end; index++) {
			sendClickableHelpEntry(sender, entries.get(index));
		}

		sendHelpPageSelector(sender, page, maxPage);
	}

	private void sendClickableHelpEntry(CommandSender sender, HelpEntry entry) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(color("&f" + entry.command() + " &7- " + entry.description()));
			return;
		}

		Component commandComponent = legacy("&f" + entry.command())
				.clickEvent(ClickEvent.suggestCommand(entry.suggestedCommand()))
				.hoverEvent(HoverEvent.showText(legacy("&dClick to put this command in chat\n&7" + entry.description())));

		player.sendMessage(commandComponent.append(legacy(" &7- " + entry.description())));
	}

	private void sendHelpPageSelector(CommandSender sender, int currentPage, int maxPage) {
		if (!(sender instanceof Player player)) {
			StringBuilder consoleSelector = new StringBuilder(color("&7Pages: "));

			for (int page = 1; page <= maxPage; page++) {
				if (page > 1) {
					consoleSelector.append(color(" &7> "));
				}

				consoleSelector.append(color(page == currentPage ? "&d[" + page + "]" : "&f[" + page + "]"));
			}

			sender.sendMessage(consoleSelector.toString());
			return;
		}

		Component selector = legacy("&7Pages: ");

		for (int page = 1; page <= maxPage; page++) {
			if (page > 1) {
				selector = selector.append(legacy(" &7> "));
			}

			selector = selector.append(legacy(page == currentPage ? "&d[" + page + "]" : "&f[" + page + "]")
					.clickEvent(ClickEvent.runCommand("/marry help " + page))
					.hoverEvent(HoverEvent.showText(legacy("&7Click to open help page &f" + page))));
		}

		player.sendMessage(selector);
	}

	private void marryTwoPlayers(CommandSender sender, String firstName, String secondName) {
		if (!sender.hasPermission("marriageplus.priest") && !sender.hasPermission("marriageplus.admin")) {
			sender.sendMessage(color("&cOnly priests can marry two players."));
			return;
		}

		Player first = Bukkit.getPlayerExact(firstName);
		Player second = Bukkit.getPlayerExact(secondName);

		if (first == null || second == null) {
			sender.sendMessage(color("&cBoth players must be online."));
			return;
		}

		marriageManager.marryPlayers(first, second);
	}

	private void selfDivorce(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
			int confirmSeconds = plugin.getConfig().getInt("settings.divorce-confirm-seconds", 30);
			divorceConfirmations.put(player.getUniqueId(), System.currentTimeMillis() + confirmSeconds * 1000L);

			player.sendMessage(color("&eAre you sure you want to divorce your partner?"));
			player.sendMessage(color("&7Type &f/marry divorce confirm &7within &f" + confirmSeconds + " seconds &7to confirm."));
			return;
		}

		long expiresAt = divorceConfirmations.getOrDefault(player.getUniqueId(), 0L);

		if (System.currentTimeMillis() > expiresAt) {
			divorceConfirmations.remove(player.getUniqueId());
			player.sendMessage(color("&cYour divorce confirmation expired. Use &f/marry divorce &cagain."));
			return;
		}

		divorceConfirmations.remove(player.getUniqueId());

		marriageManager.divorceCouple(player.getUniqueId(), partnerId);
		player.sendMessage(color("&eYou divorced your partner."));

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner != null) {
			partner.sendMessage(color("&e" + player.getName() + " divorced you."));
		}
	}

	private void priestDivorce(CommandSender sender, String playerName) {
		if (!sender.hasPermission("marriageplus.priest") && !sender.hasPermission("marriageplus.admin")) {
			sender.sendMessage(color("&cOnly priests can divorce other players."));
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
		UUID partnerId = marriageManager.getPartnerId(target.getUniqueId());

		if (partnerId == null) {
			sender.sendMessage(color("&cThat player is not married."));
			return;
		}

		marriageManager.divorceCouple(target.getUniqueId(), partnerId);
		sender.sendMessage(color("&eDivorced " + target.getName() + "'s marriage."));
	}

	private void showPartner(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		player.sendMessage(color("&dYour partner is &f"
				+ socialManager.getPartnerDisplayName(player.getUniqueId(), partnerId)
				+ " &7(" + pronounManager.getPronouns(partnerId).display() + ")&d."));
	}

	private void showStatus(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		player.sendMessage(color("&d&m-----&r &dMarriage Status &d&m-----"));

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			player.sendMessage(color("&7Pronouns: &f" + pronounManager.getPronouns(player.getUniqueId()).display()));
			return;
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		long date = dataManager.dataConfig().getLong("marriage-dates." + player.getUniqueId(), 0L);
		long days = date <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - date) / 86_400_000L);

		player.sendMessage(color("&7Partner: &f" + partner.getName()));
		player.sendMessage(color("&7Partner Nickname: &f" + socialManager.getPartnerDisplayName(player.getUniqueId(), partnerId)));
		player.sendMessage(color("&7Marriage Title: &f" + socialManager.getMarriageTitle(player.getUniqueId(), partnerId)));
		player.sendMessage(color("&7Your Pronouns: &f" + pronounManager.getPronouns(player.getUniqueId()).display()));
		player.sendMessage(color("&7Partner Pronouns: &f" + pronounManager.getPronouns(partnerId).display()));
		player.sendMessage(color("&7Married For: &f" + days + " day(s)"));
		player.sendMessage(color("&7Couple Home: " + (homeManager.hasHome(player.getUniqueId()) ? "&aSet" : "&cNot Set")));
		player.sendMessage(color("&7Partner PvP: " + (marriageManager.pvpEnabledCouples().contains(marriageManager.coupleKey(player.getUniqueId(), partnerId)) ? "&aOn" : "&cOff")));
		player.sendMessage(color("&7Your Backpack Access: " + (backpackManager.backpackAllowed().contains(player.getUniqueId()) ? "&aAllowed" : "&cBlocked")));
		player.sendMessage(color("&7Partner Backpack Access: " + (backpackManager.backpackAllowed().contains(partnerId) ? "&aAllowed" : "&cBlocked")));
		player.sendMessage(color("&7Marriage Chat Toggle: " + (requestManager.coupleChatToggled().contains(player.getUniqueId()) ? "&aOn" : "&cOff")));
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
		player.sendMessage(color("&dTeleported to your partner."));
	}

	private void configuredInteraction(Player player, String actionName) {
		if (cooldownManager.isOnCooldown(player, "action")) {
			return;
		}

		String actionPath = "actions." + actionName;

		if (!plugin.getConfig().contains(actionPath)) {
			player.sendMessage(color("&cUnknown marriage action."));
			return;
		}

		boolean actionIsNsfw = plugin.getConfig().getBoolean(actionPath + ".nsfw", false);
		boolean nsfwEnabled = plugin.getConfig().getBoolean("settings.nsfw-actions-enabled", false);

		if (actionIsNsfw && !nsfwEnabled) {
			player.sendMessage(color(plugin.getConfig().getString("messages.nsfw-disabled", "&cNSFW marriage actions are disabled on this server.")));
			return;
		}

		Player partner = marriageManager.getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		List<String> messages = plugin.getConfig().getStringList(actionPath + ".broadcast-messages");

		if (messages.isEmpty()) {
			player.sendMessage(color("&cThis action has no messages configured."));
			return;
		}

		String message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()))
				.replace("%player%", player.getName())
				.replace("%partner%", partner.getName());

		message = pronounManager.applyPronounPlaceholders(message, player, partner);

		playActionEffects(player, partner, actionPath);
		cooldownManager.setCooldown(player, "action", plugin.getConfig().getInt("settings.cooldowns.action-seconds", 5));

		Bukkit.broadcastMessage(color(message));
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
			plugin.getLogger().warning("Invalid particle in config: " + particleName);
		}

		Sound sound = getSoundFromConfig(soundName);

		if (sound != null) {
			player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
			partner.playSound(partner.getLocation(), sound, 1.0F, 1.0F);
		} else if (!soundName.isBlank()) {
			plugin.getLogger().warning("Invalid sound in config: " + soundName);
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
			player.sendMessage(color("&cYou must hold an item to gift."));
			return;
		}

		cooldownManager.setCooldown(player, "gift", plugin.getConfig().getInt("settings.cooldowns.gift-seconds", 2));
		player.getInventory().setItemInMainHand(null);

		HashMap<Integer, ItemStack> leftovers = partner.getInventory().addItem(item);

		for (ItemStack leftover : leftovers.values()) {
			player.getWorld().dropItemNaturally(partner.getLocation(), leftover);
		}

		player.sendMessage(color("&aYou gifted your held item to " + partner.getName() + "."));
		partner.sendMessage(color("&d" + player.getName() + " gifted you an item."));
	}

	private void showAnniversary(Player player) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		long date = dataManager.dataConfig().getLong("marriage-dates." + player.getUniqueId(), 0L);

		if (date <= 0L) {
			player.sendMessage(color("&7No marriage date saved."));
			return;
		}

		long days = Math.max(0L, (System.currentTimeMillis() - date) / 86_400_000L);
		player.sendMessage(color("&dYou have been married for &f" + days + " &dday(s)."));
	}

	private void setPriest(CommandSender sender, String[] args) {
		if (!sender.hasPermission("marriageplus.admin")) {
			sender.sendMessage(color("&cYou do not have permission."));
			return;
		}

		if (args.length < 2) {
			sender.sendMessage(color("&cUsage: /marry priest <player>"));
			return;
		}

		sender.sendMessage(color("&aGive &f" + args[1] + " &athe permission: &fmarriageplus.priest"));
		sender.sendMessage(color("&7If you use LuckPerms: &f/lp user " + args[1] + " permission set marriageplus.priest true"));
	}

	private void reloadMarriagePlugin(CommandSender sender) {
		if (!sender.hasPermission("marriageplus.admin")) {
			sender.sendMessage(color("&cYou do not have permission."));
			return;
		}

		plugin.reloadConfig();
		dataManager.loadData();

		sender.sendMessage(color("&amarriageplus reloaded."));
	}

	private void runWithPermission(CommandSender sender, String permission, PlayerAction action) {
		if (!hasPermission(sender, permission)) {
			return;
		}

		requirePlayer(sender, action);
	}

	private void requirePlayer(CommandSender sender, PlayerAction action) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(color("&cOnly players can use this command."));
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

		sender.sendMessage(color("&cYou do not have permission."));
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
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private HelpEntry help(String command, String description, String suggestedCommand) {
		return new HelpEntry(command, description, suggestedCommand);
	}

	private boolean isPlayerName(String value) {
		return !Set.of(
				"help", "me", "accept", "deny", "decline", "divorce", "list", "partner", "status",
				"tp", "sethome", "home", "chat", "listenchat", "pvpon", "pvpoff", "kiss", "hug",
				"cuddle", "highfive", "fuck", "gift", "backpack", "anniversary", "pronouns",
				"title", "nickname", "requests", "block", "unblock", "blocklist",
				"priest", "reload"
		).contains(value.toLowerCase(Locale.ROOT));
	}

	@FunctionalInterface
	private interface PlayerAction {
		void run(Player player);
	}
}