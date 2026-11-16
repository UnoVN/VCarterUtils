# VCARTERUTILS — ANTIGRAVITY WORKSPACE RULES & ARCHITECTURE INDEX

## 1. OPERATIONAL & CODING RULES (ALWAYS ENFORCE)
* **Index-First Navigation:** Always check Section 4 (`@src/...` Class & Method Index) to locate classes, managers, and methods before scanning or searching the repository.
* **Keep Index Updated:** Whenever you add, rename, or modify a class, public method signature, command, or PDC key, update `@GEMINI.md` in the same task.
* **In-Place Edits & Local Build Verification:** Edit files directly in the workspace. After making code changes, run `mvn test-compile` (or `mvn package -DskipTests`) in the terminal to verify zero compilation or missing-import errors against Java 21 and Paper `1.21.1-R0.1-SNAPSHOT`.
* **Config & Concurrency Invariants:**
  * In `@src/main/java/me/nh4n/vcarterutils/config/ConfigManager.java`, modular configuration files (`config.yml`, `regear.yml`, `training.yml`, `lobby.yml`, `combattag.yml`, `totem.yml`, `pearl.yml`, `mace.yml`, `xbow.yml`, `back.yml`, `score.yml`, `menu.yml`) are managed independently with bidirectional synchronization into `plugin.getConfig()`.
  * In `@src/main/java/me/nh4n/vcarterutils/VCarterUtils.java`, always call `getConfig().setDefaults(new YamlConfiguration())` immediately after `saveDefaultConfig()` and `reloadConfig()` so deleted keys in `config.yml` are never restored from jar defaults.
  * Synchronize all `playerdata.yml` read/write operations across managers using `synchronized (VCarterUtils.class)`.
* **Soft-Depend Reflection Safety:** Never hard-import `Nexo` or `FoCombatTag` classes without reflection fallbacks; use `EventExecutor` and reflection (`setAccessible(true)`) so the plugin runs cleanly if soft-depends are absent.
* **Auto-Commit, Version Bump & Push Tag After Every Update:** After completing any code/config update and verifying compilation, update `@GEMINI.md` if signatures changed, always bump the plugin version in `pom.xml`, `src/main/resources/plugin.yml`, and `@GEMINI.md`, prepend a release entry in `@CHANGELOG.md`, review `git status`, stage changes (`git add -A`), commit cleanly, push to `origin main` (`https://github.com/UnoVN/VCarterUtils`) without force-pushing, and create & push the matching annotated git tag (`v<version>`) so GitHub Actions publishes the GitHub Release automatically.
  * **Release Labeling Rule:** If the update fixes a bug, label it as **Hotfix** (`## [v<version>] - YYYY-MM-DD (Hotfix)` in `@CHANGELOG.md`, producing release title `VCarterUtils v<version> (Hotfix)`). If the update is a normal version/feature/refinement update, just bump the version cleanly (`## [v<version>] - YYYY-MM-DD`, release title `VCarterUtils v<version>`).
  * If GitHub push authentication fails, report the error to the user immediately.

