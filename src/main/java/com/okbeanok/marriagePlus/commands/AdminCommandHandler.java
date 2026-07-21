package com.okbeanok.marriagePlus.commands;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.HelpEntry;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.achievement.AchievementManager;
import com.okbeanok.marriagePlus.services.backpacks.BackpackManager;
import com.okbeanok.marriagePlus.services.families.FamilyMarriageCheckResult;
import com.okbeanok.marriagePlus.services.homes.HomeManager;
import com.okbeanok.marriagePlus.services.request.RequestManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import com.okbeanok.marriagePlus.utils.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AdminCommandHandler {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final BackpackManager backpackManager;
	private final HomeManager homeManager;
	private final RequestManager requestManager;
	private final MarriageXpManager marriageXpManager;
	private final AchievementManager achievementManager;
	private final DataManager dataManager;

	public AdminCommandHandler(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			BackpackManager backpackManager,
			HomeManager homeManager,
			RequestManager requestManager,
			MarriageXpManager marriageXpManager,
			AchievementManager achievementManager,
			DataManager dataManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.backpackManager = backpackManager;
		this.homeManager = homeManager;
		this.requestManager = requestManager;
		this.marriageXpManager = marriageXpManager;
		this.achievementManager = achievementManager;
		this.dataManager = dataManager;
	}

	public void handle(CommandSender sender, String[] args) {
		if (!sender.hasPermission("marriageplus.admin") && !sender.hasPermission("marriageplus.admin.backpack")) {
			plugin.langManager().send(sender, "general.no-permission");
			return;
		}

		if (args.length < 2) {
			plugin.langManager().send(sender, "admin.usage");
			return;
		}

		switch (args[1].toLowerCase(Locale.ROOT)) {
			case "backpack" -> adminBackpack(sender, args);
			case "achievement", "achievements" -> adminAchievement(sender, args);
			case "info" -> adminInfo(sender, args);
			case "marry" -> adminMarry(sender, args);
			case "divorce" -> adminDivorce(sender, args);
			case "reset" -> adminReset(sender, args);
			default -> plugin.langManager().send(sender, "admin.usage");
		}
	}

	public List<String> completeSecondArg(CommandSender sender, String input) {
		List<String> options = new ArrayList<>();

		if (canUseAdminBackpack(sender)) {
			options.add("backpack");
		}

		if (sender.hasPermission("marriageplus.admin")) {
			options.add("achievement");
			options.add("achievements");
			options.add("info");
			options.add("marry");
			options.add("divorce");
			options.add("reset");
		}

		return filter(options, input);
	}

	public List<String> completeThirdArg(CommandSender sender, String subCommand, String input) {
		String sub = subCommand.toLowerCase(Locale.ROOT);

		if (sub.equals("backpack") && canUseAdminBackpack(sender)) {
			return filter(onlinePlayerNames(), input);
		}

		if ((sub.equals("info") || sub.equals("marry") || sub.equals("divorce") || sub.equals("reset"))
				&& sender.hasPermission("marriageplus.admin")) {
			return filter(onlinePlayerNames(), input);
		}

		if (isAchievementSubcommand(sub) && sender.hasPermission("marriageplus.admin")) {
			return filter(List.of("grant", "revoke"), input);
		}

		return List.of();
	}

	public List<String> completeFourthArg(CommandSender sender, String subCommand, String action, String input) {
		String sub = subCommand.toLowerCase(Locale.ROOT);

		if (sub.equals("marry") && sender.hasPermission("marriageplus.admin")) {
			return filter(onlinePlayerNames(), input);
		}

		if (isAchievementSubcommand(sub)
				&& (action.equalsIgnoreCase("grant") || action.equalsIgnoreCase("revoke"))
				&& sender.hasPermission("marriageplus.admin")) {
			return filter(onlinePlayerNames(), input);
		}

		return List.of();
	}

	public List<String> completeFifthArg(CommandSender sender, String subCommand, String action, String input) {
		if (isAchievementSubcommand(subCommand)
				&& (action.equalsIgnoreCase("grant") || action.equalsIgnoreCase("revoke"))
				&& sender.hasPermission("marriageplus.admin")) {
			return filter(new ArrayList<>(achievementManager.definitions().keySet()), input);
		}

		return List.of();
	}

	public void addHelpEntries(CommandSender sender, List<HelpEntry> entries) {
		if (canUseAdminBackpack(sender)) {
			entries.add(help("/marry admin backpack <player>", "admin-backpack", "/marry admin backpack "));
		}

		if (!sender.hasPermission("marriageplus.admin")) {
			return;
		}

		entries.add(help("/marry priest <player>", "priest", "/marry priest "));
		entries.add(help("/marry admin info <player>", "admin-info", "/marry admin info "));
		entries.add(help("/marry admin marry <player1> <player2>", "admin-marry", "/marry admin marry "));
		entries.add(help("/marry admin divorce <player>", "admin-divorce", "/marry admin divorce "));
		entries.add(help("/marry admin reset <player>", "admin-reset", "/marry admin reset "));
		entries.add(help("/marry admin achievement grant <player> <id>", "admin-achievement-grant", "/marry admin achievement grant "));
		entries.add(help("/marry admin achievement revoke <player> <id>", "admin-achievement-revoke", "/marry admin achievement revoke "));
		entries.add(help("/marry listenchat", "listenchat", "/marry listenchat"));
		entries.add(help("/marry reload", "reload", "/marry reload"));
	}

	private void adminInfo(CommandSender sender, String[] args) {
		if (!requireAdmin(sender)) {
			return;
		}

		if (args.length < 3) {
			plugin.langManager().send(sender, "admin.info-usage");
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
		UUID targetId = target.getUniqueId();
		UUID partnerId = marriageManager.getPartnerId(targetId);

		plugin.langManager().send(sender, "admin.info-header");
		plugin.langManager().send(sender, "admin.info-player", Map.of(
				"%player%", safeName(target)
		));

		if (partnerId == null) {
			plugin.langManager().send(sender, "admin.info-married", Map.of(
					"%status%", plugin.langManager().get("status.values.no")
			));
			return;
		}

		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
		long marriedAt = dataManager.dataConfig().getLong("marriage-dates." + targetId, 0L);
		long marriedDays = marriedAt <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - marriedAt) / 86_400_000L);
		String coupleKey = marriageManager.coupleKey(targetId, partnerId);

		plugin.langManager().send(sender, "admin.info-married", Map.of(
				"%status%", plugin.langManager().get("status.values.yes")
		));
		plugin.langManager().send(sender, "admin.info-partner", Map.of(
				"%partner%", safeName(partner)
		));
		plugin.langManager().send(sender, "admin.info-married-days", Map.of(
				"%days%", String.valueOf(marriedDays)
		));
		plugin.langManager().send(sender, "admin.info-level", Map.of(
				"%level%", String.valueOf(marriageXpManager.getLevel(targetId))
		));
		plugin.langManager().send(sender, "admin.info-xp", Map.of(
				"%xp%", String.valueOf(marriageXpManager.getXp(targetId)),
				"%required%", String.valueOf(marriageXpManager.getXpRequired(targetId))
		));
		plugin.langManager().send(sender, "admin.info-homes", Map.of(
				"%homes%", String.valueOf(homeManager.getHomeCount(targetId))
		));
		plugin.langManager().send(sender, "admin.info-pvp", Map.of(
				"%status%", booleanStatus(marriageManager.pvpEnabledCouples().contains(coupleKey))
		));
		plugin.langManager().send(sender, "admin.info-backpack-access", Map.of(
				"%status%", accessStatus(backpackManager.backpackAllowed().contains(targetId))
		));
	}

	private void adminMarry(CommandSender sender, String[] args) {
		if (!requireAdmin(sender)) {
			return;
		}

		if (args.length < 4) {
			plugin.langManager().send(sender, "admin.marry-usage");
			return;
		}

		forceMarry(sender, args[2], args[3]);
	}

	private void adminDivorce(CommandSender sender, String[] args) {
		if (!requireAdmin(sender)) {
			return;
		}

		if (args.length < 3) {
			plugin.langManager().send(sender, "admin.divorce-usage");
			return;
		}

		forceDivorce(sender, args[2]);
	}

	private void adminReset(CommandSender sender, String[] args) {
		if (!requireAdmin(sender)) {
			return;
		}

		if (args.length < 3) {
			plugin.langManager().send(sender, "admin.reset-usage");
			return;
		}

		OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
		UUID targetId = target.getUniqueId();
		UUID partnerId = marriageManager.getPartnerId(targetId);

		if (partnerId != null) {
			marriageManager.divorceCouple(targetId, partnerId);
			sendIfOnline(partnerId, "divorce.partner-divorced-you", Map.of("%player%", sender.getName()));
		}

		homeManager.homes().remove(targetId);
		backpackManager.backpackAllowed().remove(targetId);
		requestManager.coupleChatToggled().remove(targetId);
		marriageManager.pvpEnabledCouples().removeIf(key -> key.contains(targetId.toString()));

		dataManager.dataConfig().set("marriage-dates." + targetId, null);
		dataManager.saveData();

		plugin.langManager().send(sender, "admin.reset-done", Map.of(
				"%player%", safeName(target)
		));

		sendIfOnline(targetId, "admin.reset-target");
	}

	private void adminBackpack(CommandSender sender, String[] args) {
		if (!canUseAdminBackpack(sender)) {
			plugin.langManager().send(sender, "general.no-permission");
			return;
		}

		if (!(sender instanceof Player player)) {
			plugin.langManager().send(sender, "general.players-only");
			return;
		}

		if (args.length < 3) {
			plugin.langManager().send(player, "admin.backpack-usage");
			return;
		}

		backpackManager.openAdminBackpack(player, args[2]);
	}



	private void adminAchievement(CommandSender sender, String[] args) {
		if (!requireAdmin(sender)) {
			return;
		}

		if (args.length < 5) {
			plugin.langManager().send(sender, "admin.achievement-usage");
			return;
		}

		String action = args[2].toLowerCase(Locale.ROOT);
		OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
		String achievementId = args[4];

		if (!achievementManager.definitions().containsKey(achievementId)) {
			plugin.langManager().send(sender, "admin.achievement-not-found", Map.of(
					"%achievement%", achievementId
			));
			return;
		}

		if (marriageManager.getPartnerId(target.getUniqueId()) == null) {
			plugin.langManager().send(sender, "marriage.target-not-married");
			return;
		}

		switch (action) {
			case "grant" -> grantAchievement(sender, target, achievementId);
			case "revoke" -> revokeAchievement(sender, target, achievementId);
			default -> plugin.langManager().send(sender, "admin.achievement-usage");
		}
	}

	private void grantAchievement(CommandSender sender, OfflinePlayer target, String achievementId) {
		if (achievementManager.hasUnlocked(target.getUniqueId(), achievementId)) {
			plugin.langManager().send(sender, "admin.achievement-already-unlocked", Map.of(
					"%player%", safeName(target),
					"%achievement%", achievementId
			));
			return;
		}

		if (!achievementManager.grant(target.getUniqueId(), achievementId)) {
			plugin.langManager().send(sender, "admin.achievement-grant-failed", Map.of(
					"%player%", safeName(target),
					"%achievement%", achievementId
			));
			return;
		}

		plugin.langManager().send(sender, "admin.achievement-granted", Map.of(
				"%player%", safeName(target),
				"%achievement%", achievementId
		));

		sendIfOnline(target.getUniqueId(), "achievements.admin-granted", Map.of(
				"%achievement%", achievementId
		));
	}

	private void revokeAchievement(CommandSender sender, OfflinePlayer target, String achievementId) {
		if (!achievementManager.hasUnlocked(target.getUniqueId(), achievementId)) {
			plugin.langManager().send(sender, "admin.achievement-not-unlocked", Map.of(
					"%player%", safeName(target),
					"%achievement%", achievementId
			));
			return;
		}

		if (!achievementManager.revoke(target.getUniqueId(), achievementId)) {
			plugin.langManager().send(sender, "admin.achievement-revoke-failed", Map.of(
					"%player%", safeName(target),
					"%achievement%", achievementId
			));
			return;
		}

		plugin.langManager().send(sender, "admin.achievement-revoked", Map.of(
				"%player%", safeName(target),
				"%achievement%", achievementId
		));

		sendIfOnline(target.getUniqueId(), "achievements.admin-revoked", Map.of(
				"%achievement%", achievementId
		));
	}

	public void forceMarry(CommandSender sender, String firstName, String secondName) {
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

	public void forceDivorce(CommandSender sender, String playerName) {
		OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
		UUID partnerId = marriageManager.getPartnerId(target.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(sender, "marriage.target-not-married");
			return;
		}

		marriageManager.divorceCouple(target.getUniqueId(), partnerId);

		plugin.langManager().send(sender, "priest.divorced-marriage", Map.of(
				"%player%", safeName(target)
		));

		sendIfOnline(target.getUniqueId(), "divorce.success-self");
		sendIfOnline(partnerId, "divorce.partner-divorced-you", Map.of(
				"%player%", sender.getName()
		));
	}

	public boolean canUseAdminBackpack(CommandSender sender) {
		return sender.hasPermission("marriageplus.admin") || sender.hasPermission("marriageplus.admin.backpack");
	}

	private boolean requireAdmin(CommandSender sender) {
		if (sender.hasPermission("marriageplus.admin")) {
			return true;
		}

		plugin.langManager().send(sender, "general.no-permission");
		return false;
	}

	private boolean isAchievementSubcommand(String subCommand) {
		return subCommand.equalsIgnoreCase("achievement") || subCommand.equalsIgnoreCase("achievements");
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

	private String booleanStatus(boolean enabled) {
		return plugin.langManager().get(enabled ? "status.values.on" : "status.values.off");
	}

	private String accessStatus(boolean allowed) {
		return plugin.langManager().get(allowed ? "status.values.allowed" : "status.values.blocked");
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plugin.langManager().get("general.unknown") : player.getName();
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

	private HelpEntry help(String command, String key, String suggestedCommand) {
		return new HelpEntry(command, plugin.langManager().get("help.descriptions." + key), suggestedCommand);
	}
}