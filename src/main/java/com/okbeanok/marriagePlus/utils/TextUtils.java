package com.okbeanok.marriagePlus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtils {

	private static final Pattern MINI_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
	private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

	private TextUtils() {
	}

	public static String color(String message) {
		if (message == null || message.isEmpty()) {
			return "";
		}

		return ChatColor.translateAlternateColorCodes('&', applyHexColors(message));
	}

	public static Component legacy(String message) {
		return LegacyComponentSerializer.legacySection().deserialize(color(message));
	}

	private static String applyHexColors(String message) {
		String converted = convertHexPattern(message, MINI_HEX_PATTERN);
		return convertHexPattern(converted, AMPERSAND_HEX_PATTERN);
	}

	private static String convertHexPattern(String message, Pattern pattern) {
		Matcher matcher = pattern.matcher(message);
		StringBuilder builder = new StringBuilder();

		while (matcher.find()) {
			String hex = matcher.group(1);
			StringBuilder replacement = new StringBuilder("§x");

			for (char character : hex.toCharArray()) {
				replacement.append('§').append(character);
			}

			matcher.appendReplacement(builder, replacement.toString());
		}

		matcher.appendTail(builder);
		return builder.toString();
	}
}