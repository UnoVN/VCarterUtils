# Changelog

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
