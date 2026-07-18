package com.okbeanok.marriagePlus.services.lovenotes;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.LoveNote;
import com.okbeanok.marriagePlus.services.MarriageManager;
import com.okbeanok.marriagePlus.services.xp.MarriageXpManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import static com.okbeanok.marriagePlus.utils.TextUtils.color;
import static com.okbeanok.marriagePlus.utils.TextUtils.legacy;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;


public class LoveNoteManager {

	private static final String LIST_TITLE = "§d❤ Love Notes";
	private static final String DETAIL_TITLE = "§d❤ Love Note";
	private static final int NOTES_PER_PAGE = 45;
	private static final int CHAT_NOTES_PER_PAGE = 8;

	private final MarriagePlus plugin;
	private final MarriageManager marriageManager;
	private final MarriageXpManager marriageXpManager;
	private final Map<UUID, List<LoveNote>> notes = new HashMap<>();

	public LoveNoteManager(
			MarriagePlus plugin,
			MarriageManager marriageManager,
			MarriageXpManager marriageXpManager
	) {
		this.plugin = plugin;
		this.marriageManager = marriageManager;
		this.marriageXpManager = marriageXpManager;
	}

	public Map<UUID, List<LoveNote>> notes() {
		return notes;
	}

	public void noteCommand(Player player, String[] args) {
		if (!plugin.configs().loveNotes().getBoolean("enabled", true)) {
			plugin.langManager().send(player, "love-notes.disabled");
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("send")) {
			sendNote(player, args);
			return;
		}

		if (args.length >= 2 && (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("inbox"))) {
			listNotes(player, args);
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("read")) {
			readNote(player, args);
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("unread")) {
			markNoteUnread(player, args);
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("delete")) {
			deleteNote(player, args);
			return;
		}

		openNotesGui(player);
	}

	private void sendNote(Player player, String[] args) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		if (args.length < 3) {
			plugin.langManager().send(player, "love-notes.usage-send");
			return;
		}

		String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();

		if (message.isBlank()) {
			plugin.langManager().send(player, "love-notes.usage-send");
			return;
		}

		int maxLength = plugin.configs().loveNotes().getInt("max-message-length", 256);

		if (message.length() > maxLength) {
			plugin.langManager().send(player, "love-notes.too-long", Map.of(
					"%max%", String.valueOf(maxLength)
			));
			return;
		}

		Player partner = Bukkit.getPlayer(partnerId);
		OfflinePlayer offlinePartner = Bukkit.getOfflinePlayer(partnerId);
		String partnerName = safeName(offlinePartner);

		boolean saveDigitally = plugin.configs().loveNotes().getBoolean("delivery.save-digitally", true);
		boolean giveItem = plugin.configs().loveNotes().getBoolean("delivery.give-item", true);

		if (!saveDigitally && !giveItem) {
			plugin.langManager().send(player, "love-notes.no-delivery-method");
			return;
		}

		if (giveItem && !saveDigitally && partner == null) {
			plugin.langManager().send(player, "love-notes.partner-offline-item-only");
			return;
		}

		long sentAt = System.currentTimeMillis();

		if (saveDigitally) {
			List<LoveNote> partnerNotes = notes.computeIfAbsent(partnerId, ignored -> new ArrayList<>());
			int maxNotes = plugin.configs().loveNotes().getInt("max-notes-per-player", 50);

			if (partnerNotes.size() >= maxNotes) {
				plugin.langManager().send(player, "love-notes.inbox-full");
				return;
			}

			partnerNotes.add(new LoveNote(
					player.getUniqueId(),
					player.getName(),
					message,
					sentAt,
					true
			));
		}

		if (giveItem && partner != null) {
			ItemStack noteItem = createLoveNoteItem(player.getName(), partnerName, message, sentAt);

			if (noteItem != null) {
				giveItem(partner, noteItem);
			}
		}

		if (plugin.configs().loveNotes().getBoolean("grant-xp-on-send", true)) {
			int xp = plugin.configs().loveNotes().getInt("xp-on-send", 5);
			marriageXpManager.addXp(player.getUniqueId(), partnerId, xp);
		}

