# Changelog

## [v1.3.1] - 2026-11-17 (Hotfix)

### Config, Migration & Concurrency Fixes
- **Monolithic Config Migration Precision:** Updated `ConfigManager` to replace legacy `config.yml` with the clean commented template when migrating from pre-theme monolithic configs (preserving customized `admin` values and template comments), honor deleted entries inside `AUTHORITATIVE_LEGACY_SECTIONS` (`force-worlds.worlds`, `blocked-commands.worlds`, `anti-interrupt.worlds`, `lobby-hotbar.items`, `setting-menu.items`), deep-clone YAML collections, and upgrade only individual legacy leaves in `migrateTypographyAndTheme()` without overwriting modern user customizations.
- **Atomic & Safer `PlayerDataManager` Persistence:** Added `removeIfSetAsync(...)` to skip unnecessary writes for missing keys (used by `LobbyHotbarManager`), guarded async task scheduling against shutdown races with sync fallback, and switched `writeToDisk()` to atomic temp-file writes (`ATOMIC_MOVE` / `REPLACE_EXISTING`).
- **Reload & Combat Tag Edge-Case Fixes:** Reset `plugin.getConfig()` defaults during `reloadPluginConfig()`, guarded `setupAutoResetTask()` against `interval-minutes <= 0`, moved `settingMenuManager.reload()` after `combatTagManager.reload()` and `trainingManager.reload()` so refreshed GUIs reflect new values, enabled `combat-tag.force-worlds.enabled` automatically on world overrides, passed defensive list copies on `blocked-commands` mutations, and cleared case-variant world keys consistently across `force-worlds`, `anti-interrupt`, and `blocked-commands`.

## [v1.3.0] - 2026-11-16

### Folder-Organized Configs & Persistence Optimization
- **Clean Feature Folder Structure:** Reorganized `plugins/VCarterUtils/` from 13 flat files at root into clean domain folders: `combat/` (`combattag.yml`, `mace.yml`, `pearl.yml`, `totem.yml`), `gameplay/` (`regear.yml`, `score.yml`, `training.yml`, `xbow.yml`), `utility/` (`back.yml`, `lobby.yml`), `gui/` (`menu.yml`), and `data/` (`playerdata.yml`, `backups/`), leaving only `config.yml` and organized directories at root.
- **Seamless Automatic Upgrade Migration:** `ConfigManager` and `PlayerDataManager` automatically migrate both pre-v1.3.0 flat `.yml` files (including `playerdata.yml` and legacy backups) and pre-v1.2.7 monolithic `config.yml` into the new directory hierarchy while preserving all user customizations.
- **Centralized `PlayerDataManager` (`data/playerdata.yml`):** Replaced duplicate `playerdata.yml` file I/O across `PearlManager`, `MaceManager`, `LobbyHotbarManager`, `XbowManager`, and `TrainingManager` with a single thread-safe, dirty-tracked, coalesced async/shutdown writer.
- **Config Bug Fixes & Optimizations:**
  - Fixed `ConfigManager.migrateTypographyAndTheme()` falsely matching `<#6c7086>• ` on every reload and overwriting `gui/menu.yml` customizations.
  - Prevented JAR defaults (such as `ffa_diamond`) from leaking back into deleted config keys by isolating `FeatureConfig` defaults and cleanly clearing removed sections on reload.
  - Added dirty-only leaf-diff saving via `saveFeatureConfig(...)` so modifying one setting only writes that single feature file and preserves YAML comments on untouched keys.
  - Fixed `SettingMenuManager` ignoring `training-mode.enabled: false` and `LobbyHotbarManager` ignoring `quick-back.auto-select-on-respawn: false`.
  - Added fast-path short-circuit in `MessageUtil.format(...)` for static MiniMessage strings without `%` placeholders.

## [v1.2.9] - 2026-11-16

### GUI Synchronization & Release Workflow
- **Unified GUI Framing & Layout:** Standardized top/bottom `BLACK_STAINED_GLASS_PANE` borders and inner `GRAY_STAINED_GLASS_PANE` filler across `Personal Settings`, `Training Settings`, `World Rules`, and `Global Overrides` (matching `World Manager`).
- **Balanced 36-Slot `/training` Menu:** Realigned the 8 training options into a clean 2×4 grid (`10, 12, 14, 16` and `19, 21, 23, 25`) with a centered `« Back to Settings` button at slot `31` and wrapped 2-line descriptions.
- **1:1 Icons, Status Badges & Lore:** Reserved `nexo:cancel` for `✖ Close`, switched all `« Back` buttons to `nexo:previous` (`<#89dceb>`), removed mismatched/duplicate Nexo icons on `Master Training Mode` and `Global Overrides`, and standardized `✔ ᴇɴᴀʙʟᴇᴅ` / `✖ ᴅɪsᴀʙʟᴇᴅ` / `○ ᴅᴇꜰᴀᴜʟᴛ` badges and click prompts.
- **Hotfix-Aware Release Publishing:** Updated `.github/workflows/build.yml` to tag GitHub Release titles with `(Hotfix)` when releasing a bug-fix (`Hotfix` heading or `-hotfix` tag) while keeping clean `VCarterUtils v<version>` titles for version updates.

## [v1.2.8] - 2026-11-16

### Release & Build Automation
- Bumped plugin version to `1.2.8` in `pom.xml` and `plugin.yml`.
- Updated `.github/workflows/build.yml` to automatically build and publish/update GitHub Releases with the compiled `VCarterUtils-*.jar` whenever a `v*` tag is pushed.

### Highlights Since v1.0.0
- **Modular Configuration System:** Split settings into dedicated config files (`config.yml`, `regear.yml`, `training.yml`, `lobby.yml`, `combattag.yml`, `totem.yml`, `pearl.yml`, `mace.yml`, `xbow.yml`, `back.yml`, `score.yml`, `menu.yml`) via `ConfigManager`.
- **Training Mode (`/training`):** Added practice mode GUI and toggles (God Mode, Auto Refill Hotbar, Infinite Totem, Crossbow Auto Reload, Infinite Durability, Infinite Blocks & Rails, Infinite Consumables, Keep Inventory) with combat auto-disable and world blacklist.
- **Catppuccin Theme & Typography:** Centralized `%prefix_*%` and `%c_*%` placeholders (`MessageUtil`), MiniMessage formatting, Nexo glyph support, and 1.21 item sprite components.
- **Combat Tag & Anti-Interrupt:** Added `FoCombatTag` integration, per-world combat timer overrides (`/vcu combat`), blocked commands management (`/vcu blockedcommands`), and 1v1 anti-interrupt protection.
- **Shulker Regear & Xbow Counter:** Improved slot-accurate Shulker Regear snapshot restocking, combat/cooldown checks, and Rail + TNT Minecart + Crossbow tick speed action bar counter.
- **Lobby Hotbar, Death Back, Cooldowns & 1v1 Score:** Added configurable Lobby Hotbar shortcuts (`/rtpq`, `/rtp`, `/back`, `/decor`, `/settings`), safe `/back` death teleportation, personal/world/global Ender Pearl and Mace cooldowns (`/setting`), and flicker-free 1v1 Score action bar (`ActionBarFilter`) with PlaceholderAPI support.