## 2. UI/UX, MINIMESSAGE, THEME & CATPPUCCIN PALETTE
* **Engine & Language:** Use Paper native **Adventure API** and **MiniMessage**. All in-game messages must be in concise, scannable **English** with modern Minecraft typography: Unicode symbols (`✦`, `»`, `•`, `▪`, `│`, `✔`, `✖`, `●`, `○`, `⚔`, `«`) and Unicode Small Caps ("minitext", e.g., `ᴘᴇʀsᴏɴᴀʟ sᴇᴛᴛɪɴɢ`, `✔ ᴇɴᴀʙʟᴇᴅ`, `✖ ᴅɪsᴀʙʟᴇᴅ`, `ɪɴ ᴄᴏᴍʙᴀᴛ`, `ᴄᴏᴏʟᴅᴏᴡɴ ʟᴏᴄᴋᴇᴅ`).
* **Global Theme & Rapid Customization (`theme:` in `config.yml` & `MessageUtil.java`):**
  * Centralized prefix and color replacement across all 12 modular config files and managers via `plugin.parseMessage(...)` and `plugin.formatMessage(...)`.
  * **Prefix Placeholders (`<glyph:warning_blue>`, `<glyph:warning_red>`, `<glyph:warning_purple>`):**
    * `%prefix%` / `%prefix_info%` / `%prefix_blue%` (`theme.prefixes.info`, default: `<glyph:warning_blue> `).
    * `%prefix_success%` (`theme.prefixes.success`, default: `<glyph:warning_blue> `).
    * `%prefix_error%` / `%prefix_red%` (`theme.prefixes.error`, default: `<glyph:warning_red> `).
    * `%prefix_combat%` (`theme.prefixes.combat`, default: `<glyph:warning_red> `).
    * `%prefix_warning%` / `%prefix_purple%` (`theme.prefixes.warning`, default: `<glyph:warning_purple> `).
    * `%prefix_admin%` (`theme.prefixes.admin`, default: `<glyph:warning_purple> `).
  * **Catppuccin Color Placeholders (`theme.colors`):**
    * `%c_primary%`: Sky `<#89dceb>` (player names, item names, commands, primary stats).
    * `%c_success%`: Green `<#a6e3a1>` (success messages, `✔ ᴇɴᴀʙʟᴇᴅ`/`✔ ᴏɴ` states, `[✔ ᴀᴄᴄᴇᴘᴛ]` buttons).
    * `%c_error%`: Red `<#f38ba8>` (errors, lock warnings, `✖ ᴅɪsᴀʙʟᴇᴅ`/`✖ ᴏꜰꜰ` states, `[✖ ᴅᴇᴄʟɪɴᴇ]` buttons).
    * `%c_warning%`: Yellow `<#f9e2af>` (cooldown timers, duration seconds, emphasized actions).
    * `%c_purple%`: Mauve `<#cba6f7>` (staff/admin headers, world accents, special badges).
    * `%c_text%`: Subtext0 `<#a6adc8>` (primary descriptive text).
    * `%c_muted%`: Overlay0 `<#6c7086>` (separators `»`, `│`, `▪`, and minitext category subheaders).
* **Strict Typography & Icon Synchronization Rule (NO GLYPH + SPRITE MIXING):**
  * **Rule A (Glyph Prefix Lines):** If a message or line starts with a glyph prefix (`%prefix_info%`, `%prefix_error%`, `%prefix_warning%`, `%prefix_admin%`, `%prefix_combat%`, i.e., `<glyph:warning_blue/red/purple>`), it **MUST NEVER** contain an item text component (`<sprite:...>`) or a second glyph (`<glyph:...>`) anywhere after the prefix on the same line. Use bold minitext status tags instead (e.g., `%prefix_error%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» ...`).
  * **Rule B (Item Text Component Lines):** Item-centric notifications, broadcasts, and action bars (`pearl.cooldown-changed`, `mace.cooldown-changed`, `back.death-notify`, `back.teleported`, `score.score-reset-success`, `xbow.action-bar-format`) and GUI item titles use leading 1.21.11 Item Text Components (`<white><sprite:items:item/...></white>`) **WITHOUT** a `<glyph:warning_*>` prefix. Regear messages use original `<glyph:warning_blue>` formatting.
  * **1:1 Texture-to-Item Name Match:** In Lobby Hotbar and GUIs, item display names match their Nexo textures 1:1 (`Cosmetic` for `nexo:cosmetic`, `Settings` for `nexo:settings`). Mismatched glyphs (such as `<glyph:social>` or `<glyph:admin_setting>` on personal items) are strictly prohibited.
* **GUI & Lore Formatting:** Use `<white><glyph:left_click></white>` and `<white><glyph:right_click></white>` for click prompts. Always use valid 2D vanilla item atlas names inside `<white><sprite:items:item/...></white>` (`ender_pearl`, `mace`, `netherite_sword`, `totem_of_undying`, `golden_apple`, `tnt_minecart`, `rail`, `crossbow_standby`, `diamond_chestplate`, `shulker_shell`, `compass_16`, `recovery_compass_16`).

