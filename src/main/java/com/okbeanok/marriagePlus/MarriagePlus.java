package com.okbeanok.marriagePlus;

import com.okbeanok.marriagePlus.commands.FamilyCommand;
import com.okbeanok.marriagePlus.commands.MarryCommand;
import com.okbeanok.marriagePlus.hooks.MarriagePlusExpansion;
import com.okbeanok.marriagePlus.listeners.*;
import com.okbeanok.marriagePlus.services.CooldownManager;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.achievement.AchievementManager;
import com.okbeanok.marriagePlus.services.anniversaries.AnniversaryManager;
import com.okbeanok.marriagePlus.services.backpacks.BackpackManager;
import com.okbeanok.marriagePlus.services.buffs.PartnerBuffManager;
import com.okbeanok.marriagePlus.services.families.FamilyManager;
import com.okbeanok.marriagePlus.services.families.FamilyWebServer;
import com.okbeanok.marriagePlus.services.homes.HomeManager;
import com.okbeanok.marriagePlus.services.mail.MailManager;
import com.okbeanok.marriagePlus.services.notifications.NotificationManager;
import com.okbeanok.marriagePlus.services.profiles.ProfileManager;
import com.okbeanok.marriagePlus.services.pronouns.PronounManager;
import com.okbeanok.marriagePlus.services.quests.QuestManager;
import com.okbeanok.marriagePlus.services.request.RequestManager;
import com.okbeanok.marriagePlus.services.social.SocialManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import com.okbeanok.marriagePlus.services.lovenotes.LoveNoteManager;
import com.okbeanok.marriagePlus.services.rings.RingManager;
import com.okbeanok.marriagePlus.services.gui.MarriageGuiManager;
import com.okbeanok.marriagePlus.services.ceremonies.CeremonyManager;
import com.okbeanok.marriagePlus.services.leaderboards.LeaderboardManager;
import com.okbeanok.marriagePlus.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MarriagePlus extends JavaPlugin {

	private DataManager dataManager;
	private MarriageManager marriageManager;
	private RequestManager requestManager;
	private HomeManager homeManager;
	private BackpackManager backpackManager;
	private PronounManager pronounManager;
	private SocialManager socialManager;
	private CooldownManager cooldownManager;
	private UpdateChecker updateChecker;
	private MailManager mailManager;
	private AchievementManager achievementManager;
	private MarriageXpManager marriageXpManager;
	private LangManager langManager;
	private ProfileManager profileManager;
	private PartnerBuffManager partnerBuffManager;
	private QuestManager questManager;
	private PluginConfigManager pluginConfigManager;
	private AnniversaryManager anniversaryManager;
	private LoveNoteManager loveNoteManager;
	private RingManager ringManager;
	private MarriageGuiManager marriageGuiManager;
	private CeremonyManager ceremonyManager;
	private LeaderboardManager leaderboardManager;
	private NotificationManager notificationManager;
	private FamilyManager familyManager;
	private FamilyWebServer familyWebServer;

	@Override
	public void onEnable() {
		saveDefaultConfig();


		pluginConfigManager = new PluginConfigManager(this);
		pluginConfigManager.setup();

		langManager = new LangManager(this);
		langManager.setupLangFile();

		cooldownManager = new CooldownManager(this);
		marriageManager = new MarriageManager(this);
		requestManager = new RequestManager(this, marriageManager);
		homeManager = new HomeManager(this, marriageManager, cooldownManager);
		backpackManager = new BackpackManager(this, marriageManager, cooldownManager);
		pronounManager = new PronounManager(this);
		socialManager = new SocialManager(this, marriageManager);
		mailManager = new MailManager(this, marriageManager);
		marriageXpManager = new MarriageXpManager(this, marriageManager);

		achievementManager = new AchievementManager(this, marriageManager);
		profileManager = new ProfileManager(this, marriageManager, marriageXpManager, pronounManager, socialManager);
		partnerBuffManager = new PartnerBuffManager(this, marriageManager, marriageXpManager);
		questManager = new QuestManager(this, marriageManager, marriageXpManager);
		anniversaryManager = new AnniversaryManager(this, marriageManager, marriageXpManager);
		loveNoteManager = new LoveNoteManager(this, marriageManager, marriageXpManager);
		ringManager = new RingManager(this, marriageManager, marriageXpManager, profileManager, cooldownManager);
		marriageGuiManager = new MarriageGuiManager(this, marriageManager);
		ceremonyManager = new CeremonyManager(this, marriageManager, marriageXpManager);
		leaderboardManager = new LeaderboardManager(this, marriageManager, marriageXpManager, achievementManager);
		updateChecker = new UpdateChecker(this);
		familyManager = new FamilyManager(this, marriageManager);
		notificationManager = new NotificationManager(this, marriageManager);


		dataManager = new DataManager(
				this,
				marriageManager,
				homeManager,
				backpackManager,
				pronounManager,
				socialManager,
				requestManager,
				mailManager,
				marriageXpManager,
				achievementManager,
				profileManager,
				questManager,
				anniversaryManager,
				loveNoteManager,
				familyManager
		);

		familyWebServer = new FamilyWebServer(this);
		familyWebServer.start();

		dataManager.setupDataFile();
		dataManager.loadData();

		registerCommand();
		registerListeners();
		registerPlaceholderApi();

		partnerBuffManager.start();
		questManager.start();
		updateChecker.checkForUpdates();

		int pluginId = 32416;
		Metrics metrics = new Metrics(this, pluginId);
		Server server = Bukkit.getServer();
		metrics.addCustomChart(
				new Metrics.SimplePie("Server Version", server::getVersion)
		);

		getLogger().info("MarriagePlus enabled!");
	}

	private void registerPlaceholderApi() {
		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
			getLogger().info("PlaceholderAPI not found. PlaceholderAPI support disabled.");
			return;
		}

		new MarriagePlusExpansion(this).register();
		getLogger().info("PlaceholderAPI support enabled.");
	}

	@Override
	public void onDisable() {
		if (familyWebServer != null) {
			familyWebServer.stop();
		}

		if (partnerBuffManager != null) {
			partnerBuffManager.stop();
		}
		if (questManager != null) {
			questManager.stop();
		}

		dataManager.saveData();
		dataManager.closeMysql();
		getLogger().info("MarriagePlus has been disabled!");
	}

	private void registerCommand() {
		PluginCommand marryCommand = getCommand("marry");

		if (marryCommand == null) {
			getLogger().severe("Command /marry is missing from plugin.yml.");
			return;
		}

		MarryCommand executor = new MarryCommand(
				this,
				marriageManager,
				requestManager,
				homeManager,
				backpackManager,
				pronounManager,
				socialManager,
				cooldownManager,
				dataManager,
				mailManager,
				marriageXpManager,
				achievementManager
		);

		marryCommand.setExecutor(executor);
		marryCommand.setTabCompleter(executor);

		PluginCommand familyCommand = getCommand("family");

		if (familyCommand == null) {
			getLogger().severe("Command /family is missing from plugin.yml.");
			return;
		}

		FamilyCommand familyExecutor = new FamilyCommand(this);
		familyCommand.setExecutor(familyExecutor);
		familyCommand.setTabCompleter(familyExecutor);
	}

	private void registerListeners() {
		Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
		Bukkit.getPluginManager().registerEvents(new InventoryListener(backpackManager), this);
		Bukkit.getPluginManager().registerEvents(new LoveNoteInventoryListener(loveNoteManager), this);
		Bukkit.getPluginManager().registerEvents(new MarriageGuiListener(this, marriageGuiManager), this);
		Bukkit.getPluginManager().registerEvents(new PartnerNotificationListener(notificationManager), this);
		Bukkit.getPluginManager().registerEvents(new PartnerPvpListener(marriageManager), this);
		Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this, marriageManager, socialManager, mailManager, loveNoteManager), this);
		Bukkit.getPluginManager().registerEvents(new QuestListener(marriageManager, questManager), this);
		Bukkit.getPluginManager().registerEvents(new RingListener(this, ringManager), this);
		Bukkit.getPluginManager().registerEvents(new UpdateNotificationListener(this, updateChecker), this);
		Bukkit.getPluginManager().registerEvents(new AchievementInventoryListener(this), this);
	}

	public DataManager dataManager() {
		return dataManager;
	}

	public MarriageManager marriageManager() {
		return marriageManager;
	}

	public RequestManager requestManager() {
		return requestManager;
	}

	public HomeManager homeManager() {
		return homeManager;
	}

	public MailManager mailManager() {
		return mailManager;
	}

	public LangManager langManager() {
		return langManager;
	}

	public AchievementManager achievementManager() {
		return achievementManager;
	}
	public MarriageXpManager marriageXpManager() {
		return marriageXpManager;
	}

	public FamilyManager familyManager() {
		return familyManager;
	}

	public BackpackManager backpackManager() {
		return backpackManager;
	}

	public PronounManager pronounManager() {
		return pronounManager;
	}

	public SocialManager socialManager() {
		return socialManager;
	}

	public ProfileManager profileManager() {
		return profileManager;
	}

	public PartnerBuffManager partnerBuffManager() {
		return partnerBuffManager;
	}

	public QuestManager questManager() {
		return questManager;
	}

	public CooldownManager cooldownManager() {
		return cooldownManager;
	}
	public UpdateChecker updateChecker() {
		return updateChecker;
	}

	public PluginConfigManager configs() {
		return pluginConfigManager;
	}

	public AnniversaryManager anniversaryManager() {
		return anniversaryManager;
	}

	public NotificationManager notificationManager() {
		return notificationManager;
	}

	public LoveNoteManager loveNoteManager() {
		return loveNoteManager;
	}

	public RingManager ringManager() {
		return ringManager;
	}
	public MarriageGuiManager marriageGuiManager() {
		return marriageGuiManager;
	}

	public CeremonyManager ceremonyManager() {
		return ceremonyManager;
	}
	public LeaderboardManager leaderboardManager() {
		return leaderboardManager;
	}
}