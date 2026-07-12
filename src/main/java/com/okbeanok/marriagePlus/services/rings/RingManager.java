package com.okbeanok.marriagePlus.services.rings;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.services.CooldownManager;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.profiles.ProfileManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;

public class RingManager {

	private static final long MILLIS_PER_DAY = 86_400_000L;

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final MarriageXpManager marriageXpManager;
	private final ProfileManager profileManager;
	private final CooldownManager cooldownManager;
	private final NamespacedKey ringKey;
	private final NamespacedKey ownerKey;
	private final NamespacedKey partnerKey;

	public RingManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			MarriageXpManager marriageXpManager,
			ProfileManager profileManager,
			CooldownManager cooldownManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.marriageXpManager = marriageXpManager;
		this.profileManager = profileManager;
		this.cooldownManager = cooldownManager;
		this.ringKey = new NamespacedKey(plugin, "wedding_ring");
		this.ownerKey = new NamespacedKey(plugin, "ring_owner");
		this.partnerKey = new NamespacedKey(plugin, "ring_partner");
	}

	public void ringCommand(Player player, String[] args) {
		if (!plugin.configs().rings().getBoolean("enabled", true)) {
			plugin.langManager().send(player, "rings.disabled");
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("replace")) {
			giveRing(player, true);
			return;
		}

		giveRing(player, false);
	}

	public void giveRing(Player player, boolean replacement) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (!replacement && hasWeddingRing(player)) {
			plugin.langManager().send(player, "rings.already-have");
			return;
		}

		if (replacement) {
			int cooldownSeconds = plugin.configs().rings().getInt("replace-cooldown-seconds", 60);

			if (cooldownManager.isOnCooldown(player, "ring-replace")) {
				return;
			}

			cooldownManager.setCooldown(player, "ring-replace", cooldownSeconds);
			removeWeddingRings(player);
		}

		ItemStack ring = createRing(player.getUniqueId(), partnerId);

		if (ring == null) {
			plugin.langManager().send(player, "rings.invalid-material");
			return;
		}

		giveItem(player, ring);

		plugin.langManager().send(player, replacement ? "rings.replaced" : "rings.given");
	}

	private void removeWeddingRings(Player player) {
		ItemStack[] contents = player.getInventory().getContents();

		for (int index = 0; index < contents.length; index++) {
			if (isWeddingRing(contents[index])) {
				contents[index] = null;
			}
		}

		player.getInventory().setContents(contents);

		ItemStack[] armorContents = player.getInventory().getArmorContents();

		for (int index = 0; index < armorContents.length; index++) {
			if (isWeddingRing(armorContents[index])) {
				armorContents[index] = null;
			}
		}

		player.getInventory().setArmorContents(armorContents);

		if (isWeddingRing(player.getInventory().getItemInOffHand())) {
			player.getInventory().setItemInOffHand(null);
		}
	}


	private boolean hasWeddingRing(Player player) {
		for (ItemStack item : player.getInventory().getContents()) {
			if (isWeddingRing(item)) {
				return true;
			}
		}

		for (ItemStack item : player.getInventory().getArmorContents()) {
			if (isWeddingRing(item)) {
				return true;
			}
		}

		return isWeddingRing(player.getInventory().getItemInOffHand());
	}

	public ItemStack createRing(UUID ownerId, UUID partnerId) {
		String materialName = plugin.configs().rings().getString("material", "GOLD_NUGGET");
		Material material = Material.matchMaterial(materialName == null ? "GOLD_NUGGET" : materialName.toUpperCase(Locale.ROOT));

		if (material == null || material.isAir()) {
			return null;
		}

		int amount = Math.max(1, plugin.configs().rings().getInt("amount", 1));
		ItemStack item = new ItemStack(material, amount);
		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return item;
		}

		OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
		OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);

		String ownerName = safeName(owner);
		String partnerName = safeName(partner);

		String name = plugin.configs().rings().getString("name", "&d❤ Wedding Ring");
		meta.setDisplayName(color(applyPlaceholders(name, ownerId, partnerId, ownerName, partnerName)));

		List<String> lore = new ArrayList<>();

		for (String line : plugin.configs().rings().getStringList("lore")) {
			lore.add(color(applyPlaceholders(line, ownerId, partnerId, ownerName, partnerName)));
		}

		meta.setLore(lore);

		int customModelData = plugin.configs().rings().getInt("custom-model-data", 0);

		if (customModelData > 0) {
			meta.setCustomModelData(customModelData);
		}

		if (plugin.configs().rings().getBoolean("glowing", true)) {
			Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));

			if (enchantment != null) {
				meta.addEnchant(enchantment, 1, true);
				meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			}
		}

		meta.getPersistentDataContainer().set(ringKey, PersistentDataType.BYTE, (byte) 1);
		meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerId.toString());
		meta.getPersistentDataContainer().set(partnerKey, PersistentDataType.STRING, partnerId.toString());

		item.setItemMeta(meta);
		return item;
	}

	public boolean isWeddingRing(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return false;
		}

		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return false;
		}

		Byte value = meta.getPersistentDataContainer().get(ringKey, PersistentDataType.BYTE);
		return value != null && value == (byte) 1;
	}

	private void giveItem(Player player, ItemStack item) {
		Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);

		for (ItemStack leftover : leftovers.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), leftover);
		}
	}

	private String applyPlaceholders(String text, UUID ownerId, UUID partnerId, String ownerName, String partnerName) {
		long marriageDate = plugin.dataManager().dataConfig().getLong("marriage-dates." + ownerId, 0L);
		long days = marriageDate <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - marriageDate) / MILLIS_PER_DAY);

		return text
				.replace("%player%", ownerName)
				.replace("%partner%", partnerName)
				.replace("%days%", String.valueOf(days))
				.replace("%level%", String.valueOf(marriageXpManager.getLevel(ownerId)))
				.replace("%xp%", String.valueOf(marriageXpManager.getXp(ownerId)))
				.replace("%required_xp%", String.valueOf(marriageXpManager.getXpRequired(ownerId)))
				.replace("%status%", profileManager.getStatus(ownerId));
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plugin.langManager().get("general.unknown") : player.getName();
	}
}