## 3. TECH STACK & CRITICAL INTER-SYSTEM PIPELINES
* **Plugin Info:** `VCarterUtils` v1.2.9 | Author: `Nh4n` | Main: `me.nh4n.vcarterutils.VCarterUtils` | Java 21 | Compile API: Paper `1.21.1-R0.1-SNAPSHOT` (`api-version: '1.21'`) | Runtime: Paper / Leaf `1.21.11`.
* **Soft-Depends:** `PlaceholderAPI` (2.11.6), `PerPlayerKit` (`/k1`..`/k99`, `/kit`, `/pk`, `/rekit`), `Nexo`, `FoCombatTag`, `Multiverse-Core`, `SlimeWorldManager`, `AdvancedSlimeWorldManager`, `SlimeWorldPlugin`.
* **Respawn, AutoPot, Regear & Lobby Hotbar Pipeline (Tick-Sensitive):**
  * **Tick 0 (`PlayerDeathEvent`):** `LobbyHotbarManager.markRespawning(uuid)` clears stale `savedHotbars` and blocks `PlayerChangedWorldEvent` from saving an empty hotbar before `PerPlayerKit` loads items. `FoCombatTagHook.untag(player)` and `CombatTagManager.endCombat(uuid)` clear combat state so `/back` works immediately.
  * **Tick 5 (`AutoPotListener.scheduleKitProcess`):** After `PerPlayerKit` finishes populating inventory (or on `PlayerRespawnEvent` where combat checks are bypassed): (1) `TotemManager.enforceInventoryLimit` (bypassed if in Training Mode), (2) differential potion sync (`syncKitEffects` — strips harmful effects, keeps potion bottles, clears old finite effects missing from new kit, plays `ENTITY_SPLASH_POTION_BREAK`), (3) `RegearManager.saveKitSnapshot(player)` captures 41 slots, and (4) if `fromRespawn == true`, calls `LobbyHotbarManager.applyLobbyHotbar(player)` to save real hotbar slots `0–8` into `savedHotbars`/`playerdata.yml` before placing Lobby Hotbar items (selecting slot 4 for quick `/back`).
* **Lobby Hotbar Layout:**
  * Slot 0 (`IRON_SWORD`): Matchmaking Queue (`/rtpq`).
  * Slot 1 (`COMPASS`): Random Teleport (`/rtp`).
  * Slot 4 (`RECOVERY_COMPASS`): Return to Death Location (`/back`, auto-selected on respawn).
  * Slot 7 (`nexo:cosmetic`): Cosmetic studio (`/decor`, displayed as `Cosmetic`).
  * Slot 8 (`nexo:settings`): Personal PvP Settings (`/settings`, displayed as `Settings`).
* **Training Mode & Anti-Totem Hook:**
  * When Training Mode is active (`plugin.getTrainingManager().isPlayerEnabled(uuid) == true`), Anti-Totem limits (`totem.max-in-inventory` and `totem.max-activations-per-kit`) are completely bypassed/disabled for that player in `enforceInventoryLimit`, `EntityPickupItemEvent`, and `EntityResurrectEvent`.
  * `AUTO_REFILL` auto-replenishes rails, TNT carts, throwables, pearls, consumables, and blocks without inventory depletion. Redundant `INFINITE_PROJECTILES` option is removed.
  * 8 Clean Balanced Options: God Mode (invulnerability), Auto Refill Hotbar, Infinite Totem, Crossbow Auto Reload, Infinite Durability, Infinite Blocks & Rails, Infinite Consumables, Keep Inventory.
* **Cooldown Priority (`PearlManager` & `MaceManager`):**
  * Priority order in `getEffectiveCooldownSeconds(player)`: (1) Per-World Force (`force-worlds`), (2) Global Force (`global-force`), (3) Personal setting in `playerdata.yml` (clamped `>= default-seconds`, minimum `1s`). If locked by World/Global force, `/setting` displays `✖ CANNOT CHANGE PERSONAL COOLDOWN` and blocks edits with a `COOLDOWN LOCKED!` title.
* **Slot-Accurate Shulker Regear (`RegearManager`):**
  * Places `CYAN_SHULKER_BOX` (PDC `vcarter_regear_shulker`), replaces block with `AIR` next tick, and runs virtual slot re-mapping (`Hotbar 0–8 -> Offhand 40 -> Storage 9–35`). Preserves current durability/state of `regear.preserve-items` (`ELYTRA`, `FLINT_AND_STEEL`, `BOW`, `SWORDS`, armor slots `36–39`), forbids refilling swords/bows/fireworks/XP bottles, clones `regear.whitelist` items from snapshot, and clears floor junk if `clear-junk-in-empty-slots: true`.

## 4. CLASS & METHOD INDEX (`@src/...`)

