package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.managers.MarriageManager;
import com.okbeanok.marriagePlus.managers.SocialManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class PlayerConnectionListener implements Listener {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final SocialManager socialManager;

	public PlayerConnectionListener(MarriagePlus plugin, MarriageManager marriageManager, SocialManager socialManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.socialManager = socialManager;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		if (!plugin.getConfig().getBoolean("settings.partner-join-notifications", true)) {
			return;
		}

		Player player = event.getPlayer();
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner != null) {
			partner.sendMessage(color("&d❤ Your partner &f"
					+ socialManager.getPartnerDisplayName(partner.getUniqueId(), player.getUniqueId())
					+ " &djoined the server."));
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (!plugin.getConfig().getBoolean("settings.partner-join-notifications", true)) {
			return;
		}

		Player player = event.getPlayer();
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			return;
		}

		Player partner = Bukkit.getPlayer(partnerId);

		if (partner != null) {
			partner.sendMessage(color("&d❤ Your partner &f"
					+ socialManager.getPartnerDisplayName(partner.getUniqueId(), player.getUniqueId())
					+ " &dleft the server."));
		}
	}
}