		plugin.dataManager().saveData();

		plugin.langManager().send(player, "love-notes.sent");

		if (partner == null) {
			return;
		}

		plugin.notificationManager().notifyPartner(player, "love-notes");

		if (saveDigitally) {
			plugin.langManager().send(partner, "love-notes.read-hint");
		}

		plugin.langManager().send(partner, "love-notes.received", Map.of(
				"%player%", player.getName()
		));
	}

	private void listNotes(Player player, String[] args) {
		List<LoveNote> inbox = notes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (inbox.isEmpty()) {
			plugin.langManager().send(player, "love-notes.empty");
			return;
		}

		int requestedPage = 1;

		if (args.length >= 3) {
			try {
				requestedPage = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				plugin.langManager().send(player, "love-notes.invalid-page");
				return;
			}
		}

		int maxPage = Math.max(1, (int) Math.ceil(inbox.size() / (double) CHAT_NOTES_PER_PAGE));
		int page = Math.max(1, Math.min(requestedPage, maxPage));
		int start = (page - 1) * CHAT_NOTES_PER_PAGE;
		int end = Math.min(start + CHAT_NOTES_PER_PAGE, inbox.size());

		plugin.langManager().send(player, "love-notes.header-page", Map.of(
				"%page%", String.valueOf(page),
				"%max_page%", String.valueOf(maxPage)
		));

		for (int index = start; index < end; index++) {
			LoveNote note = inbox.get(index);
			sendClickableNoteLine(player, note, index + 1);
		}

		plugin.langManager().send(player, "love-notes.list-hint");

		if (page < maxPage) {
			plugin.langManager().send(player, "love-notes.next-page-hint", Map.of(
					"%page%", String.valueOf(page + 1)
			));
		}
	}
	private void sendClickableNoteLine(Player player, LoveNote note, int noteNumber) {
		String lineKey = note.unread() ? "love-notes.list-line-unread" : "love-notes.list-line-read";

		Component noteLine = legacy(plugin.langManager().get(lineKey, Map.of(
				"%number%", String.valueOf(noteNumber),
				"%sender%", note.senderName()
		)))
				.clickEvent(ClickEvent.runCommand("/marry note read " + noteNumber))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("love-notes.list-line-hover", Map.of(
						"%number%", String.valueOf(noteNumber)
				)))));

		Component deleteButton = legacy(plugin.langManager().get("love-notes.list-delete-button"))
				.clickEvent(ClickEvent.runCommand("/marry note delete " + noteNumber))
				.hoverEvent(HoverEvent.showText(legacy(plugin.langManager().get("love-notes.list-delete-hover", Map.of(
						"%number%", String.valueOf(noteNumber)
				)))));

		player.sendMessage(noteLine.append(legacy(" ")).append(deleteButton));
	}


	private void readNote(Player player, String[] args) {
		if (args.length < 3) {
			plugin.langManager().send(player, "love-notes.usage-read");
			return;
		}

		Integer noteNumber = parseNoteNumber(player, args[2]);

		if (noteNumber == null) {
			return;
		}

		List<LoveNote> inbox = notes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (noteNumber < 1 || noteNumber > inbox.size()) {
			plugin.langManager().send(player, "love-notes.invalid-number");
			return;
		}

		int noteIndex = noteNumber - 1;
		LoveNote note = inbox.get(noteIndex);

		if (note.unread()) {
			inbox.set(noteIndex, new LoveNote(
					note.senderId(),
					note.senderName(),
					note.message(),
					note.sentAt(),
					false
			));

			plugin.dataManager().saveData();
		}

		plugin.langManager().send(player, "love-notes.note-header", Map.of(
				"%number%", String.valueOf(noteNumber)
		));
		plugin.langManager().send(player, "love-notes.note-from", Map.of(
				"%sender%", note.senderName()
		));
		plugin.langManager().send(player, "love-notes.note-message", Map.of(
				"%message%", note.message()
		));
		plugin.langManager().send(player, "love-notes.note-time", Map.of(
				"%time%", formatTime(note.sentAt())
		));
	}

	private void markNoteUnread(Player player, String[] args) {
		if (args.length < 3) {
			plugin.langManager().send(player, "love-notes.usage-unread");
			return;
		}

		Integer noteNumber = parseNoteNumber(player, args[2]);

		if (noteNumber == null) {
			return;
		}

		List<LoveNote> inbox = notes.getOrDefault(player.getUniqueId(), new ArrayList<>());

		if (noteNumber < 1 || noteNumber > inbox.size()) {
			plugin.langManager().send(player, "love-notes.invalid-number");
			return;
		}

		int noteIndex = noteNumber - 1;
		LoveNote note = inbox.get(noteIndex);

		if (note.unread()) {
			plugin.langManager().send(player, "love-notes.already-unread");
			return;
		}

		inbox.set(noteIndex, new LoveNote(
				note.senderId(),
				note.senderName(),
				note.message(),
				note.sentAt(),
				true
		));

		plugin.dataManager().saveData();
		plugin.langManager().send(player, "love-notes.marked-unread", Map.of(
				"%number%", String.valueOf(noteNumber)
		));
	}

	private void deleteNote(Player player, String[] args) {
		if (args.length < 3) {
			plugin.langManager().send(player, "love-notes.usage-delete");
			return;
		}

		Integer noteNumber = parseNoteNumber(player, args[2]);

		if (noteNumber == null) {
			return;
		}

		List<LoveNote> inbox = notes.get(player.getUniqueId());

		if (inbox == null || noteNumber < 1 || noteNumber > inbox.size()) {
			plugin.langManager().send(player, "love-notes.invalid-number");
			return;
		}

		inbox.remove(noteNumber - 1);

		if (inbox.isEmpty()) {
			notes.remove(player.getUniqueId());
		}

		plugin.dataManager().saveData();
		plugin.langManager().send(player, "love-notes.deleted");
	}

	private Integer parseNoteNumber(Player player, String input) {
		try {
			return Integer.parseInt(input);
		} catch (NumberFormatException exception) {
			plugin.langManager().send(player, "love-notes.invalid-number");
			return null;
		}
	}

	public void openNotesGui(Player player) {
		openNotesGui(player, 0);
	}

	private void openNotesGui(Player player, int page) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());

		if (partnerId == null) {
			plugin.langManager().send(player, "marriage.not-married");
			return;
		}

		List<GuiLoveNote> coupleNotes = getCoupleNotes(player.getUniqueId(), partnerId);

		if (coupleNotes.isEmpty()) {
			plugin.langManager().send(player, "love-notes.empty");
			return;
		}

		int maxPage = Math.max(0, (int) Math.ceil(coupleNotes.size() / (double) NOTES_PER_PAGE) - 1);
		int safePage = Math.max(0, Math.min(page, maxPage));
		Inventory inventory = Bukkit.createInventory(
				new LoveNotesListHolder(coupleNotes, safePage),
				54,
				LIST_TITLE + " §7(" + (safePage + 1) + "/" + (maxPage + 1) + ")"
		);

		int start = safePage * NOTES_PER_PAGE;
		int end = Math.min(start + NOTES_PER_PAGE, coupleNotes.size());

		for (int index = start; index < end; index++) {
			GuiLoveNote guiNote = coupleNotes.get(index);
			LoveNote note = guiNote.note();
			int slot = index - start;

			ItemStack item = new ItemStack(note.unread() && guiNote.ownerId().equals(player.getUniqueId()) ? Material.WRITABLE_BOOK : Material.WRITTEN_BOOK);
			ItemMeta meta = item.getItemMeta();

			if (meta != null) {
				boolean sentByViewer = note.senderId().equals(player.getUniqueId());
				String direction = sentByViewer ? "§7Sent by: §fYou" : "§7Sent by: §f" + note.senderName();

				meta.setDisplayName((note.unread() && !sentByViewer ? "§e✉ " : "§d❤ ") + "Love Note #" + (index + 1));
				meta.setLore(List.of(
						direction,
						"§7Sent: §f" + formatTime(note.sentAt()),
						"",
						"§f" + preview(note.message(), 40),
						"",
						"§eClick to open"
				));
				item.setItemMeta(meta);
			}

			inventory.setItem(slot, item);
		}

		if (safePage > 0) {
			inventory.setItem(45, navigationItem(Material.ARROW, "§ePrevious Page", "§7Go to page " + safePage + "."));
		}

		inventory.setItem(49, navigationItem(Material.BOOK, "§dLove Notes", "§7Click a note to view options."));

		if (safePage < maxPage) {
			inventory.setItem(53, navigationItem(Material.ARROW, "§eNext Page", "§7Go to page " + (safePage + 2) + "."));
		}

		player.openInventory(inventory);
	}

	public void handleInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		if (event.getInventory().getHolder() instanceof LoveNotesListHolder holder) {
			event.setCancelled(true);
			handleListClick(player, event.getRawSlot(), holder);
			return;
		}

		if (event.getInventory().getHolder() instanceof LoveNoteDetailHolder holder) {
			event.setCancelled(true);
			handleDetailClick(player, event.getRawSlot(), holder);
		}
	}

	private void handleListClick(Player player, int slot, LoveNotesListHolder holder) {
		if (slot == 45 && holder.page() > 0) {
			openNotesGui(player, holder.page() - 1);
			return;
		}

		int maxPage = Math.max(0, (int) Math.ceil(holder.notes().size() / (double) NOTES_PER_PAGE) - 1);

		if (slot == 53 && holder.page() < maxPage) {
			openNotesGui(player, holder.page() + 1);
			return;
		}

		if (slot < 0 || slot >= NOTES_PER_PAGE) {
			return;
		}

		int noteIndex = holder.page() * NOTES_PER_PAGE + slot;

		if (noteIndex < 0 || noteIndex >= holder.notes().size()) {
			return;
		}

		openNoteDetailGui(player, holder.notes().get(noteIndex), holder.page());
	}

	private void handleDetailClick(Player player, int slot, LoveNoteDetailHolder holder) {
		if (slot == 11) {
			giveBookFromGui(player, holder.guiNote());
			return;
		}

		if (slot == 13) {
			deleteNoteFromGui(player, holder.guiNote());
			player.closeInventory();
			openNotesGui(player, holder.previousPage());
			return;
		}

		if (slot == 15) {
			openNotesGui(player, holder.previousPage());
		}
	}

	private void openNoteDetailGui(Player player, GuiLoveNote guiNote, int previousPage) {
		markReadIfNeeded(player, guiNote);

		LoveNote note = guiNote.note();
		Inventory inventory = Bukkit.createInventory(new LoveNoteDetailHolder(guiNote, previousPage), 27, DETAIL_TITLE);

		inventory.setItem(4, createNoteInfoItem(player, guiNote));
		inventory.setItem(11, menuItem(
				Material.WRITTEN_BOOK,
				"§d❤ Get Book",
				List.of(
						"§7Receive this love note",
						"§7as a written book.",
						"",
						"§eClick to get book"
				)
		));
		inventory.setItem(13, menuItem(
				Material.BARRIER,
				"§cDelete Note",
				List.of(
						"§7Permanently deletes",
						"§7this love note.",
						"",
						"§cClick to delete"
				)
		));
		inventory.setItem(15, menuItem(
				Material.ARROW,
				"§eBack",
				List.of("§7Return to love notes.")
		));

		player.openInventory(inventory);
	}

	private ItemStack createNoteInfoItem(Player viewer, GuiLoveNote guiNote) {
		LoveNote note = guiNote.note();
		boolean sentByViewer = note.senderId().equals(viewer.getUniqueId());
		String senderLine = sentByViewer ? "§7Sent by: §fYou" : "§7Sent by: §f" + note.senderName();

		return menuItem(
				Material.PAPER,
				"§d❤ Love Note",
				List.of(
						senderLine,
						"§7Sent: §f" + formatTime(note.sentAt()),
						"",
						"§f" + preview(note.message(), 80)
				)
		);
	}

	private void giveBookFromGui(Player player, GuiLoveNote guiNote) {
		UUID partnerId = marriageManager.getPartnerId(player.getUniqueId());
		String partnerName = partnerId == null ? plugin.langManager().get("general.unknown") : safeName(Bukkit.getOfflinePlayer(partnerId));

		ItemStack item = createBookLoveNote(
				guiNote.note().senderName(),
				partnerName,
				guiNote.note().message(),
				guiNote.note().sentAt()
		);

		giveItem(player, item);
	}

	private void deleteNoteFromGui(Player player, GuiLoveNote guiNote) {
		List<LoveNote> ownerNotes = notes.get(guiNote.ownerId());

		if (ownerNotes == null) {
			return;
		}

		ownerNotes.remove(guiNote.note());
		plugin.dataManager().saveData();
		plugin.langManager().send(player, "love-notes.deleted");
	}

	private void markReadIfNeeded(Player player, GuiLoveNote guiNote) {
		if (!guiNote.ownerId().equals(player.getUniqueId()) || !guiNote.note().unread()) {
			return;
		}

		List<LoveNote> ownerNotes = notes.get(guiNote.ownerId());

		if (ownerNotes == null) {
			return;
		}

		int noteIndex = ownerNotes.indexOf(guiNote.note());

		if (noteIndex < 0) {
			return;
		}

		LoveNote note = guiNote.note();

		ownerNotes.set(noteIndex, new LoveNote(
				note.senderId(),
				note.senderName(),
				note.message(),
				note.sentAt(),
				false
		));

		plugin.dataManager().saveData();
	}

	private List<GuiLoveNote> getCoupleNotes(UUID playerId, UUID partnerId) {
		List<GuiLoveNote> coupleNotes = new ArrayList<>();

		for (LoveNote note : notes.getOrDefault(playerId, new ArrayList<>())) {
			if (note.senderId().equals(partnerId)) {
				coupleNotes.add(new GuiLoveNote(playerId, note));
			}
		}

		for (LoveNote note : notes.getOrDefault(partnerId, new ArrayList<>())) {
			if (note.senderId().equals(playerId)) {
				coupleNotes.add(new GuiLoveNote(partnerId, note));
			}
		}

		coupleNotes.sort((first, second) -> Long.compare(second.note().sentAt(), first.note().sentAt()));
		return coupleNotes;
	}

	public void notifyUnreadNotes(Player player) {
		if (!plugin.configs().loveNotes().getBoolean("notify-on-join", true)) {
			return;
		}

		long unread = notes.getOrDefault(player.getUniqueId(), new ArrayList<>()).stream()
				.filter(LoveNote::unread)
				.count();

		if (unread <= 0L) {
			return;
		}

		plugin.langManager().send(player, "love-notes.unread", Map.of(
				"%amount%", String.valueOf(unread)
		));
		plugin.langManager().send(player, "love-notes.read-hint");
	}

	private ItemStack createLoveNoteItem(String senderName, String partnerName, String message, long sentAt) {
		String itemType = plugin.configs().loveNotes().getString("delivery.item-type", "WRITTEN_BOOK");

		if (itemType != null && itemType.equalsIgnoreCase("PAPER")) {
			return createPaperLoveNote(senderName, partnerName, message, sentAt);
		}

		if (itemType != null && !itemType.equalsIgnoreCase("WRITTEN_BOOK")) {
			plugin.getLogger().warning("Invalid love note delivery.item-type '" + itemType + "'. Falling back to WRITTEN_BOOK.");
		}

		return createBookLoveNote(senderName, partnerName, message, sentAt);
	}

	private ItemStack createPaperLoveNote(String senderName, String partnerName, String message, long sentAt) {
		String materialName = plugin.configs().loveNotes().getString("delivery.paper.material", "PAPER");
		Material material = Material.matchMaterial(materialName == null ? "PAPER" : materialName.toUpperCase(Locale.ROOT));

		if (material == null || material.isAir()) {
			plugin.getLogger().warning("Invalid love note paper material '" + materialName + "'. Falling back to PAPER.");
			material = Material.PAPER;
		}

		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return item;
		}

		String name = plugin.configs().loveNotes().getString("delivery.paper.name", "&d❤ Love Note from %player%");
		meta.setDisplayName(color(applyPlaceholders(name, senderName, partnerName, message, sentAt)));

		List<String> lore = new ArrayList<>();

		for (String line : plugin.configs().loveNotes().getStringList("delivery.paper.lore")) {
			lore.add(color(applyPlaceholders(line, senderName, partnerName, message, sentAt)));
		}

		meta.setLore(lore);

		int customModelData = plugin.configs().loveNotes().getInt("delivery.paper.custom-model-data", 0);

		if (customModelData > 0) {
			meta.setCustomModelData(customModelData);
		}

		item.setItemMeta(meta);
		return item;
	}

	private ItemStack createBookLoveNote(String senderName, String partnerName, String message, long sentAt) {
		ItemStack item = new ItemStack(Material.WRITTEN_BOOK);

		if (!(item.getItemMeta() instanceof BookMeta meta)) {
			return item;
		}

		String title = plugin.configs().loveNotes().getString("delivery.book.title", "Love Note");
		String author = plugin.configs().loveNotes().getString("delivery.book.author", "%player%");
		String displayName = plugin.configs().loveNotes().getString("delivery.book.display-name", "&d❤ Love Note from %player%");

		meta.setTitle(trimBookTitle(color(applyPlaceholders(title, senderName, partnerName, message, sentAt))));
		meta.setAuthor(color(applyPlaceholders(author, senderName, partnerName, message, sentAt)));
		meta.setDisplayName(color(applyPlaceholders(displayName, senderName, partnerName, message, sentAt)));

		List<String> pages = plugin.configs().loveNotes().getStringList("delivery.book.pages");

		if (pages.isEmpty()) {
			meta.addPage(applyPlaceholders(message, senderName, partnerName, message, sentAt));
		} else {
			for (String page : pages) {
				meta.addPage(color(applyPlaceholders(page, senderName, partnerName, message, sentAt)));
			}
		}

		item.setItemMeta(meta);
		return item;
	}

	private ItemStack navigationItem(Material material, String name, String lore) {
		return menuItem(material, name, List.of(lore));
	}

	private ItemStack menuItem(Material material, String name, List<String> lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return item;
		}

		meta.setDisplayName(name);
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	private void giveItem(Player player, ItemStack item) {
		Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);

		for (ItemStack leftover : leftovers.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), leftover);
		}
	}

	private String preview(String message, int maxLength) {
		if (message.length() <= maxLength) {
			return message;
		}

		return message.substring(0, maxLength) + "...";
	}

	private String applyPlaceholders(String text, String senderName, String partnerName, String message, long sentAt) {
		return text
				.replace("%player%", senderName)
				.replace("%partner%", partnerName)
				.replace("%message%", message)
				.replace("%time%", formatTime(sentAt));
	}

	private String formatTime(long timestamp) {
		String timestampFormat = plugin.configs().loveNotes().getString("timestamp-format", "yyyy-MM-dd HH:mm");
		SimpleDateFormat dateFormat = new SimpleDateFormat(timestampFormat);

		return dateFormat.format(new Date(timestamp));
	}

	private String safeName(OfflinePlayer player) {
		return player.getName() == null ? plugin.langManager().get("general.unknown") : player.getName();
	}

	private String trimBookTitle(String title) {
		if (title.length() <= 32) {
			return title;
		}

		return title.substring(0, 32);
	}

	private record GuiLoveNote(UUID ownerId, LoveNote note) {
	}

	private record LoveNotesListHolder(List<GuiLoveNote> notes, int page) implements InventoryHolder {
		@Override
		public Inventory getInventory() {
			return null;
		}
	}

	private record LoveNoteDetailHolder(GuiLoveNote guiNote, int previousPage) implements InventoryHolder {
		@Override
		public Inventory getInventory() {
			return null;
		}
	}
}