### Core, Config & Utilities
* `@.gitignore` — Excludes Maven `target/`, compiled `.jar`/`.class`, IDE metadata, and OS artifacts from Git.
* `@.github/workflows/build.yml` — GitHub Actions CI building `target/VCarterUtils-*.jar` via `mvn clean package` on push/PR and publishing/updating GitHub Releases on `v*` tag pushes.
* `@CHANGELOG.md` — Release notes per version used by GitHub Actions release publishing.
* `@pom.xml` — Maven build config (Java 21, Paper 1.21.1 API, PlaceholderAPI provided).
* `@src/main/resources/plugin.yml` — Plugin metadata, commands, permissions, and soft-depends.
* `@src/main/resources/config.yml` — Centralized global theme (`theme.prefixes`, `theme.colors`), admin messages, fallback defaults.
* `@src/main/resources/*.yml` — Modular feature configurations: `regear.yml`, `training.yml`, `lobby.yml`, `combattag.yml`, `totem.yml`, `pearl.yml`, `mace.yml`, `xbow.yml`, `back.yml`, `score.yml`, `menu.yml`.
* `@src/main/java/me/nh4n/vcarterutils/VCarterUtils.java` — Main plugin lifecycle, `reloadPluginConfig()`, `playerdata.yml` sync lock, `formatMessage(String)`, `parseMessage(String)`, and manager/hook getters.
* `@src/main/java/me/nh4n/vcarterutils/config/ConfigManager.java` — Modular configuration manager handling loading, bidirectional section synchronization with main config, and auto-migration of legacy lobby items and typography (`migrateTypographyAndTheme()`).
* `@src/main/java/me/nh4n/vcarterutils/util/MessageUtil.java` — Centralized message formatter and parser replacing `%prefix_...%` (`info`, `blue`, `error`, `red`, `warning`, `purple`, `success`, `admin`, `combat`) and `%c_...%` (`primary`, `success`, `error`, `warning`, `purple`, `text`, `muted`) placeholders dynamically from `config.yml`.

### Hooks & Packet Filters (`hook/`)
* `@src/main/java/me/nh4n/vcarterutils/hook/ActionBarFilter.java` — Netty `ChannelDuplexHandler` (`vcarter_actionbar_filter`) blocking external `ClientboundSetActionBarTextPacket` / `ClientboundSystemChatPacket` (`overlay=true`) while 1v1 score action bar is active (`markScoreActionBar`).
* `@src/main/java/me/nh4n/vcarterutils/hook/FoCombatTagHook.java` — Reflection + PAPI `%focombattag_tagged%` bridge: `isTagged(Player)`, `untag(Player)`, `getRemainingSeconds(Player)`, and `CombatTagApplyEvent` listener.
* `@src/main/java/me/nh4n/vcarterutils/hook/NexoHook.java` — Reflection bridge to `NexoItems.itemFromId(id).build()`, listens to `NexoItemsLoadedEvent`, and strips `NamespacedKey("nexo", "id")` from cloned GUI/Hotbar items.
* `@src/main/java/me/nh4n/vcarterutils/hook/VCarterExpansion.java` — PlaceholderAPI expansion (`%vcarterutils_score_<target>%`, `pearl_cooldown`, `pearl_cooldown_personal`, `pearl_cooldown_remaining`, `mace_cooldown`, `mace_cooldown_personal`, `mace_cooldown_remaining`, `xbow_last_ticks`, `xbow_best_ticks`).

