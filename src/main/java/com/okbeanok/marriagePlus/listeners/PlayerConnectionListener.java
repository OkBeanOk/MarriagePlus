package com.okbeanok.marriagePlus.listeners;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.lovenotes.LoveNoteManager;
import com.okbeanok.marriagePlus.services.mail.MailManager;
import com.okbeanok.marriagePlus.services.social.SocialManager;
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
	private final MailManager mailManager;
	private final LoveNoteManager loveNoteManager;

	public PlayerConnectionListener(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			SocialManager socialManager,
			MailManager mailManager,
			LoveNoteManager loveNoteManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.socialManager = socialManager;
		this.mailManager = mailManager;
		this.loveNoteManager = loveNoteManager;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		mailManager.notifyUnreadMail(player);
		plugin.achievementManager().checkAnniversaryAchievements(player);

		if (!plugin.getConfig().getBoolean("settings.partner-join-notifications", true)) {
			return;
		}

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

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (!player.isOnline()) {
				return;
			}

			mailManager.notifyUnreadMail(player);
			loveNoteManager.notifyUnreadNotes(player);
		}, 40L);
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