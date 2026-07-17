package com.okbeanok.marriagePlus.commands;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FamilyCommand implements TabExecutor {

	private final MarriagePlus plugin;

	public FamilyCommand(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("family")) {
			return false;
		}

		if (!(sender instanceof Player player)) {
			plugin.langManager().send(sender, "general.players-only");
			return true;
		}

		if (!canUse(player)) {
			plugin.langManager().send(player, "general.no-permission");
			return true;
		}

		plugin.familyManager().familyCommand(player, args);
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!command.getName().equalsIgnoreCase("family")) {
			return Collections.emptyList();
		}

		if (!canUse(sender)) {
			return Collections.emptyList();
		}

		return switch (args.length) {
			case 1 -> completeFirstArg(args[0]);
			case 2 -> completeSecondArg( args[0], args[1]);
			default -> Collections.emptyList();
		};
	}

	private List<String> completeFirstArg(String input) {
		return filter(List.of(
				"help",
				"invite",
				"adopt",
				"accept",
				"deny",
				"decline",
				"leave",
				"kick",
				"rename",
				"chat",
				"tree",
				"web"
		), input);
	}

	private List<String> completeSecondArg(String firstArg, String input) {
		return switch (firstArg.toLowerCase(Locale.ROOT)) {
			case "invite", "adopt", "kick" -> filter(onlinePlayerNames(), input);
			default -> Collections.emptyList();
		};
	}

	private boolean canUse(CommandSender sender) {
		return sender.hasPermission("marriageplus.command.family") || sender.hasPermission("marriageplus.admin");
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
}