### Managers (`manager/`)
* `@src/main/java/me/nh4n/vcarterutils/manager/ScoreManager.java` — Tracks 1v1 kill/death scores, 60-min auto-reset task, `resetAll()`, and two-way reset requests (`accept`/`decline`).
* `@src/main/java/me/nh4n/vcarterutils/manager/RegearManager.java` — Manages 41-slot kit snapshots (`saveKitSnapshot`, stripping Regear/Lobby items), Shulker box item checks & cleanup (`isRegearItem`, `removeAllRegearItems`, `vcarter_regear_shulker`), cooldown resolution (`canRegear`, `performRegear`, `getDamageCooldownSec`, `getRegearCooldownSec`, `isCheckCombatTag`, bypassing FoCombatTag if `damage-cooldown-seconds <= 0` or `check-combat-tag: false`), and virtual slot re-mapping.
* `@src/main/java/me/nh4n/vcarterutils/manager/TotemManager.java` — Enforces `max-in-inventory` (`enforceInventoryLimit`, keeping Offhand 40 & Hotbar 0–8 first) and `max-activations-per-kit` (`resetActivations`), respecting `blacklisted-worlds` and bypassing enforcement if player is in Training Mode.
* `@src/main/java/me/nh4n/vcarterutils/manager/BackManager.java` — Stores death locations, falls back to `player.getLastDeathLocation()`, clamps Void Y (`minHeight + 2`), and executes `teleportAsync` with sync fallback.
* `@src/main/java/me/nh4n/vcarterutils/manager/PearlManager.java` — Resolves effective Ender Pearl cooldown (`getEffectiveCooldownSeconds`), per-world/global overrides, and personal settings in `playerdata.yml`.
* `@src/main/java/me/nh4n/vcarterutils/manager/MaceManager.java` — Resolves effective Mace cooldown (`getEffectiveCooldownSeconds`), smash-attack checks, and personal/forced overrides.
* `@src/main/java/me/nh4n/vcarterutils/manager/CombatTagManager.java` — Manages per-world combat tag duration (`0s` = disable, `>0s` = custom, `-1` = default), `endCombat(UUID)`, and global/per-world blocked commands.
* `@src/main/java/me/nh4n/vcarterutils/manager/SettingMenuManager.java` — Builds personal `/setting` GUI (27 slots), Admin World Manager GUI (54 slots, auto-scanning Bukkit/Multiverse/SlimeWorlds), `WorldDetailMenu` (27 slots), and `GlobalDetailMenu` (27 slots).
* `@src/main/java/me/nh4n/vcarterutils/manager/LobbyHotbarManager.java` — Saves/restores 9-slot player hotbars (`savedHotbars`), tracks `markRespawning(UUID)`, and applies Lobby Hotbar (`applyLobbyHotbar`, slots 0=`/rtpq`, 1=`/rtp`, 4=`/back`, 7=`/decor`, 8=`/settings`).
* `@src/main/java/me/nh4n/vcarterutils/manager/XbowManager.java` — Calculates Rail + TNT Minecart + Flint & Steel + Crossbow ignition speed (`totalTicks = Math.max(1, nowTick - setup.startTick())`), saves/loads personal best ticks (`bestXbowTicks` in `playerdata.yml`), and coordinates Action Bar output.
* `@src/main/java/me/nh4n/vcarterutils/manager/TrainingManager.java` — Manages Training Mode master switch, cached world blacklist (`isWorldBlacklisted`), 36-slot `/training` GUI states, 8 balanced training options, and auto-disable on entering real PvP.
* `@src/main/java/me/nh4n/vcarterutils/manager/TrainingOption.java` — Enum of 8 training options (God Mode, Auto Refill, Infinite Totem, Crossbow Auto Reload, Infinite Durability, Infinite Blocks & Rails, Infinite Consumables, Keep Inventory) with GUI slots, item text component sprite tags (`getSpriteTag()`), and descriptions.

