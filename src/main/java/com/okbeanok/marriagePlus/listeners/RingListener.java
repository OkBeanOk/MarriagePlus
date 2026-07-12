package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.rings.RingManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Locale;

public class RingListener implements Listener {

	private final MarriagePlus plugin;
	private final RingManager ringManager;

	public RingListener(MarriagePlus plugin, RingManager ringManager) {
		this.plugin = plugin;
		this.ringManager = ringManager;
	}

	@EventHandler
	public void onRingUse(PlayerInteractEvent event) {
		if (!event.hasItem()) {
			return;
		}

		if (!ringManager.isWeddingRing(event.getItem())) {
			return;
		}

		if (!plugin.configs().rings().getBoolean("right-click.enabled", true)) {
			return;
		}

		String action = plugin.configs().rings().getString("right-click.action", "PROFILE").toUpperCase(Locale.ROOT);

		switch (action) {
			case "PROFILE" -> {
				event.setCancelled(true);
				plugin.profileManager().profileCommand(event.getPlayer(), new String[]{"profile"});
			}
			case "MENU" -> {
				event.setCancelled(true);
				plugin.marriageGuiManager().openMainMenu(event.getPlayer());
			}
			case "NONE" -> {
			}
			default -> plugin.getLogger().warning("Invalid rings.yml right-click.action: " + action);
		}
	}
}