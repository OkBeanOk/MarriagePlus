package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.utils.UpdateChecker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class UpdateNotificationListener implements Listener {

	private final MarriagePlus plugin;
	private final UpdateChecker updateChecker;

	public UpdateNotificationListener(MarriagePlus plugin, UpdateChecker updateChecker) {
		this.plugin = plugin;
		this.updateChecker = updateChecker;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		if (!plugin.getConfig().getBoolean("settings.update-checker.notify-admins-on-join", true)) {
			return;
		}

		if (!event.getPlayer().hasPermission("marriageplus.admin")) {
			return;
		}

		if (!updateChecker.isUpdateAvailable()) {
			return;
		}

		event.getPlayer().sendMessage(color("&dMarriagePlus &eupdate available!"));
		event.getPlayer().sendMessage(color("&7Current: &f" + updateChecker.getCurrentVersion()));
		event.getPlayer().sendMessage(color("&7Latest: &a" + updateChecker.getLatestVersion()));
		event.getPlayer().sendMessage(color("&7Download: &f" + updateChecker.getDownloadUrl()));
	}
}