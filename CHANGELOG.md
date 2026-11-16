# Changelog

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