### Listeners (`listener/`)
* `@src/main/java/me/nh4n/vcarterutils/listener/KillDeathListener.java` — Handles 15s 1v1 Score Action Bar, delays victim display until `PlayerRespawnEvent` (+7 ticks), and coordinates `isScoreActive(UUID)` with `ActionBarFilter`.
* `@src/main/java/me/nh4n/vcarterutils/listener/AutoPotListener.java` — Intercepts `PerPlayerKit` commands and `PlayerRespawnEvent` (5-tick delay pipeline) and strips infinite effects on `PlayerChangedWorldEvent`.
* `@src/main/java/me/nh4n/vcarterutils/listener/RegearListener.java` — Intercepts `/regear` & `/rg` (`LOWEST`), tracks `EntityDamageEvent` & `EntityDamageByEntityEvent`, checks `canRegear` on `BlockPlaceEvent` (`VCarter_Regear_Block`, cancelling placement early if in combat or on cooldown), handles next-tick air cleanup, consumes Regear Shulker on success, and refunds shulker if regear fails.
* `@src/main/java/me/nh4n/vcarterutils/listener/TotemListener.java` — Enforces totem pickup/inventory limits (`EntityPickupItemEvent`, inventory click/drag/close) and cancels excess `EntityResurrectEvent` (all bypassed when Training Mode is active).
* `@src/main/java/me/nh4n/vcarterutils/listener/BackListener.java` — Records death location on `PlayerDeathEvent`, untags player in `FoCombatTag`, and sends clickable `[HERE]` (`/back`) prompt 10 ticks after respawn.
* `@src/main/java/me/nh4n/vcarterutils/listener/PearlListener.java` — Applies custom `setCooldown(Material.ENDER_PEARL, ticks)` 1 tick after `ProjectileLaunchEvent`.
* `@src/main/java/me/nh4n/vcarterutils/listener/MaceListener.java` — Cancels or applies `Material.MACE` cooldown on `EntityDamageByEntityEvent` (`HIGHEST`, `ignoreCancelled = true`).
* `@src/main/java/me/nh4n/vcarterutils/listener/CombatTagListener.java` — Blocks restricted commands at `PlayerCommandPreprocessEvent` (`LOW`) while allowing `/back` variants when not in an active 1v1 duel; untags on world change to `0s` worlds.
* `@src/main/java/me/nh4n/vcarterutils/listener/SettingMenuListener.java` — Handles clicks, hotkeys (`1–3`, `Q`, `Shift+Q`, `F`), and lock warnings across Personal, Admin World Manager, World Detail, and Global Detail GUIs.
* `@src/main/java/me/nh4n/vcarterutils/listener/LobbyHotbarListener.java` — Handles right-click command execution via PDC `vcarter_lobby_cmd`, blocks item movement in lobby, and restores hotbar on world change or kit load.
* `@src/main/java/me/nh4n/vcarterutils/listener/XbowListener.java` — Tracks Rail placement (`lastRailPlacements`), TNT Minecart placement/stacking (`firstCartTick`, `lastCartTick`), Flint & Steel / Fire ignition, Crossbow shots (arrows/fireworks), under-cart rail impact detection, and detonation events to trigger 3s Xbow tick speed Action Bar with 5s periodic map cleanup (`startCleanupTask`).
* `@src/main/java/me/nh4n/vcarterutils/listener/AntiInterruptListener.java` — Prevents unwanted third-party interference during active fights/interactions.
* `@src/main/java/me/nh4n/vcarterutils/listener/TrainingListener.java` — Enforces God Mode damage/void/hunger immunity, suppresses self/pearl `CombatTagApplyEvent`, and handles infinite items/blocks/crossbow reload and auto-refill.

### Commands (`command/`)
* `@src/main/java/me/nh4n/vcarterutils/command/MainCommand.java` — `/vcarterutils` (`/vcu`, `/vcarter`): `reload`, `combat <world> <sec|off|disable>`, `blockedcommands`, `setlobby [world]`.
* `@src/main/java/me/nh4n/vcarterutils/command/SettingCommand.java` — `/setting` (`/settings`, `/options`, `/vsetting`): personal GUI, `pearl <s>`, `mace <s>`, `admin`, `admin global`, `admin world <w>`, `admin combat <w> <s|off|disable>`.
* `@src/main/java/me/nh4n/vcarterutils/command/ScoreCommand.java` — `/score` (`/sc`, `/vscore` + `LOWEST` preprocess hook): `reset <player>`, `accept <p>`, `decline <p>`, `resetall`.
* `@src/main/java/me/nh4n/vcarterutils/command/BackCommand.java` — `/back` (`/deathback`, `/return`): executes safe async death return and updates Lobby Hotbar.
* `@src/main/java/me/nh4n/vcarterutils/command/TrainingCommand.java` — `/training` (`/train`, `/vtraining`, `/vtrain`): `[toggle|on|off|gui]` Training Mode controls.

## 5. PDC KEYS & METADATA
* `NamespacedKey(plugin, "vcarter_regear_shulker")` (`BYTE`): Marks Regear Shulker box item in inventory.
* `NamespacedKey(plugin, "vcarter_lobby_cmd")` (`STRING`): Marks Lobby Hotbar item and stores right-click command.
* `NamespacedKey("nexo", "id")`: Removed from cloned GUI/Hotbar Nexo items to prevent Nexo from overwriting lore/names or placing furniture.
* Block Metadata `"VCarter_Regear_Block"`: Marks placed Regear Shulker block before next-tick removal.
