package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.MarriagePlus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

	private final MarriagePlus plugin;

	public ChatListener(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();

		if (!plugin.requestManager().coupleChatToggled().contains(player.getUniqueId())) {
			return;
		}

		event.setCancelled(true);
		plugin.requestManager().sendCoupleChat(player, event.getMessage());
	}
}