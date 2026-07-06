package com.okbeanok.marriagePlus;

import com.okbeanok.marriagePlus.commands.MarryCommand;
import com.okbeanok.marriagePlus.hooks.MarriagePlusExpansion;
import com.okbeanok.marriagePlus.listeners.*;
import com.okbeanok.marriagePlus.managers.*;
import com.okbeanok.marriagePlus.utils.Metrics;
import com.okbeanok.marriagePlus.utils.UpdateChecker;
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


	@Override
	public void onEnable() {
		saveDefaultConfig();

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
		updateChecker = new UpdateChecker(this);

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
				achievementManager
		);

		dataManager.setupDataFile();
		dataManager.loadData();

		registerCommand();
		registerListeners();
		registerPlaceholderApi();

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
	}

	private void registerListeners() {
		Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
		Bukkit.getPluginManager().registerEvents(new InventoryListener(backpackManager), this);
		Bukkit.getPluginManager().registerEvents(new PartnerPvpListener(marriageManager), this);
		Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this, marriageManager, socialManager, mailManager), this);
		Bukkit.getPluginManager().registerEvents(new UpdateNotificationListener(this, updateChecker), this);
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


	public BackpackManager backpackManager() {
		return backpackManager;
	}

	public PronounManager pronounManager() {
		return pronounManager;
	}

	public SocialManager socialManager() {
		return socialManager;
	}

	public CooldownManager cooldownManager() {
		return cooldownManager;
	}
	public UpdateChecker updateChecker() {
		return updateChecker;
	}
}