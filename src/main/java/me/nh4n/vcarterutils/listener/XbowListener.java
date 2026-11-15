package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.XbowManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class XbowListener implements Listener {

    private final VCarterUtils plugin;

    // Stores tick & location when player places a Rail to start an Xbow setup
    private final Map<UUID, RailPlacement> lastRailPlacements = new ConcurrentHashMap<>();
    // Stores tick when player right-clicks TNT Minecart onto a Rail
    private final Map<UUID, Integer> lastCartClickTicks = new ConcurrentHashMap<>();
    // Stores current active cart setup of the player (retains startTick of Rail / first Cart)
    private final Map<UUID, CartSetup> activePlayerCartSetup = new ConcurrentHashMap<>();
    // Tracks individual TNT Minecarts: Map<CartUUID, CartSetup>
    private final Map<UUID, CartSetup> trackedCarts = new ConcurrentHashMap<>();
    // Stores tick when player used Flint & Steel / Fire Charge / placed Fire
    private final Map<UUID, Integer> lastFirePlacedTicks = new ConcurrentHashMap<>();
    // Tracks arrows/projectiles fired from Crossbows: Map<ProjectileUUID, ArrowShot>
    private final Map<UUID, ArrowShot> crossbowArrows = new ConcurrentHashMap<>();
    // Stores tick of the player's most recent Crossbow shot
    private final Map<UUID, Integer> lastCrossbowShotTicks = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public XbowListener(VCarterUtils plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private boolean isRail(Material mat) {
        return mat == Material.RAIL
                || mat == Material.POWERED_RAIL
                || mat == Material.DETECTOR_RAIL
                || mat == Material.ACTIVATOR_RAIL;
    }

    private boolean isFireIgniter(Material mat) {
        return mat == Material.FLINT_AND_STEEL || mat == Material.FIRE_CHARGE;
    }

    /**
     * 1. When player places a Rail: Record rail placement tick & location as the start of Rail -> Cart -> Flint -> Crossbow.
     * 2. When player places Fire: Record the ignition tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        XbowManager manager = plugin.getXbowManager();
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Material type = event.getBlockPlaced().getType();
        int currentTick = Bukkit.getCurrentTick();

        if (isRail(type)) {
            Location railLoc = event.getBlockPlaced().getLocation();
            RailPlacement existingRail = lastRailPlacements.get(uuid);
            CartSetup existingSetup = activePlayerCartSetup.get(uuid);

            // If player already placed a cart on an older rail, placing a new rail resets for a new setup
            if (existingSetup != null && (currentTick - existingSetup.lastCartTick()) > 2) {
                activePlayerCartSetup.remove(uuid);
            }

            // Keep first rail tick only if double-placing rails rapidly (<= 5 ticks) right next to each other before cart
            if (existingRail == null
                    || existingSetup != null
                    || (currentTick - existingRail.railTick()) > 5
                    || existingRail.railLoc().getWorld() == null
                    || !existingRail.railLoc().getWorld().equals(railLoc.getWorld())
                    || existingRail.railLoc().distanceSquared(railLoc) > 4.0) {
                lastRailPlacements.put(uuid, new RailPlacement(currentTick, railLoc.clone()));
            }
            return;
        }

        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            lastFirePlacedTicks.put(uuid, currentTick);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null && plugin.getXbowManager().isEnabled()) {
            lastFirePlacedTicks.put(player.getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    /**
     * Tracks right-click interactions with TNT Minecart on Rail and Flint & Steel / Fire Charge.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (!plugin.getXbowManager().isEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        int nowTick = Bukkit.getCurrentTick();

        ItemStack item = event.getItem();
        Material mainMat = player.getInventory().getItemInMainHand().getType();
        Material offMat = player.getInventory().getItemInOffHand().getType();

        if ((item != null && isFireIgniter(item.getType())) || isFireIgniter(mainMat) || isFireIgniter(offMat)) {
            lastFirePlacedTicks.put(uuid, nowTick);
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (item != null && item.getType() == Material.TNT_MINECART && clicked != null && isRail(clicked.getType())) {
                lastCartClickTicks.put(uuid, nowTick);
            }
        }
    }

    /**
     * Catches Flint & Steel or stacked TNT Minecart clicks when the player's crosshair hits the Minecart entity hitbox.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getXbowManager().isEnabled()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        int nowTick = Bukkit.getCurrentTick();

        Material mainMat = player.getInventory().getItemInMainHand().getType();
        Material offMat = player.getInventory().getItemInOffHand().getType();

        if (isFireIgniter(mainMat) || isFireIgniter(offMat)) {
            lastFirePlacedTicks.put(uuid, nowTick);
        } else if (mainMat == Material.TNT_MINECART || offMat == Material.TNT_MINECART) {
            lastCartClickTicks.put(uuid, nowTick);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart cart)) return;
        Player player = event.getPlayer();
        if (player == null) return;

        registerCartForPlayer(player, cart);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (!(event.getVehicle() instanceof ExplosiveMinecart cart)) return;
        if (trackedCarts.containsKey(cart.getUniqueId())) return;

        int nowTick = Bukkit.getCurrentTick();
        Location cartLoc = cart.getLocation();

        for (Map.Entry<UUID, Integer> entry : lastCartClickTicks.entrySet()) {
            if (nowTick - entry.getValue() <= 2) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.getWorld().equals(cartLoc.getWorld()) && p.getLocation().distanceSquared(cartLoc) <= 64.0) {
                    registerCartForPlayer(p, cart);
                    break;
                }
            }
        }
    }

    private void registerCartForPlayer(Player player, ExplosiveMinecart cart) {
        XbowManager manager = plugin.getXbowManager();
        if (!manager.isEnabled()) return;
        if (trackedCarts.containsKey(cart.getUniqueId())) return;

        UUID uuid = player.getUniqueId();
        int nowTick = Bukkit.getCurrentTick();
        Integer clickTick = lastCartClickTicks.get(uuid);
        int cartTick = (clickTick != null && nowTick - clickTick <= 2) ? clickTick : nowTick;
        Location cartLoc = cart.getLocation();

        // Check if player is stacking multiple carts in the same active setup (retain original startTick)
        int maxTicks = manager.getMaxSetupTicks();
        CartSetup existing = activePlayerCartSetup.get(uuid);
        Integer lastShot = lastCrossbowShotTicks.get(uuid);
        boolean shotAlreadyFired = (lastShot != null && existing != null && lastShot >= existing.firstCartTick());

        CartSetup setupToUse;
        if (existing != null
                && !shotAlreadyFired
                && (nowTick - existing.lastCartTick()) <= 30
                && (nowTick - existing.startTick()) <= maxTicks
                && existing.cartLoc().getWorld() != null
                && existing.cartLoc().getWorld().equals(cart.getWorld())
                && existing.cartLoc().distanceSquared(cartLoc) <= 16.0) {
            setupToUse = new CartSetup(existing.ownerUuid(), existing.setupId(), existing.startTick(), existing.firstCartTick(), nowTick, existing.cartLoc());
            activePlayerCartSetup.put(uuid, setupToUse);
        } else {
            // Determine startTick: if player placed the Rail just before placing this Cart, count from Rail placement tick!
            int startTick = cartTick;
            RailPlacement rail = lastRailPlacements.get(uuid);
            if (rail != null
                    && cartTick >= rail.railTick()
                    && (cartTick - rail.railTick()) <= maxTicks
                    && rail.railLoc().getWorld() != null
                    && rail.railLoc().getWorld().equals(cart.getWorld())
                    && rail.railLoc().distanceSquared(cartLoc) <= 25.0) {
                startTick = rail.railTick();
            }

            setupToUse = new CartSetup(uuid, nowTick, startTick, cartTick, nowTick, cartLoc.clone());
            activePlayerCartSetup.put(uuid, setupToUse);
        }

        trackedCarts.put(cart.getUniqueId(), setupToUse);
        if (trackedCarts.size() > 128) {
            trackedCarts.entrySet().removeIf(e -> (nowTick - e.getValue().startTick()) > maxTicks);
        }
        if (crossbowArrows.size() > 128) {
            crossbowArrows.entrySet().removeIf(e -> (nowTick - e.getValue().shotTick()) > maxTicks);
        }
    }

    /**
     * Records projectiles fired from a Crossbow.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrossbowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.CROSSBOW) return;
        if (!(event.getProjectile() instanceof Projectile proj)) return;

        int nowTick = Bukkit.getCurrentTick();
        crossbowArrows.put(proj.getUniqueId(), new ArrowShot(player.getUniqueId(), nowTick, player.getEyeLocation().clone()));
        lastCrossbowShotTicks.put(player.getUniqueId(), nowTick);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile proj)) return;
        if (!(proj.getShooter() instanceof Player player)) return;

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (main.getType() == Material.CROSSBOW || off.getType() == Material.CROSSBOW) {
            int nowTick = Bukkit.getCurrentTick();
            crossbowArrows.put(proj.getUniqueId(), new ArrowShot(player.getUniqueId(), nowTick, player.getEyeLocation().clone()));
            lastCrossbowShotTicks.put(player.getUniqueId(), nowTick);
        }
    }

    /**
     * END TICK COUNTER: When the projectile from the Crossbow hits / ignites the TNT Minecart.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHitCart(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        ExplosiveMinecart cart = null;
        if (event.getHitEntity() instanceof ExplosiveMinecart em) {
            cart = em;
        } else if (event.getHitBlock() != null) {
            // Check if projectile hit the rail or block right under/beside an ExplosiveMinecart
            Location hitLoc = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
            if (hitLoc.getWorld() != null) {
                for (Entity nearby : hitLoc.getWorld().getNearbyEntities(hitLoc, 1.8, 1.8, 1.8)) {
                    if (nearby instanceof ExplosiveMinecart em && trackedCarts.containsKey(em.getUniqueId())) {
                        cart = em;
                        break;
                    }
                }
            }
        }

        if (cart == null) {
            crossbowArrows.remove(proj.getUniqueId());
            return;
        }

        ArrowShot shot = resolveArrowShot(proj);
        if (shot == null) return;

        boolean lit = proj.getFireTicks() > 0
                || cart.getFireTicks() > 0
                || (proj instanceof Firework)
                || hasFireBlockNearby(proj.getLocation(), cart.getLocation())
                || hasFireAlongRay(shot.originLoc(), cart.getLocation());
        completeSetupIfValid(cart, shot.shooterUuid(), lit);
        crossbowArrows.remove(proj.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getVehicle() instanceof ExplosiveMinecart cart)) return;
        handleCartHitOrDestroy(cart, event.getAttacker());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof ExplosiveMinecart cart)) return;
        handleCartHitOrDestroy(cart, event.getAttacker());
        trackedCarts.remove(cart.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart cart)) return;
        CartSetup setup = trackedCarts.get(cart.getUniqueId());
        if (setup == null) return;

        Integer shotTick = lastCrossbowShotTicks.get(setup.ownerUuid());
        int nowTick = Bukkit.getCurrentTick();
        if (shotTick != null && shotTick >= (setup.firstCartTick() - 2) && (nowTick - shotTick) <= 30) {
            completeSetupIfValid(cart, setup.ownerUuid(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart cart)) return;
        CartSetup setup = trackedCarts.get(cart.getUniqueId());
        if (setup == null) return;

        Integer shotTick = lastCrossbowShotTicks.get(setup.ownerUuid());
        int nowTick = Bukkit.getCurrentTick();
        if (shotTick != null && shotTick >= (setup.firstCartTick() - 2) && (nowTick - shotTick) <= 30) {
            completeSetupIfValid(cart, setup.ownerUuid(), true);
        }
        trackedCarts.remove(cart.getUniqueId());
    }

    private ArrowShot resolveArrowShot(Projectile proj) {
        ArrowShot shot = crossbowArrows.get(proj.getUniqueId());
        if (shot != null) {
            return shot;
        }
        if (proj.getShooter() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            Integer lastShot = lastCrossbowShotTicks.get(uuid);
            int nowTick = Bukkit.getCurrentTick();
            if (lastShot != null && (nowTick - lastShot) <= 40) {
                return new ArrowShot(uuid, lastShot, player.getEyeLocation().clone());
            }
        }
        return null;
    }

    private void handleCartHitOrDestroy(ExplosiveMinecart cart, Entity attacker) {
        if (attacker instanceof Projectile proj) {
            ArrowShot shot = resolveArrowShot(proj);
            if (shot != null) {
                boolean lit = proj.getFireTicks() > 0
                        || cart.getFireTicks() > 0
                        || (proj instanceof Firework)
                        || hasFireBlockNearby(proj.getLocation(), cart.getLocation())
                        || hasFireAlongRay(shot.originLoc(), cart.getLocation());
                completeSetupIfValid(cart, shot.shooterUuid(), lit);
            }
        } else if (attacker instanceof Player player) {
            CartSetup setup = trackedCarts.get(cart.getUniqueId());
            if (setup == null) return;
            Integer shotTick = lastCrossbowShotTicks.get(player.getUniqueId());
            int nowTick = Bukkit.getCurrentTick();
            if (shotTick != null && shotTick >= (setup.firstCartTick() - 2) && (nowTick - shotTick) <= 15) {
                boolean lit = cart.getFireTicks() > 0
                        || isFireNear(cart.getLocation())
                        || hasFireAlongRay(player.getEyeLocation(), cart.getLocation());
                completeSetupIfValid(cart, player.getUniqueId(), lit);
            }
        }
    }

    private void completeSetupIfValid(ExplosiveMinecart cart, UUID shooterUuid, boolean fireDetectedOnImpact) {
        XbowManager manager = plugin.getXbowManager();
        if (!manager.isEnabled()) return;

        CartSetup setup = trackedCarts.get(cart.getUniqueId());
        if (setup == null || !setup.ownerUuid().equals(shooterUuid)) {
            return;
        }

        int nowTick = Bukkit.getCurrentTick();
        int maxTicks = manager.getMaxSetupTicks();
        int totalTicks = nowTick - setup.startTick();

        if (totalTicks < 0 || totalTicks > maxTicks) {
            trackedCarts.remove(cart.getUniqueId());
            return;
        }

        Integer shotTick = lastCrossbowShotTicks.get(shooterUuid);
        if (shotTick == null || shotTick < (setup.firstCartTick() - 2)) {
            return;
        }

        Integer fireTick = lastFirePlacedTicks.get(shooterUuid);
        boolean placedFireRecently = (fireTick != null && fireTick >= setup.startTick() - 20 && (nowTick - fireTick) <= maxTicks);

        if (!fireDetectedOnImpact && !placedFireRecently && cart.getFireTicks() <= 0 && !isFireNear(cart.getLocation())) {
            return;
        }

        // Validated! Remove all carts sharing the same setupId to prevent duplicate triggers
        trackedCarts.remove(cart.getUniqueId());
        trackedCarts.entrySet().removeIf(e -> e.getValue().ownerUuid().equals(setup.ownerUuid())
                && e.getValue().setupId() == setup.setupId());
        activePlayerCartSetup.remove(setup.ownerUuid());
        lastRailPlacements.remove(setup.ownerUuid());

        Player player = Bukkit.getPlayer(setup.ownerUuid());
        if (player != null && player.isOnline()) {
            manager.recordAndDisplayXbow(player, Math.max(1, totalTicks));
        }
    }

    private boolean hasFireAlongRay(Location start, Location end) {
        if (start == null || end == null || start.getWorld() == null || !start.getWorld().equals(end.getWorld())) {
            return false;
        }
        double dist = start.distance(end);
        if (dist <= 0.01 || dist > 12.0) {
            return isFireNear(start) || isFireNear(end);
        }
        Vector dir = end.toVector().subtract(start.toVector()).normalize();
        Location cursor = start.clone();
        int steps = (int) Math.ceil(dist * 2.0);
        for (int i = 0; i <= steps; i++) {
            Material m = cursor.getBlock().getType();
            if (m == Material.FIRE || m == Material.SOUL_FIRE) {
                return true;
            }
            cursor.add(dir.clone().multiply(0.5));
        }
        return false;
    }

    private boolean hasFireBlockNearby(Location loc1, Location loc2) {
        return isFireNear(loc1) || isFireNear(loc2);
    }

    private boolean isFireNear(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Block center = loc.getBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Material m = center.getRelative(dx, dy, dz).getType();
                    if (m == Material.FIRE || m == Material.SOUL_FIRE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            int maxTicks = plugin.getXbowManager().getMaxSetupTicks();
            trackedCarts.entrySet().removeIf(e -> (now - e.getValue().startTick()) > maxTicks);
            crossbowArrows.entrySet().removeIf(e -> (now - e.getValue().shotTick()) > maxTicks);
            lastRailPlacements.entrySet().removeIf(e -> (now - e.getValue().railTick()) > maxTicks);
            activePlayerCartSetup.entrySet().removeIf(e -> (now - e.getValue().lastCartTick()) > maxTicks);
            lastCartClickTicks.entrySet().removeIf(e -> (now - e.getValue()) > 40);
            lastFirePlacedTicks.entrySet().removeIf(e -> (now - e.getValue()) > maxTicks);
            lastCrossbowShotTicks.entrySet().removeIf(e -> (now - e.getValue()) > maxTicks);
        }, 100L, 100L);
    }

    public void clearAll() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        lastRailPlacements.clear();
        lastCartClickTicks.clear();
        activePlayerCartSetup.clear();
        trackedCarts.clear();
        lastFirePlacedTicks.clear();
        crossbowArrows.clear();
        lastCrossbowShotTicks.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastRailPlacements.remove(uuid);
        activePlayerCartSetup.remove(uuid);
        lastCartClickTicks.remove(uuid);
        lastFirePlacedTicks.remove(uuid);
        lastCrossbowShotTicks.remove(uuid);
        trackedCarts.entrySet().removeIf(e -> e.getValue().ownerUuid().equals(uuid));
        crossbowArrows.entrySet().removeIf(e -> e.getValue().shooterUuid().equals(uuid));
        plugin.getXbowManager().stopActionBarTask(uuid);
    }

    private record RailPlacement(int railTick, Location railLoc) {
    }

    private record CartSetup(UUID ownerUuid, int setupId, int startTick, int firstCartTick, int lastCartTick, Location cartLoc) {
    }

    private record ArrowShot(UUID shooterUuid, int shotTick, Location originLoc) {
    }
}
