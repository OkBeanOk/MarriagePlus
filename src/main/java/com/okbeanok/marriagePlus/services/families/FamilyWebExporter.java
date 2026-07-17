package com.okbeanok.marriagePlus.services.families;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.okbeanok.marriagePlus.models.Family;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FamilyWebExporter {

	private final MarriagePlus plugin;
	private final FamilyManager familyManager;

	public FamilyWebExporter(MarriagePlus plugin, FamilyManager familyManager) {
		this.plugin = plugin;
		this.familyManager = familyManager;
	}

	public void export() {
		if (!plugin.configs().families().getBoolean("web.enabled", true)) {
			return;
		}

		String outputFolderPath = plugin.configs().families().getString("web.output-folder", "web/families");
		String htmlFileName = plugin.configs().families().getString("web.file-name", "index.html");
		String jsonFileName = plugin.configs().families().getString("web.json-file-name", "families.json");

		File outputFolder = new File(plugin.getDataFolder(), outputFolderPath);

		if (!outputFolder.exists() && !outputFolder.mkdirs()) {
			plugin.getLogger().warning("Could not create family web output folder.");
			return;
		}

		writeJson(new File(outputFolder, jsonFileName));
		writeHtml(new File(outputFolder, htmlFileName), jsonFileName);
	}

	public void exportFamily(Family family) {
		export();
	}

	public String familyFileName(Family family) {
		return plugin.configs().families().getString("web.file-name", "index.html");
	}

	private void writeJson(File file) {
		boolean showUuids = plugin.configs().families().getBoolean("web.show-uuids", false);
		boolean showCreatedDate = plugin.configs().families().getBoolean("web.show-created-date", true);
		boolean showOfflineMembers = plugin.configs().families().getBoolean("web.show-offline-members", true);
		boolean showHistoricalMembers = plugin.configs().families().getBoolean("web.show-historical-members", false);

		StringBuilder json = new StringBuilder();
		json.append("[\n");

		int familyIndex = 0;

		for (Family family : familyManager.families().values()) {
			if (familyIndex++ > 0) {
				json.append(",\n");
			}

			json.append("  {\n");
			json.append("    \"id\": ").append(jsonValue(showUuids ? family.id() : "")).append(",\n");
			json.append("    \"name\": ").append(jsonValue(family.name())).append(",\n");
			json.append("    \"parentOne\": ").append(playerJson(family.parentOne(), showUuids)).append(",\n");
			json.append("    \"parentTwo\": ").append(playerJson(family.parentTwo(), showUuids)).append(",\n");

			if (showCreatedDate) {
				json.append("    \"createdAt\": ").append(family.createdAt()).append(",\n");
			} else {
				json.append("    \"createdAt\": null,\n");
			}

			json.append("    \"members\": [");

			int memberIndex = 0;

			for (UUID memberId : family.members()) {
				if (family.isParent(memberId)) {
					continue;
				}

				OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);

				if (!showOfflineMembers && !member.isOnline()) {
					continue;
				}

				if (memberIndex++ > 0) {
					json.append(", ");
				}

				json.append(playerJson(memberId, showUuids));
			}

			json.append("],\n");
			json.append("    \"adoptedChildren\": [");

			int childIndex = 0;

			for (UUID childId : family.adoptedChildren()) {
				OfflinePlayer child = Bukkit.getOfflinePlayer(childId);

				if (!showOfflineMembers && !child.isOnline()) {
					continue;
				}

				if (childIndex++ > 0) {
					json.append(", ");
				}

				json.append(playerJson(childId, showUuids));
			}

			json.append("],\n");
			json.append("    \"childParents\": {");

			int childParentIndex = 0;

			for (Map.Entry<UUID, Set<UUID>> entry : family.childParents().entrySet()) {
				if (childParentIndex++ > 0) {
					json.append(", ");
				}

				json.append(jsonValue(entry.getKey().toString())).append(": [");

				int parentIndex = 0;

				for (UUID parentId : entry.getValue()) {
					if (parentIndex++ > 0) {
						json.append(", ");
					}

					json.append(jsonValue(parentId.toString()));
				}

				json.append("]");
			}

			json.append("},\n");
			json.append("    \"formerMembers\": [");

			if (showHistoricalMembers) {
				int formerIndex = 0;

				for (UUID formerMemberId : family.formerMembers()) {
					if (formerIndex++ > 0) {
						json.append(", ");
					}

					json.append(playerJson(formerMemberId, showUuids));
				}
			}

			json.append("]\n");
			json.append("  }");
		}

		json.append("\n]\n");

		try (FileWriter writer = new FileWriter(file)) {
			writer.write(json.toString());
		} catch (IOException exception) {
			plugin.getLogger().warning("Could not write families.json: " + exception.getMessage());
		}
	}

	private void writeHtml(File file, String jsonFileName) {
		String title = plugin.configs().families().getString("web.page-title", "MarriagePlus Family Trees");
		String serverName = plugin.configs().families().getString("web.server-name", "Minecraft Server");

		String html = """
				<!DOCTYPE html>
				<html lang="en">
				<head>
					<meta charset="UTF-8">
					<meta name="viewport" content="width=device-width, initial-scale=1.0">
					<title>%s</title>
					<style>
						:root {
							--bg: #080611;
							--bg-soft: #110b20;
							--card: rgba(22, 15, 37, 0.78);
							--card-strong: rgba(35, 23, 58, 0.92);
							--border: rgba(255, 255, 255, 0.12);
							--border-strong: rgba(255, 255, 255, 0.2);
							--text: #fff8ff;
							--muted: #b9a7d1;
							--muted-2: #847394;
							--pink: #ff6bd6;
							--purple: #9b6dff;
							--blue: #64b5ff;
							--green: #64ffa4;
							--yellow: #ffcf6a;
							--red: #ff6b7a;
							--shadow: 0 24px 80px rgba(0, 0, 0, 0.42);
						}

						* {
							box-sizing: border-box;
						}

						html {
							scroll-behavior: smooth;
						}

						body {
							margin: 0;
							min-height: 100vh;
							font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
							background:
								radial-gradient(circle at 15%% 10%%, rgba(255, 107, 214, 0.24), transparent 34rem),
								radial-gradient(circle at 90%% 0%%, rgba(100, 181, 255, 0.18), transparent 28rem),
								radial-gradient(circle at 65%% 85%%, rgba(155, 109, 255, 0.20), transparent 34rem),
								linear-gradient(180deg, #090612, #120b1f 52%%, #080611);
							color: var(--text);
							overflow-x: hidden;
						}

						body::before {
							content: "";
							position: fixed;
							inset: 0;
							pointer-events: none;
							background-image:
								linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
								linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
							background-size: 48px 48px;
							mask-image: linear-gradient(to bottom, black, transparent 78%%);
						}

						button,
						input,
						select {
							font: inherit;
						}

						.shell {
							width: min(1280px, calc(100%% - 32px));
							margin: 0 auto;
							padding: 28px 0 44px;
						}

						.nav {
							display: flex;
							align-items: center;
							justify-content: space-between;
							gap: 18px;
							margin-bottom: 26px;
						}

						.brand {
							display: flex;
							align-items: center;
							gap: 12px;
							min-width: 0;
						}

						.logo {
							width: 46px;
							height: 46px;
							border-radius: 16px;
							display: grid;
							place-items: center;
							background:
								linear-gradient(135deg, rgba(255, 107, 214, 0.95), rgba(155, 109, 255, 0.95)),
								#24173a;
							box-shadow: 0 14px 36px rgba(255, 107, 214, 0.24);
							font-size: 23px;
						}

						.brand-title {
							font-weight: 900;
							letter-spacing: -0.04em;
							font-size: 20px;
							white-space: nowrap;
							overflow: hidden;
							text-overflow: ellipsis;
						}

						.brand-subtitle {
							color: var(--muted);
							font-size: 13px;
							margin-top: 2px;
						}

						.pill {
							border: 1px solid var(--border);
							background: rgba(255, 255, 255, 0.06);
							color: var(--muted);
							border-radius: 999px;
							padding: 9px 13px;
							backdrop-filter: blur(18px);
							-webkit-backdrop-filter: blur(18px);
							white-space: nowrap;
						}

						.hero {
							position: relative;
							overflow: hidden;
							border: 1px solid var(--border);
							background:
								linear-gradient(135deg, rgba(255, 107, 214, 0.18), rgba(155, 109, 255, 0.13), rgba(100, 181, 255, 0.11)),
								rgba(255, 255, 255, 0.045);
							border-radius: 34px;
							padding: clamp(28px, 5vw, 54px);
							box-shadow: var(--shadow);
							backdrop-filter: blur(22px);
							-webkit-backdrop-filter: blur(22px);
						}

						.hero::after {
							content: "";
							position: absolute;
							width: 360px;
							height: 360px;
							right: -120px;
							top: -140px;
							background: radial-gradient(circle, rgba(255, 255, 255, 0.28), transparent 64%%);
							filter: blur(4px);
							pointer-events: none;
						}

						.hero-content {
							position: relative;
							z-index: 1;
							max-width: 850px;
						}

						.kicker {
							display: inline-flex;
							align-items: center;
							gap: 8px;
							border: 1px solid rgba(255, 255, 255, 0.16);
							background: rgba(255, 255, 255, 0.08);
							color: #ffe1fb;
							border-radius: 999px;
							padding: 8px 12px;
							font-size: 13px;
							font-weight: 800;
							margin-bottom: 18px;
						}

						h1 {
							margin: 0;
							font-size: clamp(40px, 7vw, 86px);
							line-height: 0.94;
							letter-spacing: -0.08em;
						}

						.gradient-text {
							background: linear-gradient(90deg, #ffffff, #ffc1f2 36%%, #bba3ff 74%%, #9bd3ff);
							-webkit-background-clip: text;
							background-clip: text;
							color: transparent;
						}

						.hero-description {
							margin: 18px 0 0;
							color: var(--muted);
							font-size: clamp(16px, 2vw, 20px);
							line-height: 1.65;
							max-width: 720px;
						}

						.toolbar {
							display: grid;
							grid-template-columns: 1fr 190px 190px;
							gap: 12px;
							margin: 20px 0;
						}

						.field {
							position: relative;
						}

						.field-icon {
							position: absolute;
							left: 15px;
							top: 50%%;
							transform: translateY(-50%%);
							color: var(--muted-2);
							pointer-events: none;
						}

						input,
						select {
							width: 100%%;
							border: 1px solid var(--border);
							background: rgba(255, 255, 255, 0.08);
							color: var(--text);
							border-radius: 18px;
							padding: 15px 16px;
							outline: none;
							backdrop-filter: blur(18px);
							-webkit-backdrop-filter: blur(18px);
							transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
						}

						input {
							padding-left: 44px;
						}

						input::placeholder {
							color: var(--muted-2);
						}

						select {
							cursor: pointer;
						}

						input:focus,
						select:focus {
							border-color: rgba(255, 107, 214, 0.65);
							box-shadow: 0 0 0 4px rgba(255, 107, 214, 0.12);
							background: rgba(255, 255, 255, 0.11);
						}

						select option {
							color: #160e24;
						}

						.stats {
							display: grid;
							grid-template-columns: repeat(4, minmax(0, 1fr));
							gap: 14px;
							margin: 18px 0 22px;
						}

						.stat {
							position: relative;
							overflow: hidden;
							border: 1px solid var(--border);
							background: rgba(255, 255, 255, 0.06);
							border-radius: 24px;
							padding: 20px;
							box-shadow: 0 16px 44px rgba(0, 0, 0, 0.16);
							backdrop-filter: blur(18px);
							-webkit-backdrop-filter: blur(18px);
						}

						.stat::after {
							content: "";
							position: absolute;
							inset: auto -40px -60px auto;
							width: 130px;
							height: 130px;
							background: radial-gradient(circle, rgba(255, 107, 214, 0.22), transparent 62%%);
						}

						.stat-value {
							position: relative;
							z-index: 1;
							font-size: 34px;
							font-weight: 950;
							letter-spacing: -0.05em;
						}

						.stat-label {
							position: relative;
							z-index: 1;
							margin-top: 4px;
							color: var(--muted);
							font-size: 14px;
						}

						.main-layout {
							display: grid;
							grid-template-columns: 320px 1fr;
							gap: 18px;
							align-items: start;
						}

						.sidebar {
							position: sticky;
							top: 18px;
							display: grid;
							gap: 14px;
						}

						.panel {
							border: 1px solid var(--border);
							background: rgba(255, 255, 255, 0.055);
							border-radius: 26px;
							padding: 18px;
							backdrop-filter: blur(18px);
							-webkit-backdrop-filter: blur(18px);
						}

						.panel-title {
							font-size: 14px;
							text-transform: uppercase;
							letter-spacing: 0.12em;
							color: var(--muted);
							font-weight: 900;
							margin-bottom: 14px;
						}

						.feature-list {
							display: grid;
							gap: 10px;
						}

						.feature {
							display: flex;
							gap: 10px;
							align-items: flex-start;
							color: var(--muted);
							line-height: 1.45;
							font-size: 14px;
						}

						.feature strong {
							color: var(--text);
						}

						.dot {
							width: 9px;
							height: 9px;
							border-radius: 999px;
							margin-top: 6px;
							background: var(--pink);
							box-shadow: 0 0 16px rgba(255, 107, 214, 0.75);
							flex: 0 0 auto;
						}

						.status {
							border: 1px solid var(--border);
							background: rgba(255, 255, 255, 0.055);
							border-radius: 22px;
							padding: 16px 18px;
							color: var(--muted);
							margin-bottom: 16px;
						}

						.status:empty {
							display: none;
						}

						.grid {
							display: grid;
							grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
							gap: 16px;
						}

						.family-card {
							position: relative;
							overflow: hidden;
							border: 1px solid var(--border);
							background:
								linear-gradient(180deg, rgba(255, 255, 255, 0.075), rgba(255, 255, 255, 0.045)),
								var(--card);
							border-radius: 28px;
							padding: 18px;
							box-shadow: 0 18px 54px rgba(0, 0, 0, 0.24);
							backdrop-filter: blur(20px);
							-webkit-backdrop-filter: blur(20px);
							transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
						}

						.family-card:hover {
							transform: translateY(-4px);
							border-color: rgba(255, 107, 214, 0.34);
							background:
								linear-gradient(180deg, rgba(255, 255, 255, 0.095), rgba(255, 255, 255, 0.055)),
								var(--card-strong);
						}

						.family-card::before {
							content: "";
							position: absolute;
							inset: 0 0 auto 0;
							height: 4px;
							background: linear-gradient(90deg, var(--pink), var(--purple), var(--blue));
						}

						.family-top {
							display: flex;
							justify-content: space-between;
							gap: 14px;
							align-items: flex-start;
							margin-bottom: 16px;
						}

						.family-name {
							margin: 0;
							font-size: 22px;
							line-height: 1.15;
							letter-spacing: -0.04em;
						}

						.family-date {
							color: var(--muted-2);
							font-size: 12px;
							margin-top: 6px;
						}

						.badges {
							display: flex;
							flex-wrap: wrap;
							justify-content: flex-end;
							gap: 7px;
						}

						.badge {
							border: 1px solid rgba(255, 255, 255, 0.12);
							background: rgba(255, 255, 255, 0.07);
							color: var(--muted);
							border-radius: 999px;
							padding: 7px 9px;
							font-size: 12px;
							font-weight: 800;
							white-space: nowrap;
						}

						.badge.online-badge {
							color: var(--green);
							border-color: rgba(100, 255, 164, 0.24);
							background: rgba(100, 255, 164, 0.08);
						}

						.tree {
							position: relative;
							display: grid;
							gap: 14px;
							margin: 16px 0;
						}
						
						.generation-tree {
							display: grid;
							gap: 12px;
							margin-top: 14px;
						}
						
						.tree-node {
							position: relative;
							display: grid;
							gap: 10px;
						}
						
						.tree-node.has-children {
							padding-left: 18px;
						}
						
						.tree-node.has-children::before {
							content: "";
							position: absolute;
							left: 5px;
							top: 28px;
							bottom: 8px;
							width: 2px;
							background: linear-gradient(to bottom, rgba(255, 107, 214, 0.55), rgba(155, 109, 255, 0.16));
							border-radius: 999px;
						}
						
						.tree-children {
							display: grid;
							gap: 10px;
							margin-left: 18px;
							padding-left: 14px;
							border-left: 1px solid rgba(255, 255, 255, 0.10);
						}
						
						.tree-branch-label {
							display: inline-flex;
							width: fit-content;
							align-items: center;
							gap: 7px;
							border: 1px solid rgba(255, 107, 214, 0.18);
							background: rgba(255, 107, 214, 0.08);
							color: #ffd7f7;
							border-radius: 999px;
							padding: 6px 9px;
							font-size: 11px;
							font-weight: 900;
							text-transform: uppercase;
							letter-spacing: 0.08em;
						}
						
						.person-row {
							display: flex;
							align-items: center;
							gap: 8px;
							flex-wrap: wrap;
						}
						
						.relationship-chip {
							border: 1px solid rgba(255, 255, 255, 0.11);
							background: rgba(255, 255, 255, 0.055);
							color: var(--muted);
							border-radius: 999px;
							padding: 5px 8px;
							font-size: 11px;
							font-weight: 800;
						}
						
						.parents {
							display: grid;
							grid-template-columns: 1fr 42px 1fr;
							align-items: center;
							gap: 10px;
						}

						.connector {
							display: grid;
							place-items: center;
							width: 42px;
							height: 42px;
							border-radius: 15px;
							border: 1px solid rgba(255, 107, 214, 0.22);
							background: rgba(255, 107, 214, 0.10);
							color: var(--pink);
							font-size: 20px;
							box-shadow: inset 0 0 20px rgba(255, 107, 214, 0.08);
						}

						.player {
							display: flex;
							align-items: center;
							gap: 11px;
							min-width: 0;
							border: 1px solid rgba(255, 255, 255, 0.10);
							background: rgba(255, 255, 255, 0.06);
							border-radius: 18px;
							padding: 10px;
						}

						.avatar {
							position: relative;
							width: 42px;
							height: 42px;
							border-radius: 14px;
							overflow: hidden;
							background: linear-gradient(135deg, var(--pink), var(--purple));
							flex: 0 0 auto;
						}

						.avatar img {
							width: 100%%;
							height: 100%%;
							display: block;
							image-rendering: pixelated;
						}

						.avatar-fallback {
							width: 100%%;
							height: 100%%;
							display: grid;
							place-items: center;
							font-weight: 950;
						}

						.status-dot {
							position: absolute;
							right: -1px;
							bottom: -1px;
							width: 13px;
							height: 13px;
							border-radius: 999px;
							border: 2px solid #181026;
							background: var(--muted-2);
						}

						.status-dot.is-online {
							background: var(--green);
							box-shadow: 0 0 12px rgba(100, 255, 164, 0.75);
						}

						.player-body {
							min-width: 0;
						}

						.player-name {
							font-weight: 900;
							overflow: hidden;
							text-overflow: ellipsis;
							white-space: nowrap;
						}

						.player-meta {
							margin-top: 2px;
							font-size: 12px;
							color: var(--muted-2);
						}

						.player-meta.online {
							color: var(--green);
						}

						.section {
							margin-top: 14px;
						}

						.section-head {
							display: flex;
							align-items: center;
							justify-content: space-between;
							gap: 12px;
							margin-bottom: 9px;
						}

						.section-title {
							font-size: 12px;
							text-transform: uppercase;
							letter-spacing: 0.13em;
							font-weight: 950;
							color: var(--muted);
						}

						.section-count {
							font-size: 12px;
							color: var(--muted-2);
						}

						.member-strip {
							display: flex;
							flex-wrap: wrap;
							gap: 8px;
						}

						.mini-player {
							display: inline-flex;
							align-items: center;
							gap: 8px;
							max-width: 100%%;
							border: 1px solid rgba(255, 255, 255, 0.10);
							background: rgba(255, 255, 255, 0.055);
							border-radius: 999px;
							padding: 6px 10px 6px 6px;
							color: var(--text);
						}

						.mini-avatar {
							width: 24px;
							height: 24px;
							border-radius: 8px;
							overflow: hidden;
							background: linear-gradient(135deg, var(--purple), var(--blue));
							flex: 0 0 auto;
						}

						.mini-avatar img {
							width: 100%%;
							height: 100%%;
							image-rendering: pixelated;
						}

						.mini-name {
							overflow: hidden;
							text-overflow: ellipsis;
							white-space: nowrap;
							font-size: 13px;
							font-weight: 800;
						}

						.empty {
							border: 1px dashed rgba(255, 255, 255, 0.14);
							background: rgba(255, 255, 255, 0.035);
							color: var(--muted-2);
							border-radius: 18px;
							padding: 13px;
							font-size: 14px;
						}

						.details {
							margin-top: 14px;
							border-top: 1px solid rgba(255, 255, 255, 0.09);
							padding-top: 14px;
						}

						.details-toggle {
							width: 100%%;
							border: 1px solid rgba(255, 255, 255, 0.12);
							background: rgba(255, 255, 255, 0.06);
							color: var(--text);
							border-radius: 16px;
							padding: 12px 14px;
							cursor: pointer;
							font-weight: 900;
							transition: background 0.18s ease, border-color 0.18s ease;
						}

						.details-toggle:hover {
							background: rgba(255, 255, 255, 0.09);
							border-color: rgba(255, 107, 214, 0.32);
						}

						.details-body {
							display: none;
							margin-top: 12px;
						}

						.family-card.is-open .details-body {
							display: grid;
							gap: 12px;
						}

						.empty-state,
						.error {
							border: 1px solid var(--border);
							background: rgba(255, 255, 255, 0.055);
							border-radius: 28px;
							padding: 28px;
							color: var(--muted);
							text-align: center;
						}

						.error {
							border-color: rgba(255, 107, 122, 0.32);
							background: rgba(255, 107, 122, 0.09);
							color: #ffd8dd;
						}

						.footer {
							margin-top: 28px;
							color: var(--muted-2);
							text-align: center;
							font-size: 13px;
						}

						@media (max-width: 980px) {
							.toolbar {
								grid-template-columns: 1fr;
							}

							.main-layout {
								grid-template-columns: 1fr;
							}

							.sidebar {
								position: static;
								grid-template-columns: repeat(2, minmax(0, 1fr));
							}
						}

						@media (max-width: 720px) {
							.shell {
								width: min(100%% - 20px, 1280px);
								padding-top: 16px;
							}

							.nav {
								align-items: flex-start;
								flex-direction: column;
							}

							.hero {
								border-radius: 26px;
							}

							.stats {
								grid-template-columns: repeat(2, minmax(0, 1fr));
							}

							.sidebar {
								grid-template-columns: 1fr;
							}

							.grid {
								grid-template-columns: 1fr;
							}

							.parents {
								grid-template-columns: 1fr;
							}

							.connector {
								width: 100%%;
							}

							.family-top {
								flex-direction: column;
							}

							.badges {
								justify-content: flex-start;
							}
						}
					</style>
				</head>
				<body>
					<div class="shell">
						<header class="nav">
							<div class="brand">
								<div class="logo">❤</div>
								<div>
									<div class="brand-title">%s</div>
									<div class="brand-subtitle">Live family tree dashboard</div>
								</div>
							</div>
							<div class="pill" id="lastUpdated">Loading data...</div>
						</header>

						<section class="hero">
							<div class="hero-content">
								<div class="kicker">✦ Minecraft Family Network</div>
								<h1><span class="gradient-text">%s</span></h1>
								<p class="hero-description">
									Explore families, parents, adopted children, online members, and relationship history across %s.
								</p>
							</div>
						</section>

						<section class="stats">
							<div class="stat">
								<div class="stat-value" id="familyCount">0</div>
								<div class="stat-label">Families</div>
							</div>
							<div class="stat">
								<div class="stat-value" id="memberCount">0</div>
								<div class="stat-label">Unique Members</div>
							</div>
							<div class="stat">
								<div class="stat-value" id="onlineCount">0</div>
								<div class="stat-label">Online Now</div>
							</div>
							<div class="stat">
								<div class="stat-value" id="visibleCount">0</div>
								<div class="stat-label">Visible Results</div>
							</div>
						</section>

						<section class="toolbar">
							<div class="field">
								<span class="field-icon">⌕</span>
								<input id="search" placeholder="Search families, parents, children..." autocomplete="off">
							</div>
							<select id="filter">
								<option value="all">All families</option>
								<option value="online">Has online players</option>
								<option value="children">Has children</option>
								<option value="former">Has history</option>
							</select>
							<select id="sort">
								<option value="name">Sort by name</option>
								<option value="members">Most members</option>
								<option value="online">Most online</option>
								<option value="newest">Newest first</option>
								<option value="oldest">Oldest first</option>
							</select>
						</section>

						<div class="main-layout">
							<aside class="sidebar">
								<div class="panel">
									<div class="panel-title">Overview</div>
									<div class="feature-list">
										<div class="feature"><span class="dot"></span><span><strong>Search</strong> by family or player name.</span></div>
										<div class="feature"><span class="dot"></span><span><strong>Filter</strong> online families and family history.</span></div>
										<div class="feature"><span class="dot"></span><span><strong>Expand</strong> a family to view extra details.</span></div>
									</div>
								</div>
								<div class="panel">
									<div class="panel-title">Top Family</div>
									<div id="topFamily">Loading...</div>
								</div>
							</aside>

							<main>
								<div id="status" class="status">Loading family data...</div>
								<section id="families" class="grid"></section>
							</main>
						</div>

						<div class="footer">
							Generated by MarriagePlus.
						</div>
					</div>

					<script>
						const container = document.getElementById('families');
						const search = document.getElementById('search');
						const filter = document.getElementById('filter');
						const sort = document.getElementById('sort');
						const statusBox = document.getElementById('status');
						const topFamilyBox = document.getElementById('topFamily');
						const lastUpdated = document.getElementById('lastUpdated');

						const familyCount = document.getElementById('familyCount');
						const memberCount = document.getElementById('memberCount');
						const onlineCount = document.getElementById('onlineCount');
						const visibleCount = document.getElementById('visibleCount');

						let families = [];

						function escapeHtml(value) {
							return String(value ?? '')
								.replaceAll('&', '&amp;')
								.replaceAll('<', '&lt;')
								.replaceAll('>', '&gt;')
								.replaceAll('"', '&quot;')
								.replaceAll("'", '&#039;');
						}

						function playerKey(player) {
							return player?.id || player?.uuid || player?.name || Math.random().toString();
						}

						function allPlayers(family) {
							return [
								family.parentOne,
								family.parentTwo,
								...(family.members || []),
								...(family.adoptedChildren || [])
							].filter(Boolean);
						}

						function uniquePlayers(players) {
							const seen = new Set();

							return players.filter(player => {
								const key = playerKey(player).toLowerCase();

								if (seen.has(key)) {
									return false;
								}

								seen.add(key);
								return true;
							});
						}

						function initials(name) {
							const clean = String(name || '?').trim();
							return clean.length ? clean[0].toUpperCase() : '?';
						}

						function avatarUrl(player, size = 64) {
							const name = encodeURIComponent(player?.name || 'Steve');
							return `https://minotar.net/avatar/${name}/${size}`;
						}

						function formatDate(timestamp) {
							if (!timestamp) {
								return 'Unknown date';
							}

							return new Date(timestamp).toLocaleDateString(undefined, {
								year: 'numeric',
								month: 'short',
								day: 'numeric'
							});
						}

						function playerCard(player) {
							const safeName = escapeHtml(player?.name || 'Unknown');
							const isOnline = Boolean(player?.online);
							const statusText = isOnline ? 'Online now' : 'Offline';
							const statusClass = isOnline ? 'online' : '';

							return `
								<div class="player" title="${safeName}">
									<div class="avatar">
										<img src="${avatarUrl(player)}" alt="${safeName}" loading="lazy" onerror="this.style.display='none'; this.nextElementSibling.style.display='grid';">
										<div class="avatar-fallback" style="display:none;">${escapeHtml(initials(player?.name))}</div>
										<span class="status-dot ${isOnline ? 'is-online' : ''}"></span>
									</div>
									<div class="player-body">
										<div class="player-name">${safeName}</div>
										<div class="player-meta ${statusClass}">${statusText}</div>
									</div>
								</div>
							`;
						}

						function miniPlayer(player) {
							const safeName = escapeHtml(player?.name || 'Unknown');

							return `
								<div class="mini-player" title="${safeName}">
									<div class="mini-avatar">
										<img src="${avatarUrl(player, 32)}" alt="${safeName}" loading="lazy">
									</div>
									<span class="mini-name">${safeName}</span>
								</div>
							`;
						}

						function miniList(players, emptyText) {
							if (!players || players.length === 0) {
								return `<div class="empty">${escapeHtml(emptyText)}</div>`;
							}

							return `<div class="member-strip">${players.map(miniPlayer).join('')}</div>`;
						}

						function familyMemberCount(family) {
							return uniquePlayers(allPlayers(family)).length;
						}

						function familyOnlineCount(family) {
							return uniquePlayers(allPlayers(family)).filter(player => player.online).length;
						}

						function playerMap(family) {
							const map = new Map();

							for (const player of [
								family.parentOne,
								family.parentTwo,
								...(family.members || []),
								...(family.adoptedChildren || []),
								...(family.formerMembers || [])
							].filter(Boolean)) {
								if (player.id) {
									map.set(player.id, player);
								}

								if (player.uuid) {
									map.set(player.uuid, player);
								}

								if (player.name) {
									map.set(player.name, player);
								}
							}

							return map;
						}

						function childParentMap(family) {
							return family.childParents && typeof family.childParents === 'object'
								? family.childParents
								: {};
						}

						function childrenOf(parentId, family) {
							const childParents = childParentMap(family);

							return Object.entries(childParents)
								.filter(([, parentIds]) => Array.isArray(parentIds) && parentIds.includes(parentId))
								.map(([childId]) => childId);
						}

						function rootChildren(family) {
							const parentIds = [
								playerKey(family.parentOne),
								playerKey(family.parentTwo)
							].filter(Boolean);
				
							const childParents = childParentMap(family);

							return Object.entries(childParents)
								.filter(([, linkedParents]) =>
									Array.isArray(linkedParents) && linkedParents.some(parentId => parentIds.includes(parentId))
								)
								.map(([childId]) => childId);
						}

						function renderTreeNode(playerId, family, players, visited = new Set(), depth = 0) {
							if (!playerId || visited.has(playerId)) {
								return '';
							}

							visited.add(playerId);

							const player = players.get(playerId) || { uuid: playerId, name: 'Unknown', online: false };
							const childIds = childrenOf(playerId, family)
								.filter(childId => !visited.has(childId));
							const hasChildren = childIds.length > 0;
							const label = depth === 0 ? 'Child' : depth === 1 ? 'Grandchild' : `${depth + 1}th Generation`;

							return `
								<div class="tree-node ${hasChildren ? 'has-children' : ''}">
									<div class="person-row">
										${miniPlayer(player)}
										<span class="relationship-chip">${escapeHtml(label)}</span>
									</div>
									${hasChildren ? `
										<div class="tree-children">
											${childIds.map(childId => renderTreeNode(childId, family, players, new Set(visited), depth + 1)).join('')}
										</div>
									` : ''}
								</div>
							`;
						}

						function recursiveFamilyTree(family) {
							const players = playerMap(family);
							const childIds = [...new Set(rootChildren(family))];

							if (childIds.length === 0) {
								return `<div class="empty">No descendants listed yet.</div>`;
							}

							return `
								<div class="generation-tree">
									<div class="tree-branch-label">Family Tree</div>
									${childIds.map(childId => renderTreeNode(childId, family, players)).join('')}
								</div>
							`;
						}

						function unlinkedMembers(family) {
							const childIds = new Set(Object.keys(childParentMap(family)));
							const parentIds = new Set([
								playerKey(family.parentOne),
								playerKey(family.parentTwo)
							].filter(Boolean));

							return uniquePlayers([
								...(family.members || []),
								...(family.adoptedChildren || [])
							]).filter(player => {
								const key = playerKey(player);
								return key && !childIds.has(key) && !parentIds.has(key);
							});
						}

						function familySearchText(family) {
							return [
								family.name,
								family.parentOne?.name,
								family.parentTwo?.name,
								...(family.members || []).map(player => player.name),
								...(family.adoptedChildren || []).map(player => player.name),
								...(family.formerMembers || []).map(player => player.name)
							].filter(Boolean).join(' ').toLowerCase();
						}

						function matchesFilter(family) {
							const selected = filter.value;

							if (selected === 'online') {
								return familyOnlineCount(family) > 0;
							}

							if (selected === 'children') {
								return (family.members || []).length > 0 || (family.adoptedChildren || []).length > 0;
							}

							if (selected === 'former') {
								return (family.formerMembers || []).length > 0;
							}

							return true;
						}

						function sortFamilies(items) {
							const selected = sort.value;
							const sorted = [...items];

							if (selected === 'members') {
								return sorted.sort((a, b) => familyMemberCount(b) - familyMemberCount(a));
							}

							if (selected === 'online') {
								return sorted.sort((a, b) => familyOnlineCount(b) - familyOnlineCount(a));
							}

							if (selected === 'newest') {
								return sorted.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
							}

							if (selected === 'oldest') {
								return sorted.sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
							}

							return sorted.sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
						}

						function renderStats(visibleFamilies) {
							const players = uniquePlayers(families.flatMap(allPlayers));

							familyCount.textContent = families.length;
							memberCount.textContent = players.length;
							onlineCount.textContent = players.filter(player => player.online).length;
							visibleCount.textContent = visibleFamilies.length;
						}

						function renderTopFamily() {
							if (families.length === 0) {
								topFamilyBox.innerHTML = '<div class="empty">No family data yet.</div>';
								return;
							}

							const topFamily = [...families].sort((a, b) => familyMemberCount(b) - familyMemberCount(a))[0];

							topFamilyBox.innerHTML = `
								<div class="feature">
									<span class="dot"></span>
									<span>
										<strong>${escapeHtml(topFamily.name || 'Unnamed Family')}</strong><br>
										${familyMemberCount(topFamily)} members · ${familyOnlineCount(topFamily)} online
									</span>
								</div>
							`;
						}

						function familyCard(family, index) {
							const members = family.members || [];
							const adoptedChildren = family.adoptedChildren || [];
							const formerMembers = family.formerMembers || [];
							const totalMembers = familyMemberCount(family);
							const onlineMembers = familyOnlineCount(family);
							const cardId = `family-card-${index}`;

							return `
								<article class="family-card" id="${cardId}">
									<div class="family-top">
										<div>
											<h2 class="family-name">${escapeHtml(family.name || 'Unnamed Family')}</h2>
											<div class="family-date">Created ${escapeHtml(formatDate(family.createdAt))}</div>
										</div>
										<div class="badges">
											<span class="badge">${totalMembers} members</span>
											<span class="badge online-badge">${onlineMembers} online</span>
										</div>
									</div>

									<div class="tree">
										<div class="section-head">
											<div class="section-title">Parents</div>
										</div>
										<div class="parents">
											${playerCard(family.parentOne)}
											<div class="connector">❤</div>
											${playerCard(family.parentTwo)}
										</div>

										${recursiveFamilyTree(family)}
									</div>

									<div class="section">
										<div class="section-head">
											<div class="section-title">Unlinked Members</div>
											<div class="section-count">${unlinkedMembers(family).length}</div>
										</div>
										${miniList(unlinkedMembers(family), 'No unlinked members.')}
									</div>

									<div class="details">
										<button class="details-toggle" type="button" onclick="toggleFamily('${cardId}', this)">
											Show family details
										</button>
										<div class="details-body">
											<div class="section">
												<div class="section-head">
													<div class="section-title">Adopted Children</div>
													<div class="section-count">${adoptedChildren.length}</div>
												</div>
												${miniList(adoptedChildren, 'No adopted children listed.')}
											</div>

											<div class="section">
												<div class="section-head">
													<div class="section-title">Former Members</div>
													<div class="section-count">${formerMembers.length}</div>
												</div>
												${miniList(formerMembers, 'No former members listed.')}
											</div>
										</div>
									</div>
								</article>
							`;
						}

						function toggleFamily(cardId, button) {
							const card = document.getElementById(cardId);

							if (!card) {
								return;
							}

							card.classList.toggle('is-open');
							button.textContent = card.classList.contains('is-open')
								? 'Hide family details'
								: 'Show family details';
						}

						function render() {
							const query = search.value.trim().toLowerCase();

							const visibleFamilies = sortFamilies(families.filter(family =>
								familySearchText(family).includes(query) && matchesFilter(family)
							));

							renderStats(visibleFamilies);
							renderTopFamily();

							if (families.length === 0) {
								statusBox.innerHTML = '<div class="empty-state">No families have been exported yet. Run <strong>/family web export</strong> after creating a family.</div>';
								container.innerHTML = '';
								return;
							}

							if (visibleFamilies.length === 0) {
								statusBox.innerHTML = '<div class="empty-state">No families match your search or filter.</div>';
								container.innerHTML = '';
								return;
							}

							statusBox.textContent = '';
							container.innerHTML = visibleFamilies.map(familyCard).join('');
						}

						fetch('%s', { cache: 'no-store' })
							.then(response => {
								if (!response.ok) {
									throw new Error('Could not load families.json. HTTP ' + response.status);
								}

								return response.json();
							})
							.then(data => {
								families = Array.isArray(data) ? data : [];
								lastUpdated.textContent = 'Updated ' + new Date().toLocaleTimeString();
								render();
							})
							.catch(error => {
								lastUpdated.textContent = 'Data failed to load';
								statusBox.innerHTML = `<div class="error">${escapeHtml(error.message)}<br>Run <strong>/family web export</strong> and make sure families.json is accessible.</div>`;
								container.innerHTML = '';
							});

						search.addEventListener('input', render);
						filter.addEventListener('change', render);
						sort.addEventListener('change', render);
					</script>
				</body>
				</html>
				""".formatted(title, title, title, serverName, jsonFileName);

		try (FileWriter writer = new FileWriter(file)) {
			writer.write(html);
		} catch (IOException exception) {
			plugin.getLogger().warning("Could not write family web page: " + exception.getMessage());
		}
	}

	private String playerJson(UUID uuid, boolean showUuid) {
		OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

		return "{"
				+ "\"id\":" + jsonValue(uuid.toString()) + ","
				+ "\"name\":" + jsonValue(player.getName() == null ? "Unknown" : player.getName()) + ","
				+ "\"online\":" + player.isOnline() + ","
				+ "\"uuid\":" + jsonValue(showUuid ? uuid.toString() : "")
				+ "}";
	}

	private String jsonValue(String value) {
		if (value == null) {
			return "null";
		}

		return "\"" + value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				+ "\"";
	}
}