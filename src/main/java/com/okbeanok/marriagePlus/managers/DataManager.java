package com.okbeanok.marriagePlus.managers;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.Pronouns;
import com.okbeanok.marriagePlus.models.PartnerMail;
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
			AchievementManager achievementManager
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
	}

	public void setupDataFile() {
		dataFile = new File(plugin.getDataFolder(), "data.yml");

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

					saveYamlDataFile();
				}
			} catch (IOException exception) {
				plugin.getLogger().severe("Could not create data.yml: " + exception.getMessage());
			}
		}

		dataConfig = YamlConfiguration.loadConfiguration(dataFile);

		mysqlEnabled = plugin.getConfig().getString("storage.type", "YAML").equalsIgnoreCase("MYSQL");

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

		for (String coupleKey : achievementsSection.getKeys(false)) {
			achievementManager.unlockedAchievements().put(coupleKey, achievementsSection.getStringList(coupleKey));
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
		String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
		int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
		String database = plugin.getConfig().getString("storage.mysql.database", "marriageplus");
		String username = plugin.getConfig().getString("storage.mysql.username", "root");
		String password = plugin.getConfig().getString("storage.mysql.password", "");
		boolean useSsl = plugin.getConfig().getBoolean("storage.mysql.use-ssl", false);
		String tablePrefix = plugin.getConfig().getString("storage.mysql.table-prefix", "marriageplus_");

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

		saveMarriages();
		saveHomes();
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

		saveDataFile();
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
		backpackManager.backpacks().clear();

		loadMarriages();
		loadHomes();
		loadBackpackAllowed();
		loadPvpEnabledCouples();
		loadPronouns();
		loadMarriageTitles();
		loadPartnerNicknames();
		loadRequestSettings();
		loadPartnerMail();
		loadMarriageXp();
		loadBackpacks();
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
		for (Map.Entry<String, List<String>> entry : achievementManager.unlockedAchievements().entrySet()) {
			dataConfig.set("couple-achievements." + entry.getKey(), entry.getValue());
		}
	}

	public Inventory loadBackpack(UUID owner) {
		return backpackManager.loadBackpack(owner);
	}

	public ItemStack[] loadBackpackContents(UUID owner) {
		ItemStack[] items = new ItemStack[27];

		java.util.List<?> contents = dataConfig.getList("backpacks." + owner + ".contents");

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
}