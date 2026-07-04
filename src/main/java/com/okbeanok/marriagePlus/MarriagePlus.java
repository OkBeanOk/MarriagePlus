package com.okbeanok.marriagePlus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MarriagePlus extends JavaPlugin implements Listener, TabExecutor {

	private final Map<UUID, UUID> partners = new HashMap<>();
	private final Map<UUID, UUID> proposals = new HashMap<>();
	private final Map<UUID, Location> homes = new HashMap<>();
	private final Map<UUID, Inventory> backpacks = new HashMap<>();
	private final Map<UUID, Pronouns> pronouns = new HashMap<>();
	private final Map<UUID, Long> divorceConfirmations = new HashMap<>();
	private final Map<String, Long> cooldowns = new HashMap<>();

	private final Set<UUID> coupleChatToggled = new HashSet<>();
	private final Set<UUID> listeningToMarriageChat = new HashSet<>();
	private final Set<UUID> backpackAllowed = new HashSet<>();
	private final Set<String> pvpEnabledCouples = new HashSet<>();

	private File dataFile;
	private FileConfiguration dataConfig;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		setupDataFile();
		loadData();

		if (getCommand("marry") != null) {
			getCommand("marry").setExecutor(this);
			getCommand("marry").setTabCompleter(this);
		}

		Bukkit.getPluginManager().registerEvents(this, this);
		getLogger().info("MarriagePlus enabled!");
	}

	@Override
	public void onDisable() {
		saveData();
		getLogger().info("MarriagePlus has been disabled!");
	}

	private void setupDataFile() {
		dataFile = new File(getDataFolder(), "data.yml");

		if (!dataFile.exists()) {
			try {
				if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
					getLogger().warning("Could not create plugin data folder.");
				}

				if (dataFile.createNewFile()) {
					dataConfig = YamlConfiguration.loadConfiguration(dataFile);
					dataConfig.set("marriages", new HashMap<>());
					dataConfig.set("marriage-dates", new HashMap<>());
					dataConfig.set("homes", new HashMap<>());
					dataConfig.set("backpack-allowed", new ArrayList<>());
					dataConfig.set("pvp-enabled-couples", new ArrayList<>());
					dataConfig.set("backpacks", new HashMap<>());
					dataConfig.set("pronouns", new HashMap<>());
					saveDataFile();
				}
			} catch (IOException exception) {
				getLogger().severe("Could not create data.yml: " + exception.getMessage());
			}
		}

		dataConfig = YamlConfiguration.loadConfiguration(dataFile);
	}

	private void saveDataFile() {
		try {
			dataConfig.save(dataFile);
		} catch (IOException exception) {
			getLogger().severe("Could not save data.yml: " + exception.getMessage());
		}
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
			case "me" -> runWithPermission(sender, "marriageplus.command.me", player -> sendMarriageRequest(player, args));
			case "accept" -> requirePlayer(sender, this::acceptProposal);
			case "deny", "decline" -> requirePlayer(sender, this::denyProposal);
			case "divorce" -> handleDivorce(sender, args);
			case "list" -> {
				if (hasPermission(sender, "marriageplus.command.list")) {
					listMarriages(sender);
				}
			}
			case "partner" -> runWithPermission(sender, "marriageplus.command.partner", this::showPartner);
			case "status" -> runWithPermission(sender, "marriageplus.command.status", this::showStatus);
			case "tp" -> runWithPermission(sender, "marriageplus.command.tp", this::teleportToPartner);
			case "sethome" -> runWithPermission(sender, "marriageplus.command.home", this::setHome);
			case "home" -> runWithPermission(sender, "marriageplus.command.home", this::goHome);
			case "chat" -> runWithPermission(sender, "marriageplus.command.chat", player -> marriageChat(player, args));
			case "listenchat" -> requirePlayer(sender, this::toggleListenChat);
			case "pvpon" -> runWithPermission(sender, "marriageplus.command.pvp", player -> setPartnerPvp(player, true));
			case "pvpoff" -> runWithPermission(sender, "marriageplus.command.pvp", player -> setPartnerPvp(player, false));
			case "kiss", "hug", "cuddle", "highfive", "fuck" -> runWithPermission(sender, "marriageplus.command.actions", player -> configuredInteraction(player, args[0].toLowerCase(Locale.ROOT)));
			case "gift" -> runWithPermission(sender, "marriageplus.command.gift", this::giftItem);
			case "backpack" -> runWithPermission(sender, "marriageplus.command.backpack", player -> backpackCommand(player, args));
			case "anniversary" -> runWithPermission(sender, "marriageplus.command.anniversary", this::showAnniversary);
			case "pronouns" -> runWithPermission(sender, "marriageplus.command.pronouns", player -> pronounsCommand(player, args));
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

				if (getConfig().getBoolean("settings.nsfw-actions-enabled", false)) {
					completions.add("fuck");
				}
			}

			addIfAllowed(sender, completions, "marriageplus.command.gift", "gift");
			addIfAllowed(sender, completions, "marriageplus.command.backpack", "backpack");
			addIfAllowed(sender, completions, "marriageplus.command.anniversary", "anniversary");
			addIfAllowed(sender, completions, "marriageplus.command.pronouns", "pronouns");

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
				return filterCompletions(List.of("1", "2", "3", "4"), args[1]);
			}

			if (args[0].equalsIgnoreCase("me") || args[0].equalsIgnoreCase("priest") || args[0].equalsIgnoreCase("divorce")) {
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
				help("/marry pronouns custom <subject> <object> <possessive>", "Sets custom pronouns", "/marry pronouns custom ")
		));

		if (getConfig().getBoolean("settings.nsfw-actions-enabled", false)) {
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

	private void sendMarriageRequest(Player player, String[] args) {
		if (args.length < 2) {
			player.sendMessage(color("&cUsage: /marry me <player>"));
			return;
		}

		if (isMarried(player.getUniqueId())) {
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

		if (isMarried(target.getUniqueId())) {
			player.sendMessage(color("&cThat player is already married."));
			return;
		}

		proposals.put(target.getUniqueId(), player.getUniqueId());

		int expireSeconds = getConfig().getInt("settings.proposal-expire-seconds", 60);

		Bukkit.getScheduler().runTaskLater(this, () -> {
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
		target.sendMessage(color("&7Use &f/marry accept &7or &f/marry deny&7."));
		target.sendMessage(color("&7This request expires in &f" + expireSeconds + " seconds&7."));
	}

	private void acceptProposal(Player player) {
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

		marryPlayers(proposer, player);
	}

	private void denyProposal(Player player) {
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

		marryPlayers(first, second);
	}

	private void marryPlayers(Player first, Player second) {
		if (first.getUniqueId().equals(second.getUniqueId())) {
			first.sendMessage(color("&cYou cannot marry yourself."));
			return;
		}

		if (isMarried(first.getUniqueId()) || isMarried(second.getUniqueId())) {
			first.sendMessage(color("&cOne of you is already married."));
			second.sendMessage(color("&cOne of you is already married."));
			return;
		}

		partners.put(first.getUniqueId(), second.getUniqueId());
		partners.put(second.getUniqueId(), first.getUniqueId());

		long date = System.currentTimeMillis();
		dataConfig.set("marriage-dates." + first.getUniqueId(), date);
		dataConfig.set("marriage-dates." + second.getUniqueId(), date);

		saveData();

		Bukkit.broadcastMessage(color("&d❤ &f" + first.getName() + " &dand &f" + second.getName() + " &dare now married! ❤"));
	}

	private void selfDivorce(Player player, String[] args) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
			int confirmSeconds = getConfig().getInt("settings.divorce-confirm-seconds", 30);
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

		divorceCouple(player.getUniqueId(), partnerId);
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
		UUID partnerId = partners.get(target.getUniqueId());

		if (partnerId == null) {
			sender.sendMessage(color("&cThat player is not married."));
			return;
		}

		divorceCouple(target.getUniqueId(), partnerId);
		sender.sendMessage(color("&eDivorced " + target.getName() + "'s marriage."));
	}

	private void divorceCouple(UUID first, UUID second) {
		partners.remove(first);
		partners.remove(second);
		homes.remove(first);
		homes.remove(second);
		coupleChatToggled.remove(first);
		coupleChatToggled.remove(second);
		pvpEnabledCouples.remove(coupleKey(first, second));
		saveData();
	}

	private void listMarriages(CommandSender sender) {
		sender.sendMessage(color("&dMarried Players:"));

		Set<String> shown = new HashSet<>();

		for (Map.Entry<UUID, UUID> entry : partners.entrySet()) {
			String key = coupleKey(entry.getKey(), entry.getValue());

			if (shown.add(key)) {
				OfflinePlayer first = Bukkit.getOfflinePlayer(entry.getKey());
				OfflinePlayer second = Bukkit.getOfflinePlayer(entry.getValue());

				sender.sendMessage(color("&f- &d" + first.getName() + " &7❤ &d" + second.getName()));
			}
		}

		if (shown.isEmpty()) {
			sender.sendMessage(color("&7No players are married yet."));
		}
	}

	private void showPartner(Player player) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		player.sendMessage(color("&dYour partner is &f" + partner.getName() + " &7(" + getPronouns(partnerId).display() + ")&d."));
	}

	private void showStatus(Player player) {
		UUID partnerId = partners.get(player.getUniqueId());

		player.sendMessage(color("&d&m-----&r &dMarriage Status &d&m-----"));

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			player.sendMessage(color("&7Pronouns: &f" + getPronouns(player.getUniqueId()).display()));
			return;
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		long date = dataConfig.getLong("marriage-dates." + player.getUniqueId(), 0L);
		long days = date <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - date) / 86_400_000L);

		player.sendMessage(color("&7Partner: &f" + partner.getName()));
		player.sendMessage(color("&7Your Pronouns: &f" + getPronouns(player.getUniqueId()).display()));
		player.sendMessage(color("&7Partner Pronouns: &f" + getPronouns(partnerId).display()));
		player.sendMessage(color("&7Married For: &f" + days + " day(s)"));
		player.sendMessage(color("&7Couple Home: " + (homes.containsKey(player.getUniqueId()) ? "&aSet" : "&cNot Set")));
		player.sendMessage(color("&7Partner PvP: " + (pvpEnabledCouples.contains(coupleKey(player.getUniqueId(), partnerId)) ? "&aOn" : "&cOff")));
		player.sendMessage(color("&7Your Backpack Access: " + (backpackAllowed.contains(player.getUniqueId()) ? "&aAllowed" : "&cBlocked")));
		player.sendMessage(color("&7Partner Backpack Access: " + (backpackAllowed.contains(partnerId) ? "&aAllowed" : "&cBlocked")));
		player.sendMessage(color("&7Marriage Chat Toggle: " + (coupleChatToggled.contains(player.getUniqueId()) ? "&aOn" : "&cOff")));
	}

	private void teleportToPartner(Player player) {
		if (isOnCooldown(player, "tp")) {
			return;
		}

		Player partner = getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		setCooldown(player, "tp", getConfig().getInt("settings.cooldowns.tp-seconds", 30));
		player.teleport(partner.getLocation());
		player.sendMessage(color("&dTeleported to your partner."));
	}

	private void setHome(Player player) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		homes.put(player.getUniqueId(), player.getLocation());
		homes.put(partnerId, player.getLocation());
		saveData();

		player.sendMessage(color("&aCouple home set."));
	}

	private void goHome(Player player) {
		if (isOnCooldown(player, "home")) {
			return;
		}

		Location home = homes.get(player.getUniqueId());

		if (home == null) {
			player.sendMessage(color("&cYou do not have a couple home set."));
			return;
		}

		setCooldown(player, "home", getConfig().getInt("settings.cooldowns.home-seconds", 30));
		player.teleport(home);
		player.sendMessage(color("&dTeleported to your couple home."));
	}

	private void marriageChat(Player player, String[] args) {
		if (!isMarried(player.getUniqueId())) {
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

	private void sendCoupleChat(Player player, String message) {
		Player partner = getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		String formatted = color("&d[Marriage Chat] &f" + player.getName() + "&7: &f" + message);

		player.sendMessage(formatted);
		partner.sendMessage(formatted);

		for (UUID listenerId : listeningToMarriageChat) {
			Player listener = Bukkit.getPlayer(listenerId);

			if (listener != null && !listener.getUniqueId().equals(player.getUniqueId()) && !listener.getUniqueId().equals(partner.getUniqueId())) {
				listener.sendMessage(color("&8[Marriage Spy] &f" + player.getName() + " -> " + partner.getName() + "&7: &f" + message));
			}
		}
	}

	private void toggleListenChat(Player player) {
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

	private void setPartnerPvp(Player player, boolean enabled) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		String key = coupleKey(player.getUniqueId(), partnerId);

		if (enabled) {
			pvpEnabledCouples.add(key);
			player.sendMessage(color("&aPvP with your partner is now enabled."));
		} else {
			pvpEnabledCouples.remove(key);
			player.sendMessage(color("&ePvP with your partner is now disabled."));
		}

		saveData();
	}

	private void configuredInteraction(Player player, String actionName) {
		if (isOnCooldown(player, "action")) {
			return;
		}

		String actionPath = "actions." + actionName;

		if (!getConfig().contains(actionPath)) {
			player.sendMessage(color("&cUnknown marriage action."));
			return;
		}

		boolean actionIsNsfw = getConfig().getBoolean(actionPath + ".nsfw", false);
		boolean nsfwEnabled = getConfig().getBoolean("settings.nsfw-actions-enabled", false);

		if (actionIsNsfw && !nsfwEnabled) {
			player.sendMessage(color(getConfig().getString("messages.nsfw-disabled", "&cNSFW marriage actions are disabled on this server.")));
			return;
		}

		Player partner = getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		List<String> messages = getConfig().getStringList(actionPath + ".broadcast-messages");

		if (messages.isEmpty()) {
			player.sendMessage(color("&cThis action has no messages configured."));
			return;
		}

		String message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()))
				.replace("%player%", player.getName())
				.replace("%partner%", partner.getName());

		message = applyPronounPlaceholders(message, player, partner);

		playActionEffects(player, partner, actionPath);
		setCooldown(player, "action", getConfig().getInt("settings.cooldowns.action-seconds", 5));

		Bukkit.broadcastMessage(color(message));
	}

	private void playActionEffects(Player player, Player partner, String actionPath) {
		String particleName = getConfig().getString(actionPath + ".particle", "");
		String soundName = getConfig().getString(actionPath + ".sound", "");

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
		} else if (particleName != null && !particleName.isBlank()) {
			getLogger().warning("Invalid particle in config: " + particleName);
		}

		Sound sound = getSoundFromConfig(soundName);

		if (sound != null) {
			player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
			partner.playSound(partner.getLocation(), sound, 1.0F, 1.0F);
		} else if (soundName != null && !soundName.isBlank()) {
			getLogger().warning("Invalid sound in config: " + soundName);
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
		if (isOnCooldown(player, "gift")) {
			return;
		}

		Player partner = getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		ItemStack item = player.getInventory().getItemInMainHand();

		if (item.getType().isAir()) {
			player.sendMessage(color("&cYou must hold an item to gift."));
			return;
		}

		setCooldown(player, "gift", getConfig().getInt("settings.cooldowns.gift-seconds", 2));
		player.getInventory().setItemInMainHand(null);

		HashMap<Integer, ItemStack> leftovers = partner.getInventory().addItem(item);

		for (ItemStack leftover : leftovers.values()) {
			player.getWorld().dropItemNaturally(partner.getLocation(), leftover);
		}

		player.sendMessage(color("&aYou gifted your held item to " + partner.getName() + "."));
		partner.sendMessage(color("&d" + player.getName() + " gifted you an item."));
	}

	private void backpackCommand(Player player, String[] args) {
		if (args.length >= 2) {
			if (args[1].equalsIgnoreCase("on")) {
				backpackAllowed.add(player.getUniqueId());
				saveData();
				player.sendMessage(color("&aYour partner can now use your backpack."));
				return;
			}

			if (args[1].equalsIgnoreCase("off")) {
				backpackAllowed.remove(player.getUniqueId());
				saveData();
				player.sendMessage(color("&eYour partner can no longer use your backpack."));
				return;
			}
		}

		if (isOnCooldown(player, "backpack")) {
			return;
		}

		Player partner = getOnlinePartner(player);

		if (partner == null) {
			return;
		}

		if (!backpackAllowed.contains(partner.getUniqueId())) {
			player.sendMessage(color("&cYour partner has not allowed you to use their backpack."));
			return;
		}

		Inventory backpack = backpacks.computeIfAbsent(partner.getUniqueId(), this::loadBackpack);

		setCooldown(player, "backpack", getConfig().getInt("settings.cooldowns.backpack-seconds", 2));
		player.openInventory(backpack);
	}

	private Inventory loadBackpack(UUID owner) {
		OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(owner);
		String ownerName = offlineOwner.getName() == null ? "Player" : offlineOwner.getName();

		Inventory inventory = Bukkit.createInventory(null, 27, color("&d" + ownerName + "'s Marriage Backpack"));
		List<?> contents = dataConfig.getList("backpacks." + owner + ".contents");

		if (contents == null) {
			return inventory;
		}

		ItemStack[] items = new ItemStack[27];

		for (int index = 0; index < Math.min(contents.size(), items.length); index++) {
			Object object = contents.get(index);

			if (object instanceof ItemStack itemStack) {
				items[index] = itemStack;
			}
		}

		inventory.setContents(items);
		return inventory;
	}

	private void showAnniversary(Player player) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return;
		}

		long date = dataConfig.getLong("marriage-dates." + player.getUniqueId(), 0L);

		if (date <= 0L) {
			player.sendMessage(color("&7No marriage date saved."));
			return;
		}

		long days = Math.max(0L, (System.currentTimeMillis() - date) / 86_400_000L);
		player.sendMessage(color("&dYou have been married for &f" + days + " &dday(s)."));
	}

	private void pronounsCommand(Player player, String[] args) {
		if (args.length == 1) {
			Pronouns playerPronouns = getPronouns(player.getUniqueId());

			player.sendMessage(color("&dYour pronouns are &f" + playerPronouns.display() + "&d."));
			player.sendMessage(color("&7Use &f/marry pronouns he/him&7, &f/marry pronouns she/her&7, &f/marry pronouns they/them&7, &f/marry pronouns any&7."));
			player.sendMessage(color("&7Custom: &f/marry pronouns custom <subject> <object> <possessive>"));
			return;
		}

		if (args[1].equalsIgnoreCase("custom")) {
			if (args.length < 5) {
				player.sendMessage(color("&cUsage: /marry pronouns custom <subject> <object> <possessive>"));
				player.sendMessage(color("&7Example: &f/marry pronouns custom xe xem xyr"));
				return;
			}

			Pronouns customPronouns = new Pronouns(args[2], args[3], args[4], args[2] + "/" + args[3]);
			pronouns.put(player.getUniqueId(), customPronouns);
			saveData();

			player.sendMessage(color("&aYour pronouns are now &f" + customPronouns.display() + "&a."));
			return;
		}

		Pronouns selectedPronouns = parsePronouns(args[1]);

		if (selectedPronouns == null) {
			player.sendMessage(color("&cUnknown pronouns. Try he/him, she/her, they/them, any, or custom."));
			return;
		}

		pronouns.put(player.getUniqueId(), selectedPronouns);
		saveData();

		player.sendMessage(color("&aYour pronouns are now &f" + selectedPronouns.display() + "&a."));
	}

	private Pronouns parsePronouns(String input) {
		String normalized = input.toLowerCase(Locale.ROOT);

		return switch (normalized) {
			case "he", "him", "he/him" -> new Pronouns("he", "him", "his", "he/him");
			case "she", "her", "she/her" -> new Pronouns("she", "her", "her", "she/her");
			case "they", "them", "they/them" -> new Pronouns("they", "them", "their", "they/them");
			case "any", "any/all" -> new Pronouns("they", "them", "their", "any/all");
			default -> null;
		};
	}

	private Pronouns getPronouns(UUID uuid) {
		return pronouns.getOrDefault(uuid, new Pronouns(
				getConfig().getString("settings.default-pronouns.subject", "they"),
				getConfig().getString("settings.default-pronouns.object", "them"),
				getConfig().getString("settings.default-pronouns.possessive", "their"),
				getConfig().getString("settings.default-pronouns.display", "they/them")
		));
	}

	private String applyPronounPlaceholders(String message, Player player, Player partner) {
		Pronouns playerPronouns = getPronouns(player.getUniqueId());
		Pronouns partnerPronouns = getPronouns(partner.getUniqueId());

		return message
				.replace("%player_pronouns%", playerPronouns.display())
				.replace("%player_subject%", playerPronouns.subject())
				.replace("%player_object%", playerPronouns.object())
				.replace("%player_possessive%", playerPronouns.possessive())
				.replace("%partner_pronouns%", partnerPronouns.display())
				.replace("%partner_subject%", partnerPronouns.subject())
				.replace("%partner_object%", partnerPronouns.object())
				.replace("%partner_possessive%", partnerPronouns.possessive());
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

		reloadConfig();
		loadData();

		sender.sendMessage(color("&amarriageplus reloaded."));
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();

		if (!coupleChatToggled.contains(player.getUniqueId())) {
			return;
		}

		event.setCancelled(true);
		sendCoupleChat(player, event.getMessage());
	}

	@EventHandler
	public void onPartnerPvp(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}

		if (!(event.getDamager() instanceof Player attacker)) {
			return;
		}

		UUID attackerPartner = partners.get(attacker.getUniqueId());

		if (attackerPartner == null || !attackerPartner.equals(victim.getUniqueId())) {
			return;
		}

		if (!pvpEnabledCouples.contains(coupleKey(attacker.getUniqueId(), victim.getUniqueId()))) {
			event.setCancelled(true);
			attacker.sendMessage(color("&cPvP with your partner is disabled. Use /marry pvpon to enable it."));
		}
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		for (Map.Entry<UUID, Inventory> entry : backpacks.entrySet()) {
			if (entry.getValue().equals(event.getInventory())) {
				saveBackpack(entry.getKey(), event.getInventory());
				return;
			}
		}
	}

	private Player getOnlinePartner(Player player) {
		UUID partnerId = partners.get(player.getUniqueId());

		if (partnerId == null) {
			player.sendMessage(color("&cYou are not married."));
			return null;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner == null) {
			player.sendMessage(color("&cYour partner is not online."));
			return null;
		}

		return partner;
	}

	private boolean isMarried(UUID uuid) {
		return partners.containsKey(uuid);
	}

	private boolean isPlayerName(String value) {
		return !Set.of(
				"help", "me", "accept", "deny", "decline", "divorce", "list", "partner", "status",
				"tp", "sethome", "home", "chat", "listenchat", "pvpon", "pvpoff", "kiss", "hug",
				"cuddle", "highfive", "fuck", "gift", "backpack", "anniversary", "pronouns",
				"priest", "reload"
		).contains(value.toLowerCase(Locale.ROOT));
	}

	private String coupleKey(UUID first, UUID second) {
		return first.toString().compareTo(second.toString()) < 0
				? first + ":" + second
				: second + ":" + first;
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

	private boolean isOnCooldown(Player player, String key) {
		String cooldownKey = player.getUniqueId() + ":" + key;
		long expiresAt = cooldowns.getOrDefault(cooldownKey, 0L);

		if (System.currentTimeMillis() <= expiresAt) {
			long secondsLeft = Math.max(1L, (expiresAt - System.currentTimeMillis()) / 1000L);
			player.sendMessage(color("&cPlease wait &f" + secondsLeft + "s &cbefore using this again."));
			return true;
		}

		cooldowns.remove(cooldownKey);
		return false;
	}

	private void setCooldown(Player player, String key, int seconds) {
		if (seconds <= 0) {
			return;
		}

		cooldowns.put(player.getUniqueId() + ":" + key, System.currentTimeMillis() + seconds * 1000L);
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

	private void saveData() {
		dataConfig.set("marriages", null);
		dataConfig.set("homes", null);
		dataConfig.set("backpack-allowed", null);
		dataConfig.set("pvp-enabled-couples", null);
		dataConfig.set("pronouns", null);

		Set<String> savedCouples = new HashSet<>();

		for (Map.Entry<UUID, UUID> entry : partners.entrySet()) {
			String key = coupleKey(entry.getKey(), entry.getValue());

			if (savedCouples.add(key)) {
				dataConfig.set("marriages." + entry.getKey(), entry.getValue().toString());
			}
		}

		for (Map.Entry<UUID, Location> entry : homes.entrySet()) {
			saveLocation("homes." + entry.getKey(), entry.getValue());
		}

		dataConfig.set("backpack-allowed", backpackAllowed.stream().map(UUID::toString).toList());
		dataConfig.set("pvp-enabled-couples", pvpEnabledCouples.stream().toList());

		for (Map.Entry<UUID, Pronouns> entry : pronouns.entrySet()) {
			String path = "pronouns." + entry.getKey();
			Pronouns savedPronouns = entry.getValue();

			dataConfig.set(path + ".subject", savedPronouns.subject());
			dataConfig.set(path + ".object", savedPronouns.object());
			dataConfig.set(path + ".possessive", savedPronouns.possessive());
			dataConfig.set(path + ".display", savedPronouns.display());
		}

		for (Map.Entry<UUID, Inventory> entry : backpacks.entrySet()) {
			saveBackpack(entry.getKey(), entry.getValue());
		}

		saveDataFile();
	}

	private void loadData() {
		partners.clear();
		homes.clear();
		backpackAllowed.clear();
		pvpEnabledCouples.clear();
		pronouns.clear();
		backpacks.clear();

		ConfigurationSection marriagesSection = dataConfig.getConfigurationSection("marriages");

		if (marriagesSection != null) {
			for (String key : marriagesSection.getKeys(false)) {
				UUID first = UUID.fromString(key);
				UUID second = UUID.fromString(marriagesSection.getString(key));

				partners.put(first, second);
				partners.put(second, first);
			}
		}

		ConfigurationSection homesSection = dataConfig.getConfigurationSection("homes");

		if (homesSection != null) {
			for (String key : homesSection.getKeys(false)) {
				Location location = loadLocation("homes." + key);

				if (location != null) {
					homes.put(UUID.fromString(key), location);
				}
			}
		}

		for (String uuid : dataConfig.getStringList("backpack-allowed")) {
			backpackAllowed.add(UUID.fromString(uuid));
		}

		pvpEnabledCouples.addAll(dataConfig.getStringList("pvp-enabled-couples"));

		ConfigurationSection pronounsSection = dataConfig.getConfigurationSection("pronouns");

		if (pronounsSection != null) {
			for (String key : pronounsSection.getKeys(false)) {
				String path = "pronouns." + key;

				pronouns.put(UUID.fromString(key), new Pronouns(
						dataConfig.getString(path + ".subject", "they"),
						dataConfig.getString(path + ".object", "them"),
						dataConfig.getString(path + ".possessive", "their"),
						dataConfig.getString(path + ".display", "they/them")
				));
			}
		}

		ConfigurationSection backpacksSection = dataConfig.getConfigurationSection("backpacks");

		if (backpacksSection != null) {
			for (String key : backpacksSection.getKeys(false)) {
				UUID owner = UUID.fromString(key);
				backpacks.put(owner, loadBackpack(owner));
			}
		}
	}

	private void saveLocation(String path, Location location) {
		if (location.getWorld() == null) {
			return;
		}

		dataConfig.set(path + ".world", location.getWorld().getName());
		dataConfig.set(path + ".x", location.getX());
		dataConfig.set(path + ".y", location.getY());
		dataConfig.set(path + ".z", location.getZ());
		dataConfig.set(path + ".yaw", location.getYaw());
		dataConfig.set(path + ".pitch", location.getPitch());
	}

	private Location loadLocation(String path) {
		String worldName = dataConfig.getString(path + ".world");

		if (worldName == null) {
			return null;
		}

		World world = Bukkit.getWorld(worldName);

		if (world == null) {
			return null;
		}

		return new Location(
				world,
				dataConfig.getDouble(path + ".x"),
				dataConfig.getDouble(path + ".y"),
				dataConfig.getDouble(path + ".z"),
				(float) dataConfig.getDouble(path + ".yaw"),
				(float) dataConfig.getDouble(path + ".pitch")
		);
	}

	private void saveBackpack(UUID owner, Inventory inventory) {
		dataConfig.set("backpacks." + owner + ".contents", Arrays.asList(inventory.getContents()));
		saveDataFile();
	}

	private String color(String message) {
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	private Component legacy(String message) {
		return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
	}

	private record Pronouns(String subject, String object, String possessive, String display) {
	}

	private record HelpEntry(String command, String description, String suggestedCommand) {
	}

	@FunctionalInterface
	private interface PlayerAction {
		void run(Player player);
	}
}