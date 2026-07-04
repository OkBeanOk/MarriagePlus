package com.okbeanok.marriagePlus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

public final class TextUtils {

	private TextUtils() {
	}

	public static String color(String message) {
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	public static Component legacy(String message) {
		return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
	}
}