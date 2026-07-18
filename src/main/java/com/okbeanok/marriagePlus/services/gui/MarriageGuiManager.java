package com.okbeanok.marriagePlus.services.gui;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.PartnerMail;
import com.okbeanok.marriagePlus.services.MarriageManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class MarriageGuiManager {

	private static final long MILLIS_PER_DAY = 86_400_000L;
	private static final String HOMES_MENU_TITLE = "&d❤ &8Couple Homes";
	private static final String MAIL_MENU_TITLE = "&d❤ &8Partner Mail";

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;

	public MarriageGuiManager(MarriagePlus plugin, MarriageManager marriageManager) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
	}

	public void openMainMenu(Player player) {
		if (!plugin.configs().gui().getBoolean("enabled", true)) {
			plugin.langManager().send(player, "gui.disabled");
			return;
		}

		String title = color(plugin.configs().gui().getString("main-menu.title", "&d❤ Marriage Menu"));
		int size = normalizeSize(plugin.configs().gui().getInt("main-menu.size", 54));

		Inventory inventory = Bukkit.createInventory(null, size, title);

		if (plugin.configs().gui().getBoolean("main-menu.filler.enabled", true)) {
			fillInventory(inventory);
		}

		ConfigurationSection itemsSection = plugin.configs().gui().getConfigurationSection("items");

		if (itemsSection != null) {
			for (String itemId : itemsSection.getKeys(false)) {
				String path = "items." + itemId;
				int slot = plugin.configs().gui().getInt(path + ".slot", -1);

				if (slot < 0 || slot >= inventory.getSize()) {
					plugin.getLogger().warning("Invalid GUI slot for item '" + itemId + "': " + slot);
					continue;
				}

				inventory.setItem(slot, createMenuItem(player, itemId));
			}
		}

		player.openInventory(inventory);
	}
	public void openMailMenu(Player player) {
		if (!plugin.configs().gui().getBoolean("enabled", true)) {
			plugin.mailManager().mailCommand(player, new String[]{"mail", "inbox"});
			return;
		}

		if (!marriageManager.isMarried(player.getUniqueId())) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		Inventory inventory = Bukkit.createInventory(null, 54, color(MAIL_MENU_TITLE));
		fillInventory(inventory);

		List<PartnerMail> inbox = plugin.mailManager().inboxes().getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (inbox.isEmpty()) {
			inventory.setItem(22, simpleItem(
					Material.BARRIER,
					"&cNo Partner Mail",
					List.of(
							"&7You do not have any partner mail.",
							"",
							"&7Your partner can send mail with",
							"&f/marry mail send <message>&7."
					),
					false
			));
		} else {
			int[] slots = {
					10, 11, 12, 13, 14, 15, 16,
					19, 20, 21, 22, 23, 24, 25,
					28, 29, 30, 31, 32, 33, 34
			};

			int index = 0;

			for (PartnerMail mail : inbox) {
				if (index >= slots.length) {
					break;
				}

				inventory.setItem(slots[index], mailItem(mail, index + 1));
				index++;
			}
		}

		inventory.setItem(45, simpleItem(
				Material.ARROW,
				"&d← &fBack",
				List.of("&7Return to the marriage menu."),
				false
		));

		inventory.setItem(49, simpleItem(
				Material.BARRIER,
				"&c✕ &fClose",
				List.of("&7Close this menu."),
				false
		));

		player.openInventory(inventory);
	}

	public boolean isMailMenuTitle(String title) {
		return title.equals(color(MAIL_MENU_TITLE));
	}

	private ItemStack mailItem(PartnerMail mail, int mailNumber) {
		List<String> lore = new ArrayList<>();
		lore.add("&8Partner mail");
		lore.add("");
		lore.add("&7From: &f" + mail.senderName());
		lore.add("&7Sent: &f" + formatMailTime(mail.sentAt()));
		lore.add("");
		lore.add("&f" + mail.message());
		lore.add("");
		lore.add("&aLeft-click to print in chat.");
		lore.add("&cShift-click to delete.");

		return simpleItem(
				Material.PAPER,
				"&e✉ &fMail #" + mailNumber,
				lore,
				true
		);
	}

	private String formatMailTime(long timestamp) {
		String timestampFormat = plugin.configs().mail().getString("timestamp-format", "yyyy-MM-dd HH:mm");
		SimpleDateFormat dateFormat = new SimpleDateFormat(timestampFormat);

		return dateFormat.format(new Date(timestamp));
	}

	public void openHomesMenu(Player player) {
		if (!plugin.configs().gui().getBoolean("enabled", true)) {
			plugin.homeManager().listHomes(player);
			return;
		}

		if (!marriageManager.isMarried(player.getUniqueId())) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		Inventory inventory = Bukkit.createInventory(null, 54, color(HOMES_MENU_TITLE));
		fillInventory(inventory);

		Map<String, Location> homes = plugin.homeManager().homes().get(player.getUniqueId());

		if (homes == null || homes.isEmpty()) {
			inventory.setItem(22, simpleItem(
					Material.BARRIER,
					"&cNo Couple Homes",
					List.of(
							"&7You do not have any couple homes set.",
							"",
							"&7Use &f/marry sethome <name>",
							"&7to create one."
					),
					false
			));
		} else {
			int[] slots = {
					10, 11, 12, 13, 14, 15, 16,
					19, 20, 21, 22, 23, 24, 25,
					28, 29, 30, 31, 32, 33, 34
			};

			int index = 0;

			for (String homeName : homes.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
				if (index >= slots.length) {
					break;
				}

				Location location = homes.get(homeName);
				inventory.setItem(slots[index++], homeItem(homeName, location));
			}
		}

		inventory.setItem(45, simpleItem(
				Material.ARROW,
				"&d← &fBack",
				List.of("&7Return to the marriage menu."),
				false
		));

		inventory.setItem(49, simpleItem(
				Material.BARRIER,
				"&c✕ &fClose",
				List.of("&7Close this menu."),
				false
		));

		player.openInventory(inventory);
	}

	public boolean isHomesMenuTitle(String title) {
		return title.equals(color(HOMES_MENU_TITLE));
	}

	private ItemStack homeItem(String homeName, Location location) {
		List<String> lore = new ArrayList<>();
		lore.add("&8Couple home");
		lore.add("");
		lore.add("&7World: &f" + (location.getWorld() == null ? "Unknown" : location.getWorld().getName()));
		lore.add("&7Location: &f" + location.getBlockX() + "&7, &f" + location.getBlockY() + "&7, &f" + location.getBlockZ());
		lore.add("");
		lore.add("&aLeft-click to teleport.");
		lore.add("&cShift-click to delete.");

		return simpleItem(
				Material.ENDER_PEARL,
				"&d⌂ &f" + homeName,
				lore,
				true
		);
	}

	private ItemStack simpleItem(Material material, String name, List<String> lore, boolean glow) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return item;
		}

		meta.setDisplayName(color(name));

		List<String> coloredLore = new ArrayList<>();

		for (String line : lore) {
			coloredLore.add(color(line));
		}

		meta.setLore(coloredLore);

		if (glow) {
			Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));

			if (enchantment != null) {
				meta.addEnchant(enchantment, 1, true);
				meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			}
		}

		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		item.setItemMeta(meta);
		return item;
	}

	private void fillInventory(Inventory inventory) {
		String materialName = plugin.configs().gui().getString("main-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
		Material material = material(materialName, Material.BLACK_STAINED_GLASS_PANE);

		ItemStack filler = new ItemStack(material);
		ItemMeta meta = filler.getItemMeta();

		if (meta != null) {
			meta.setDisplayName(color(plugin.configs().gui().getString("main-menu.filler.name", " ")));
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			filler.setItemMeta(meta);
		}

		for (int slot = 0; slot < inventory.getSize(); slot++) {
			inventory.setItem(slot, filler);
		}
	}

	private ItemStack createMenuItem(Player player, String itemId) {
		String path = "items." + itemId;
		String materialName = plugin.configs().gui().getString(path + ".material", fallbackMaterial(itemId).name());
		Material material = material(materialName, fallbackMaterial(itemId));

		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return item;
		}

		String name = plugin.configs().gui().getString(path + ".name", defaultName(itemId));
		meta.setDisplayName(color(applyPlaceholders(player, name)));

		List<String> lore = new ArrayList<>();
		List<String> configuredLore = plugin.configs().gui().getStringList(path + ".lore");

		if (configuredLore.isEmpty()) {
			configuredLore = defaultLore(itemId);
		}

		for (String line : configuredLore) {
			lore.add(color(applyPlaceholders(player, line)));
		}

		meta.setLore(lore);

		int customModelData = plugin.configs().gui().getInt(path + ".custom-model-data", 0);

		if (customModelData > 0) {
			meta.setCustomModelData(customModelData);
		}

		if (plugin.configs().gui().getBoolean(path + ".glow", false)) {
			Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));

			if (enchantment != null) {
				meta.addEnchant(enchantment, 1, true);
				meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			}
		}

		meta.addItemFlags(
				ItemFlag.HIDE_ATTRIBUTES,
				ItemFlag.HIDE_DYE,
				ItemFlag.HIDE_UNBREAKABLE
		);

		item.setItemMeta(meta);
		return item;
	}

	public String getActionBySlot(int slot) {
		ConfigurationSection itemsSection = plugin.configs().gui().getConfigurationSection("items");

		if (itemsSection == null) {
			return "";
		}

		for (String itemId : itemsSection.getKeys(false)) {
			if (plugin.configs().gui().getInt("items." + itemId + ".slot", -1) == slot) {
				return itemId;
			}
		}

		return "";
	}

	private String applyPlaceholders(Player player, String text) {
		UUID playerId = player.getUniqueId();
		UUID partnerId = marriageManager.getPartnerId(playerId);

		String yes = color("&aYes");
		String no = color("&cNo");

		String partnerName = plugin.langManager().get("general.none");
		String partnerOnline = no;
		String married = no;
		long marriedDays = 0L;

		if (partnerId != null) {
			OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);

			partnerName = partner.getName() == null ? plugin.langManager().get("general.unknown") : partner.getName();
			partnerOnline = Bukkit.getPlayer(partnerId) == null ? no : yes;
			married = yes;

			long marriageDate = plugin.dataManager().dataConfig().getLong("marriage-dates." + playerId, 0L);
			marriedDays = marriageDate <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - marriageDate) / MILLIS_PER_DAY);
		}

		return text
				.replace("%player%", player.getName())
				.replace("%player_online%", yes)
				.replace("%partner%", partnerName)
				.replace("%partner_online%", partnerOnline)
				.replace("%married%", married)
				.replace("%married_days%", String.valueOf(marriedDays))
				.replace("%marriage_level%", String.valueOf(plugin.marriageXpManager().getLevel(playerId)))
				.replace("%marriage_xp%", String.valueOf(plugin.marriageXpManager().getXp(playerId)))
				.replace("%required_xp%", String.valueOf(plugin.marriageXpManager().getXpRequired(playerId)));
	}

	private List<String> defaultLore(String itemId) {
		return switch (itemId.toLowerCase(Locale.ROOT)) {
			case "profile", "partner" -> List.of(
					"&8Marriage profile",
					"",
					"&7Partner: &f%partner%",
					"&7Partner Online: %partner_online%",
					"&7Married: %married%",
					"",
					"&dClick to view profile."
			);
			case "teleport" -> List.of(
					"&8Partner teleport",
					"",
					"&7Partner: &f%partner%",
					"&7Online: %partner_online%",
					"",
					"&dClick to teleport."
			);
			case "homes" -> List.of(
					"&8Couple homes",
					"",
					"&7Manage and teleport",
					"&7to your couple homes.",
					"",
					"&dClick to view homes."
			);
			case "backpack" -> List.of(
					"&8Partner backpack",
					"",
					"&7Open your partner's",
					"&7shared backpack.",
					"",
					"&dClick to open."
			);
			case "mail" -> List.of(
					"&8Partner mail",
					"",
					"&7Read messages from",
					"&7your partner.",
					"",
					"&dClick to read mail."
			);
			case "love-notes" -> List.of(
					"&8Love notes",
					"",
					"&7Read romantic notes",
					"&7from your partner.",
					"",
					"&dClick to view notes."
			);
			case "quests" -> List.of(
					"&8Couple quests",
					"",
					"&7Complete quests together",
					"&7to earn marriage XP.",
					"",
					"&dClick to view quests."
			);
			case "anniversary" -> List.of(
					"&8Marriage anniversary",
					"",
					"&7Married for: &f%married_days% day(s)",
					"",
					"&dClick to view anniversary."
			);
			case "achievements" -> List.of(
					"&8Couple achievements",
					"",
					"&7View your unlocked",
					"&7couple achievements.",
					"",
					"&dClick to view achievements."
			);
			case "ring" -> List.of(
					"&8Wedding ring",
					"",
					"&7Get your wedding ring.",
					"&7Replacement removes old rings.",
					"",
					"&dClick to receive ring."
			);
			case "close" -> List.of(
					"&8Close menu",
					"",
					"&cClick to close."
			);
			default -> List.of(
					"&8MarriagePlus",
					"",
					"&dClick to use this option."
			);
		};
	}

	private String defaultName(String itemId) {
		return switch (itemId.toLowerCase(Locale.ROOT)) {
			case "profile" -> "&d❤ &fYour Profile";
			case "partner" -> "&d❤ &fPartner Profile";
			case "teleport" -> "&b✦ &fTeleport";
			case "homes" -> "&a⌂ &fCouple Homes";
			case "backpack" -> "&6▣ &fPartner Backpack";
			case "mail" -> "&e✉ &fPartner Mail";
			case "love-notes" -> "&c❤ &fLove Notes";
			case "quests" -> "&a✦ &fCouple Quests";
			case "anniversary" -> "&d☄ &fAnniversary";
			case "achievements" -> "&b★ &fAchievements";
			case "ring" -> "&e◇ &fWedding Ring";
			case "close" -> "&c✕ &fClose";
			default -> "&d" + itemId;
		};
	}

	private Material fallbackMaterial(String itemId) {
		return switch (itemId.toLowerCase(Locale.ROOT)) {
			case "profile", "partner" -> Material.PLAYER_HEAD;
			case "teleport" -> Material.ENDER_PEARL;
			case "homes" -> Material.OAK_DOOR;
			case "backpack" -> Material.CHEST;
			case "mail" -> Material.PAPER;
			case "love-notes" -> Material.PINK_DYE;
			case "quests" -> Material.WRITABLE_BOOK;
			case "anniversary" -> Material.CAKE;
			case "achievements" -> Material.NETHER_STAR;
			case "ring" -> Material.GOLD_NUGGET;
			case "close" -> Material.BARRIER;
			default -> Material.AMETHYST_SHARD;
		};
	}

	private Material material(String materialName, Material fallback) {
		Material material = Material.matchMaterial(materialName == null ? fallback.name() : materialName.toUpperCase(Locale.ROOT));

		if (material == null || material.isAir()) {
			return fallback;
		}

		return material;
	}

	private int normalizeSize(int size) {
		if (size < 9) {
			return 9;
		}

		if (size > 54) {
			return 54;
		}

		return ((size + 8) / 9) * 9;
	}
}