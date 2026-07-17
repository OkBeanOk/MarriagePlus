package com.okbeanok.marriagePlus.utils;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.LoveNote;
import com.okbeanok.marriagePlus.models.Pronouns;
import com.okbeanok.marriagePlus.models.PartnerMail;
import com.okbeanok.marriagePlus.models.Family;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.achievement.AchievementManager;
import com.okbeanok.marriagePlus.services.anniversaries.AnniversaryManager;
import com.okbeanok.marriagePlus.services.backpacks.BackpackManager;
import com.okbeanok.marriagePlus.services.homes.HomeManager;
import com.okbeanok.marriagePlus.services.lovenotes.LoveNoteManager;
import com.okbeanok.marriagePlus.services.mail.MailManager;
import com.okbeanok.marriagePlus.services.profiles.ProfileManager;
import com.okbeanok.marriagePlus.services.pronouns.PronounManager;
import com.okbeanok.marriagePlus.services.quests.QuestManager;
import com.okbeanok.marriagePlus.services.request.RequestManager;
import com.okbeanok.marriagePlus.services.social.SocialManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import com.okbeanok.marriagePlus.services.families.FamilyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class DataManager {

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final HomeManager homeManager;
	private final BackpackManager backpackManager;
	private final PronounManager pronounManager;
	private final SocialManager socialManager;
	private final RequestManager requestManager;
	private final MailManager mailManager;
	private final MarriageXpManager marriageXpManager;
	private final AchievementManager achievementManager;
	private final ProfileManager profileManager;
	private final QuestManager questManager;
	private final AnniversaryManager anniversaryManager;
	private final LoveNoteManager loveNoteManager;
	private final FamilyManager familyManager;

	private File dataFile;
	private FileConfiguration dataConfig;

	private boolean mysqlEnabled;
	private Connection mysqlConnection;
	private String mysqlTableName;

	public DataManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			HomeManager homeManager,
			BackpackManager backpackManager,
			PronounManager pronounManager,
			SocialManager socialManager,
			RequestManager requestManager,
			MailManager mailManager,
			MarriageXpManager marriageXpManager,
			AchievementManager achievementManager,
			ProfileManager profileManager,
			QuestManager questManager,
			AnniversaryManager anniversaryManager,
			LoveNoteManager loveNoteManager,
			FamilyManager familyManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.homeManager = homeManager;
		this.backpackManager = backpackManager;
		this.pronounManager = pronounManager;
		this.socialManager = socialManager;
		this.requestManager = requestManager;
		this.mailManager = mailManager;
		this.marriageXpManager = marriageXpManager;
		this.achievementManager = achievementManager;
		this.profileManager = profileManager;
		this.questManager = questManager;
		this.anniversaryManager = anniversaryManager;
		this.loveNoteManager = loveNoteManager;
		this.familyManager = familyManager;
	}

	public void setupDataFile() {
		dataFile = new File(plugin.getDataFolder(), "data/data.yml");

		if (!dataFile.exists()) {
			try {
				if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
					plugin.getLogger().warning("Could not create plugin data folder.");
				}

				if (dataFile.createNewFile()) {
					dataConfig = YamlConfiguration.loadConfiguration(dataFile);

					dataConfig.set("marriages", new HashMap<>());
					dataConfig.set("marriage-dates", new HashMap<>());
					dataConfig.set("homes", new HashMap<>());
					dataConfig.set("default-homes", new HashMap<>());
					dataConfig.set("backpack-allowed", new ArrayList<>());
					dataConfig.set("pvp-enabled-couples", new ArrayList<>());
					dataConfig.set("backpacks", new HashMap<>());
					dataConfig.set("pronouns", new HashMap<>());
					dataConfig.set("marriage-titles", new HashMap<>());
					dataConfig.set("partner-nicknames", new HashMap<>());
					dataConfig.set("marriage-requests-disabled", new ArrayList<>());
					dataConfig.set("blocked-marriage-requests", new ArrayList<>());
					dataConfig.set("partner-mail", new HashMap<>());
					dataConfig.set("marriage-xp", new HashMap<>());
					dataConfig.set("partner-statuses", new HashMap<>());
					dataConfig.set("couple-quest-progress", new HashMap<>());
					dataConfig.set("couple-quest-reset-dates", new HashMap<>());
					dataConfig.set("anniversary-claimed-milestones", new HashMap<>());
					dataConfig.set("love-notes", new HashMap<String, Object>());
					dataConfig.set("families", new HashMap<String, Object>());
					dataConfig.set("player-families", new HashMap<String, Object>());

					saveYamlDataFile();
				}
			} catch (IOException exception) {
				plugin.getLogger().severe("Could not create data.yml: " + exception.getMessage());
			}
		}

		dataConfig = YamlConfiguration.loadConfiguration(dataFile);

		mysqlEnabled = plugin.configs().storage().getString("type", "YAML").equalsIgnoreCase("MYSQL");

		if (mysqlEnabled) {
			setupMysql();

			if (mysqlConnection != null) {
				loadMysqlDataIntoConfig();
			} else {
				mysqlEnabled = false;
				plugin.getLogger().warning("MySQL storage failed to initialize. Falling back to YAML storage.");
			}
		}
	}

	private void loadAchievements() {
		ConfigurationSection achievementsSection = dataConfig.getConfigurationSection("couple-achievements");

		if (achievementsSection == null) {
			return;
		}

		for (String storedCoupleKey : achievementsSection.getKeys(false)) {
			String coupleKey = decodeAchievementCoupleKey(storedCoupleKey);
			List<String> achievements = achievementsSection.getStringList(storedCoupleKey);

			if (!achievements.isEmpty()) {
				achievementManager.unlockedAchievements().put(coupleKey, new ArrayList<>(achievements));
			}
		}
	}

	private void loadPartnerStatuses() {
		ConfigurationSection statusSection = dataConfig.getConfigurationSection("partner-statuses");

		if (statusSection == null) {
			return;
		}

		for (String uuidText : statusSection.getKeys(false)) {
			try {
				UUID playerId = UUID.fromString(uuidText);
				String status = statusSection.getString(uuidText, "");

				if (!status.isBlank()) {
					profileManager.partnerStatuses().put(playerId, status);
				}
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid partner status UUID in data.yml: " + uuidText);
			}
		}
	}

	private void savePartnerStatuses() {
		dataConfig.set("partner-statuses", new HashMap<>());

		for (Map.Entry<UUID, String> entry : profileManager.partnerStatuses().entrySet()) {
			dataConfig.set("partner-statuses." + entry.getKey(), entry.getValue());
		}
	}

	public void saveDataFile() {
		if (mysqlEnabled) {
			saveMysqlDataFile();
			return;
		}

		saveYamlDataFile();
	}
	private void setupMysql() {
		String host = plugin.configs().storage().getString("mysql.host", "localhost");
		int port = plugin.configs().storage().getInt("mysql.port", 3306);
		String database = plugin.configs().storage().getString("mysql.database", "marriageplus");
		String username = plugin.configs().storage().getString("mysql.username", "root");
		String password = plugin.configs().storage().getString("mysql.password", "");
		boolean useSsl = plugin.configs().storage().getBoolean("mysql.use-ssl", false);
		String tablePrefix = plugin.configs().storage().getString("mysql.table-prefix", "marriageplus_");

		mysqlTableName = tablePrefix + "data";

		String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
				+ "?useSSL=" + useSsl
				+ "&allowPublicKeyRetrieval=true"
				+ "&characterEncoding=utf8"
				+ "&useUnicode=true";

		try {
			mysqlConnection = DriverManager.getConnection(jdbcUrl, username, password);
			createMysqlTables();

			plugin.getLogger().info("Connected to MySQL storage.");
		} catch (SQLException exception) {
			plugin.getLogger().severe("Could not connect to MySQL: " + exception.getMessage());
			mysqlConnection = null;
		}
	}

	private void createMysqlTables() throws SQLException {
		String sql = """
				CREATE TABLE IF NOT EXISTS `%s` (
					`data_key` VARCHAR(64) NOT NULL PRIMARY KEY,
					`data_value` LONGTEXT NOT NULL,
					`updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
				""".formatted(mysqlTableName);

		try (PreparedStatement statement = mysqlConnection.prepareStatement(sql)) {
			statement.executeUpdate();
		}
	}

	private void loadMysqlDataIntoConfig() {
		String sql = "SELECT data_value FROM `" + mysqlTableName + "` WHERE data_key = ?";

		try (PreparedStatement statement = mysqlConnection.prepareStatement(sql)) {
			statement.setString(1, "data");

			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					saveMysqlDataFile();
					plugin.getLogger().info("Created initial MySQL data storage.");
					return;
				}

				String rawYaml = resultSet.getString("data_value");

				if (rawYaml == null || rawYaml.isBlank()) {
					saveMysqlDataFile();
					return;
				}

				YamlConfiguration mysqlConfig = new YamlConfiguration();
				mysqlConfig.loadFromString(rawYaml);
				dataConfig = mysqlConfig;

				plugin.getLogger().info("Loaded MarriagePlus data from MySQL.");
			}
		} catch (SQLException | InvalidConfigurationException exception) {
			plugin.getLogger().severe("Could not load MySQL data: " + exception.getMessage());
		}
	}

	private void saveMysqlDataFile() {
		if (mysqlConnection == null) {
			saveYamlDataFile();
			return;
		}

		String sql = "INSERT INTO `" + mysqlTableName + "` (data_key, data_value) VALUES (?, ?) "
				+ "ON DUPLICATE KEY UPDATE data_value = VALUES(data_value)";

		try (PreparedStatement statement = mysqlConnection.prepareStatement(sql)) {
			statement.setString(1, "data");
			statement.setString(2, dataConfig.saveToString());
			statement.executeUpdate();
		} catch (SQLException exception) {
			plugin.getLogger().severe("Could not save MySQL data: " + exception.getMessage());
		}
	}

	public void closeMysql() {
		if (mysqlConnection == null) {
			return;
		}

		try {
			mysqlConnection.close();
			plugin.getLogger().info("Closed MySQL connection.");
		} catch (SQLException exception) {
			plugin.getLogger().warning("Could not close MySQL connection: " + exception.getMessage());
		}
	}

	private void saveYamlDataFile() {
		try {
			dataConfig.save(dataFile);
		} catch (IOException exception) {
			plugin.getLogger().severe("Could not save data.yml: " + exception.getMessage());
		}
	}

	public FileConfiguration dataConfig() {
		return dataConfig;
	}

	public void saveData() {
		dataConfig.set("marriages", null);
		dataConfig.set("homes", null);
		dataConfig.set("default-homes", null);
		dataConfig.set("backpack-allowed", null);
		dataConfig.set("pvp-enabled-couples", null);
		dataConfig.set("pronouns", null);
		dataConfig.set("marriage-titles", null);
		dataConfig.set("partner-nicknames", null);
		dataConfig.set("marriage-requests-disabled", null);
		dataConfig.set("blocked-marriage-requests", null);
		dataConfig.set("chat-colors", null);
		dataConfig.set("chat-prefix-disabled", null);
		dataConfig.set("partner-mail", null);
		dataConfig.set("marriage-xp", null);
		dataConfig.set("couple-achievements", null);
		dataConfig.set("couple-quest-progress", null);
		dataConfig.set("couple-quest-reset-dates", null);
		dataConfig.set("anniversary-claimed-milestones", null);
		dataConfig.set("love-notes", null);
		dataConfig.set("families", null);
		dataConfig.set("player-families", null);

		saveMarriages();
		saveHomes();
		saveDefaultHomes();
		saveBackpackAllowed();
		savePvpEnabledCouples();
		savePronouns();
		saveMarriageTitles();
		savePartnerNicknames();
		saveRequestSettings();
		savePartnerMail();
		saveMarriageXp();
		saveBackpacks();
		saveAchievements();
		savePartnerStatuses();
		saveQuestData();
		saveAnniversaryData();
		saveLoveNotes();
		saveFamilies();

		saveDataFile();
	}
	private void saveLoveNotes() {
		dataConfig.set("love-notes", new HashMap<String, Object>());

		for (Map.Entry<UUID, List<LoveNote>> inboxEntry : loveNoteManager.notes().entrySet()) {
			for (int index = 0; index < inboxEntry.getValue().size(); index++) {
				LoveNote note = inboxEntry.getValue().get(index);
				String path = "love-notes." + inboxEntry.getKey() + "." + index;

				dataConfig.set(path + ".sender-id", note.senderId().toString());
				dataConfig.set(path + ".sender-name", note.senderName());
				dataConfig.set(path + ".message", note.message());
				dataConfig.set(path + ".sent-at", note.sentAt());
				dataConfig.set(path + ".unread", note.unread());
			}
		}
	}

	private void loadLoveNotes() {
		ConfigurationSection notesSection = dataConfig.getConfigurationSection("love-notes");

		if (notesSection == null) {
			return;
		}

		for (String receiverKey : notesSection.getKeys(false)) {
			try {
				UUID receiverId = UUID.fromString(receiverKey);
				ConfigurationSection receiverSection = dataConfig.getConfigurationSection("love-notes." + receiverKey);

				if (receiverSection == null) {
					continue;
				}

				List<LoveNote> inbox = new ArrayList<>();

				for (String noteKey : receiverSection.getKeys(false)) {
					String path = "love-notes." + receiverKey + "." + noteKey;

					try {
						UUID senderId = UUID.fromString(dataConfig.getString(path + ".sender-id", ""));
						String senderName = dataConfig.getString(path + ".sender-name", "Unknown");
						String message = dataConfig.getString(path + ".message", "");
						long sentAt = dataConfig.getLong(path + ".sent-at", System.currentTimeMillis());
						boolean unread = dataConfig.getBoolean(path + ".unread", true);

						if (!message.isBlank()) {
							inbox.add(new LoveNote(senderId, senderName, message, sentAt, unread));
						}
					} catch (IllegalArgumentException exception) {
						plugin.getLogger().warning("Invalid love note entry in data.yml: " + path);
					}
				}

				if (!inbox.isEmpty()) {
					loveNoteManager.notes().put(receiverId, inbox);
				}
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid love note receiver UUID in data.yml: " + receiverKey);
			}
		}
	}

	private void saveMarriages() {
		Map<String, Boolean> savedCouples = new HashMap<>();

		for (Map.Entry<UUID, UUID> entry : marriageManager.partners().entrySet()) {
			String key = marriageManager.coupleKey(entry.getKey(), entry.getValue());

			if (!savedCouples.containsKey(key)) {
				dataConfig.set("marriages." + entry.getKey(), entry.getValue().toString());
				savedCouples.put(key, true);
			}
		}
	}

	private void saveHomes() {
		for (Map.Entry<UUID, Map<String, Location>> playerEntry : homeManager.homes().entrySet()) {
			for (Map.Entry<String, Location> homeEntry : playerEntry.getValue().entrySet()) {
				saveLocation("homes." + playerEntry.getKey() + "." + homeEntry.getKey(), homeEntry.getValue());
			}
		}
	}

	private void saveBackpackAllowed() {
		dataConfig.set("backpack-allowed", backpackManager.backpackAllowed().stream()
				.map(UUID::toString)
				.toList());
	}

	private void savePvpEnabledCouples() {
		dataConfig.set("pvp-enabled-couples", marriageManager.pvpEnabledCouples().stream().toList());
	}

	private void savePronouns() {
		for (Map.Entry<UUID, Pronouns> entry : pronounManager.pronouns().entrySet()) {
			String path = "pronouns." + entry.getKey();
			Pronouns savedPronouns = entry.getValue();

			dataConfig.set(path + ".subject", savedPronouns.subject());
			dataConfig.set(path + ".object", savedPronouns.object());
			dataConfig.set(path + ".possessive", savedPronouns.possessive());
			dataConfig.set(path + ".display", savedPronouns.display());
		}
	}

	private void saveMarriageTitles() {
		for (Map.Entry<UUID, String> entry : socialManager.marriageTitles().entrySet()) {
			dataConfig.set("marriage-titles." + entry.getKey(), entry.getValue());
		}
	}

	private void savePartnerNicknames() {
		for (Map.Entry<UUID, String> entry : socialManager.partnerNicknames().entrySet()) {
			dataConfig.set("partner-nicknames." + entry.getKey(), entry.getValue());
		}
	}

	private void saveRequestSettings() {
		dataConfig.set("marriage-requests-disabled", requestManager.marriageRequestsDisabled().stream()
				.map(UUID::toString)
				.toList());

		dataConfig.set("blocked-marriage-requests", requestManager.blockedMarriageRequests().stream().toList());

		for (Map.Entry<UUID, String> entry : requestManager.chatColors().entrySet()) {
			dataConfig.set("chat-colors." + entry.getKey(), entry.getValue());
		}

		dataConfig.set("chat-prefix-disabled", requestManager.chatPrefixDisabled().stream()
				.map(UUID::toString)
				.toList());
	}

	private void savePartnerMail() {
		for (Map.Entry<UUID, java.util.List<PartnerMail>> inboxEntry : mailManager.inboxes().entrySet()) {
			for (int index = 0; index < inboxEntry.getValue().size(); index++) {
				PartnerMail mail = inboxEntry.getValue().get(index);
				String path = "partner-mail." + inboxEntry.getKey() + "." + index;

				dataConfig.set(path + ".sender-id", mail.senderId().toString());
				dataConfig.set(path + ".sender-name", mail.senderName());
				dataConfig.set(path + ".message", mail.message());
				dataConfig.set(path + ".sent-at", mail.sentAt());
			}
		}
	}

	private void saveMarriageXp() {
		for (Map.Entry<String, Integer> entry : marriageXpManager.coupleXp().entrySet()) {
			dataConfig.set("marriage-xp." + entry.getKey(), entry.getValue());
		}
	}

	private void saveBackpacks() {
		for (Map.Entry<UUID, Inventory> entry : backpackManager.backpacks().entrySet()) {
			saveBackpack(entry.getKey(), entry.getValue());
		}
	}

	public void loadData() {
		marriageManager.partners().clear();
		homeManager.homes().clear();
		homeManager.defaultHomes().clear();
		backpackManager.backpackAllowed().clear();
		marriageManager.pvpEnabledCouples().clear();
		pronounManager.pronouns().clear();
		socialManager.marriageTitles().clear();
		socialManager.partnerNicknames().clear();
		requestManager.marriageRequestsDisabled().clear();
		requestManager.blockedMarriageRequests().clear();
		requestManager.chatColors().clear();
		requestManager.chatPrefixDisabled().clear();
		mailManager.inboxes().clear();
		marriageXpManager.coupleXp().clear();
		achievementManager.unlockedAchievements().clear();
		backpackManager.backpacks().clear();
		profileManager.partnerStatuses().clear();
		questManager.questProgress().clear();
		questManager.questResetDates().clear();
		anniversaryManager.claimedMilestones().clear();
		loveNoteManager.notes().clear();
		familyManager.families().clear();
		familyManager.playerFamilies().clear();


		loadMarriages();
		loadHomes();
		loadDefaultHomes();
		loadBackpackAllowed();
		loadPvpEnabledCouples();
		loadPronouns();
		loadMarriageTitles();
		loadPartnerNicknames();
		loadRequestSettings();
		loadPartnerMail();
		loadMarriageXp();
		loadAchievements();
		loadBackpacks();
		loadPartnerStatuses();
		loadQuestData();
		loadAnniversaryData();
		loadLoveNotes();
		loadFamilies();
	}

	private void loadMarriages() {
		ConfigurationSection marriagesSection = dataConfig.getConfigurationSection("marriages");

		if (marriagesSection == null) {
			return;
		}

		for (String key : marriagesSection.getKeys(false)) {
			try {
				UUID first = UUID.fromString(key);
				UUID second = UUID.fromString(marriagesSection.getString(key));

				marriageManager.partners().put(first, second);
				marriageManager.partners().put(second, first);
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid marriage entry in data.yml: " + key);
			}
		}
	}

	private void saveAnniversaryData() {
		dataConfig.set("anniversary-claimed-milestones", new HashMap<>());

		for (Map.Entry<String, Set<Integer>> entry : anniversaryManager.claimedMilestones().entrySet()) {
			dataConfig.set("anniversary-claimed-milestones." + entry.getKey(), entry.getValue().stream()
					.sorted()
					.toList());
		}
	}

	private void loadDefaultHomes() {
		ConfigurationSection defaultHomesSection = dataConfig.getConfigurationSection("default-homes");

		if (defaultHomesSection == null) {
			return;
		}

		for (String playerKey : defaultHomesSection.getKeys(false)) {
			try {
				UUID playerId = UUID.fromString(playerKey);
				String homeName = defaultHomesSection.getString(playerKey, HomeManager.DEFAULT_HOME_NAME);

				if (homeName != null && !homeName.isBlank()) {
					homeManager.setDefaultHomeFor(playerId, homeName);
				}
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid default home entry in data.yml: " + playerKey);
			}
		}
	}

	private void saveDefaultHomes() {
		dataConfig.set("default-homes", new HashMap<String, Object>());

		for (Map.Entry<UUID, String> entry : homeManager.defaultHomes().entrySet()) {
			dataConfig.set("default-homes." + entry.getKey(), entry.getValue());
		}
	}

	private void loadAnniversaryData() {
		ConfigurationSection anniversarySection = dataConfig.getConfigurationSection("anniversary-claimed-milestones");

		if (anniversarySection == null) {
			return;
		}

		for (String coupleKey : anniversarySection.getKeys(false)) {
			anniversaryManager.claimedMilestones().put(
					coupleKey,
					new HashSet<>(dataConfig.getIntegerList("anniversary-claimed-milestones." + coupleKey))
			);
		}
	}

	private void saveQuestData() {
		dataConfig.set("couple-quest-progress", new HashMap<>());
		dataConfig.set("couple-quest-reset-dates", new HashMap<>());

		for (Map.Entry<String, Map<String, Integer>> coupleEntry : questManager.questProgress().entrySet()) {
			for (Map.Entry<String, Integer> questEntry : coupleEntry.getValue().entrySet()) {
				dataConfig.set("couple-quest-progress." + coupleEntry.getKey() + "." + questEntry.getKey(), questEntry.getValue());
			}
		}

		for (Map.Entry<String, String> entry : questManager.questResetDates().entrySet()) {
			dataConfig.set("couple-quest-reset-dates." + entry.getKey(), entry.getValue());
		}
	}

	private void loadQuestData() {
		ConfigurationSection progressSection = dataConfig.getConfigurationSection("couple-quest-progress");

		if (progressSection != null) {
			for (String coupleKey : progressSection.getKeys(false)) {
				ConfigurationSection coupleSection = dataConfig.getConfigurationSection("couple-quest-progress." + coupleKey);

				if (coupleSection == null) {
					continue;
				}

				Map<String, Integer> progress = new HashMap<>();

				for (String questKey : coupleSection.getKeys(false)) {
					progress.put(questKey, coupleSection.getInt(questKey, 0));
				}

				questManager.questProgress().put(coupleKey, progress);
			}
		}

		ConfigurationSection resetSection = dataConfig.getConfigurationSection("couple-quest-reset-dates");

		if (resetSection == null) {
			return;
		}

		for (String coupleKey : resetSection.getKeys(false)) {
			questManager.questResetDates().put(coupleKey, resetSection.getString(coupleKey, ""));
		}
	}

	private void loadHomes() {
		ConfigurationSection homesSection = dataConfig.getConfigurationSection("homes");

		if (homesSection == null) {
			return;
		}

		for (String playerKey : homesSection.getKeys(false)) {
			try {
				UUID playerId = UUID.fromString(playerKey);
				ConfigurationSection playerHomesSection = dataConfig.getConfigurationSection("homes." + playerKey);

				if (playerHomesSection == null) {
					continue;
				}

				if (playerHomesSection.contains("world")) {
					Location oldHome = loadLocation("homes." + playerKey);

					if (oldHome != null) {
						homeManager.setHomeFor(playerId, HomeManager.DEFAULT_HOME_NAME, oldHome);
					}

					continue;
				}

				for (String homeName : playerHomesSection.getKeys(false)) {
					Location location = loadLocation("homes." + playerKey + "." + homeName);

					if (location != null) {
						homeManager.setHomeFor(playerId, homeName, location);
					}
				}
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid home entry in data.yml: " + playerKey);
			}
		}
	}

	private void loadBackpackAllowed() {
		for (String uuid : dataConfig.getStringList("backpack-allowed")) {
			try {
				backpackManager.backpackAllowed().add(UUID.fromString(uuid));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid backpack-allowed UUID in data.yml: " + uuid);
			}
		}
	}

	private void loadPvpEnabledCouples() {
		marriageManager.pvpEnabledCouples().addAll(dataConfig.getStringList("pvp-enabled-couples"));
	}

	private void loadPronouns() {
		ConfigurationSection pronounsSection = dataConfig.getConfigurationSection("pronouns");

		if (pronounsSection == null) {
			return;
		}

		for (String key : pronounsSection.getKeys(false)) {
			try {
				String path = "pronouns." + key;

				pronounManager.pronouns().put(UUID.fromString(key), new Pronouns(
						dataConfig.getString(path + ".subject", "they"),
						dataConfig.getString(path + ".object", "them"),
						dataConfig.getString(path + ".possessive", "their"),
						dataConfig.getString(path + ".display", "they/them")
				));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid pronouns entry in data.yml: " + key);
			}
		}
	}

	private void loadMarriageTitles() {
		ConfigurationSection titlesSection = dataConfig.getConfigurationSection("marriage-titles");

		if (titlesSection == null) {
			return;
		}

		for (String key : titlesSection.getKeys(false)) {
			try {
				socialManager.marriageTitles().put(UUID.fromString(key), titlesSection.getString(key, ""));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid marriage title entry in data.yml: " + key);
			}
		}
	}

	private void loadPartnerNicknames() {
		ConfigurationSection nicknamesSection = dataConfig.getConfigurationSection("partner-nicknames");

		if (nicknamesSection == null) {
			return;
		}

		for (String key : nicknamesSection.getKeys(false)) {
			try {
				socialManager.partnerNicknames().put(UUID.fromString(key), nicknamesSection.getString(key, ""));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid partner nickname entry in data.yml: " + key);
			}
		}
	}

	private void loadRequestSettings() {
		for (String uuid : dataConfig.getStringList("marriage-requests-disabled")) {
			try {
				requestManager.marriageRequestsDisabled().add(UUID.fromString(uuid));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid marriage-requests-disabled UUID in data.yml: " + uuid);
			}
		}

		requestManager.blockedMarriageRequests().addAll(dataConfig.getStringList("blocked-marriage-requests"));

		ConfigurationSection chatColorsSection = dataConfig.getConfigurationSection("chat-colors");

		if (chatColorsSection != null) {
			for (String key : chatColorsSection.getKeys(false)) {
				try {
					requestManager.chatColors().put(UUID.fromString(key), chatColorsSection.getString(key, "&f"));
				} catch (IllegalArgumentException exception) {
					plugin.getLogger().warning("Invalid chat color UUID in data.yml: " + key);
				}
			}
		}

		for (String uuid : dataConfig.getStringList("chat-prefix-disabled")) {
			try {
				requestManager.chatPrefixDisabled().add(UUID.fromString(uuid));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid chat-prefix-disabled UUID in data.yml: " + uuid);
			}
		}
	}

	private void loadPartnerMail() {
		ConfigurationSection mailSection = dataConfig.getConfigurationSection("partner-mail");

		if (mailSection == null) {
			return;
		}

		for (String receiverKey : mailSection.getKeys(false)) {
			try {
				UUID receiverId = UUID.fromString(receiverKey);
				ConfigurationSection inboxSection = dataConfig.getConfigurationSection("partner-mail." + receiverKey);

				if (inboxSection == null) {
					continue;
				}

				List<PartnerMail> inbox = new ArrayList<>();

				for (String mailKey : inboxSection.getKeys(false)) {
					String path = "partner-mail." + receiverKey + "." + mailKey;

					try {
						UUID senderId = UUID.fromString(dataConfig.getString(path + ".sender-id", ""));
						String senderName = dataConfig.getString(path + ".sender-name", "Unknown");
						String message = dataConfig.getString(path + ".message", "");
						long sentAt = dataConfig.getLong(path + ".sent-at", System.currentTimeMillis());

						if (!message.isBlank()) {
							inbox.add(new PartnerMail(senderId, senderName, message, sentAt));
						}
					} catch (IllegalArgumentException exception) {
						plugin.getLogger().warning("Invalid partner mail entry in data.yml: " + path);
					}
				}

				if (!inbox.isEmpty()) {
					mailManager.inboxes().put(receiverId, inbox);
				}
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid partner mail receiver UUID in data.yml: " + receiverKey);
			}
		}
	}

	private void loadMarriageXp() {
		ConfigurationSection xpSection = dataConfig.getConfigurationSection("marriage-xp");

		if (xpSection == null) {
			return;
		}

		for (String coupleKey : xpSection.getKeys(false)) {
			marriageXpManager.coupleXp().put(coupleKey, xpSection.getInt(coupleKey, 0));
		}
	}

	private void loadBackpacks() {
		ConfigurationSection backpacksSection = dataConfig.getConfigurationSection("backpacks");

		if (backpacksSection == null) {
			return;
		}

		for (String key : backpacksSection.getKeys(false)) {
			try {
				UUID owner = UUID.fromString(key);
				backpackManager.backpacks().put(owner, backpackManager.loadBackpack(owner));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid backpack entry in data.yml: " + key);
			}
		}
	}

	private void saveLocation(String path, Location location) {
		if (location.getWorld() == null) {
			return;
		}

		dataConfig.set(path + ".world", location.getWorld().getName());
		dataConfig.set(path + ".x", location.getX());
		dataConfig.set(path + ".y", location.getY());
		dataConfig.set(path + ".z", location.getZ());
		dataConfig.set(path + ".yaw", location.getYaw());
		dataConfig.set(path + ".pitch", location.getPitch());
	}

	private Location loadLocation(String path) {
		String worldName = dataConfig.getString(path + ".world");

		if (worldName == null) {
			return null;
		}

		World world = Bukkit.getWorld(worldName);

		if (world == null) {
			return null;
		}

		return new Location(
				world,
				dataConfig.getDouble(path + ".x"),
				dataConfig.getDouble(path + ".y"),
				dataConfig.getDouble(path + ".z"),
				(float) dataConfig.getDouble(path + ".yaw"),
				(float) dataConfig.getDouble(path + ".pitch")
		);
	}

	public void saveBackpack(UUID owner, Inventory inventory) {
		dataConfig.set("backpacks." + owner + ".contents", Arrays.asList(inventory.getContents()));
		saveDataFile();
	}

	private void saveAchievements() {
		dataConfig.set("couple-achievements", new HashMap<String, Object>());

		for (Map.Entry<String, List<String>> entry : achievementManager.unlockedAchievements().entrySet()) {
			String storedCoupleKey = encodeAchievementCoupleKey(entry.getKey());
			dataConfig.set("couple-achievements." + storedCoupleKey, new ArrayList<>(entry.getValue()));
		}
	}

	private String encodeAchievementCoupleKey(String coupleKey) {
		return coupleKey.replace(":", "__");
	}

	private String decodeAchievementCoupleKey(String storedCoupleKey) {
		return storedCoupleKey.replace("__", ":");
	}

	public Inventory loadBackpack(UUID owner) {
		return backpackManager.loadBackpack(owner);
	}


	public ItemStack[] loadBackpackContents(UUID owner) {
		java.util.List<?> contents = dataConfig.getList("backpacks." + owner + ".contents");
		int size = plugin.configs().backpack().getInt("size", 27);
		size = Math.max(9, Math.min(54, size));

		if (size % 9 != 0) {
			size = (size / 9) * 9;
		}

		size = Math.max(9, size);

		if (contents != null && contents.size() > size) {
			size = Math.min(54, ((contents.size() + 8) / 9) * 9);
		}

		ItemStack[] items = new ItemStack[size];

		if (contents == null) {
			return items;
		}

		for (int index = 0; index < Math.min(contents.size(), items.length); index++) {
			Object object = contents.get(index);

			if (object instanceof ItemStack itemStack) {
				items[index] = itemStack;
			}
		}

		return items;
	}


	private void saveFamilies() {
		dataConfig.set("families", new HashMap<String, Object>());
		dataConfig.set("player-families", new HashMap<String, Object>());

		for (Map.Entry<String, Family> entry : familyManager.families().entrySet()) {
			Family family = entry.getValue();
			String path = "families." + entry.getKey();

			dataConfig.set(path + ".parent-one", family.parentOne().toString());
			dataConfig.set(path + ".parent-two", family.parentTwo().toString());
			dataConfig.set(path + ".name", family.name());
			dataConfig.set(path + ".members", family.members().stream().map(UUID::toString).toList());
			dataConfig.set(path + ".adopted-children", family.adoptedChildren().stream().map(UUID::toString).toList());
			dataConfig.set(path + ".former-members", family.formerMembers().stream().map(UUID::toString).toList());
			dataConfig.set(path + ".created-at", family.createdAt());

			for (Map.Entry<UUID, Set<UUID>> childEntry : family.childParents().entrySet()) {
				dataConfig.set(
						path + ".child-parents." + childEntry.getKey(),
						childEntry.getValue().stream().map(UUID::toString).toList()
				);
			}
		}

		for (Map.Entry<UUID, String> entry : familyManager.playerFamilies().entrySet()) {
			dataConfig.set("player-families." + entry.getKey(), entry.getValue());
		}
	}

	private void loadFamilies() {
		ConfigurationSection familiesSection = dataConfig.getConfigurationSection("families");

		if (familiesSection != null) {
			for (String familyId : familiesSection.getKeys(false)) {
				String path = "families." + familyId;

				try {
					UUID parentOne = UUID.fromString(dataConfig.getString(path + ".parent-one", ""));
					UUID parentTwo = UUID.fromString(dataConfig.getString(path + ".parent-two", ""));
					String name = dataConfig.getString(path + ".name", "Family");
					Set<UUID> members = new HashSet<>();
					Set<UUID> adoptedChildren = new HashSet<>();
					Set<UUID> formerMembers = new HashSet<>();
					Map<UUID, Set<UUID>> childParents = new HashMap<>();

					for (String member : dataConfig.getStringList(path + ".members")) {
						members.add(UUID.fromString(member));
					}

					for (String child : dataConfig.getStringList(path + ".adopted-children")) {
						adoptedChildren.add(UUID.fromString(child));
					}

					for (String formerMember : dataConfig.getStringList(path + ".former-members")) {
						formerMembers.add(UUID.fromString(formerMember));
					}

					ConfigurationSection childParentsSection = dataConfig.getConfigurationSection(path + ".child-parents");

					if (childParentsSection != null) {
						for (String childKey : childParentsSection.getKeys(false)) {
							UUID childId = UUID.fromString(childKey);
							Set<UUID> parents = new HashSet<>();

							for (String parentId : dataConfig.getStringList(path + ".child-parents." + childKey)) {
								parents.add(UUID.fromString(parentId));
							}

							childParents.put(childId, parents);
						}
					}

					long createdAt = dataConfig.getLong(path + ".created-at", System.currentTimeMillis());

					familyManager.families().put(familyId, new Family(
							familyId,
							parentOne,
							parentTwo,
							name,
							members,
							adoptedChildren,
							formerMembers,
							childParents,
							createdAt
					));
				} catch (IllegalArgumentException exception) {
					plugin.getLogger().warning("Invalid family entry in data.yml: " + familyId);
				}
			}
		}

		ConfigurationSection lookupSection = dataConfig.getConfigurationSection("player-families");

		if (lookupSection == null) {
			return;
		}

		for (String playerId : lookupSection.getKeys(false)) {
			try {
				familyManager.playerFamilies().put(
						UUID.fromString(playerId),
						lookupSection.getString(playerId, "")
				);
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid player family lookup in data.yml: " + playerId);
			}
		}
	}
}