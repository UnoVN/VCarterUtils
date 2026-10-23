package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.XbowManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class XbowListener implements Listener {

    private final VCarterUtils plugin;

    // Luu tick nguoi choi click dat TNT Minecart len Rail
    private final Map<UUID, Integer> lastCartClickTicks = new ConcurrentHashMap<>();
    // Luu setup cart hien tai cua nguoi choi (de neu dat nhieu cart cung 1 cho thi tinh tu cart dau tien)
    private final Map<UUID, CartSetup> activePlayerCartSetup = new ConcurrentHashMap<>();
    // Luu thong tin cua tung TNT Minecart: Map<CartUUID, CartSetup>
    private final Map<UUID, CartSetup> trackedCarts = new ConcurrentHashMap<>();
    // Luu tick nguoi choi dat Lua (Flint & Steel / Fire Charge)
    private final Map<UUID, Integer> lastFirePlacedTicks = new ConcurrentHashMap<>();
    // Luu mui ten ban ra tu Crossbow: Map<ArrowUUID, ArrowShot>
    private final Map<UUID, ArrowShot> crossbowArrows = new ConcurrentHashMap<>();
    // Luu tick ban Crossbow gan nhat cua nguoi choi
    private final Map<UUID, Integer> lastCrossbowShotTicks = new ConcurrentHashMap<>();

    public XbowListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    private boolean isRail(Material mat) {
        return mat == Material.RAIL
                || mat == Material.POWERED_RAIL
                || mat == Material.DETECTOR_RAIL
                || mat == Material.ACTIVATOR_RAIL;
    }

    /**
     * Khi nguoi choi dat Rail moi -> Reset setup cu de lan dat Cart tiep theo tinh tick moi.
     * Khi nguoi choi dat Lua -> Ghi nhan tick dat lua.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        XbowManager manager = plugin.getXbowManager();
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        Material type = event.getBlockPlaced().getType();
        int currentTick = Bukkit.getCurrentTick();

        if (isRail(type)) {
            activePlayerCartSetup.remove(player.getUniqueId());
            return;
        }

        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            lastFirePlacedTicks.put(player.getUniqueId(), currentTick);
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
     * BAT DAU DEM TICK: Tu luc nguoi choi dat TNT Minecart xuong Rail
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractRailWithCart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        Block clicked = event.getClickedBlock();
        if (item == null || item.getType() != Material.TNT_MINECART || clicked == null || !isRail(clicked.getType())) {
            return;
        }
        lastCartClickTicks.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
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

        UUID uuid = player.getUniqueId();
        int nowTick = Bukkit.getCurrentTick();

        // Neu nguoi choi dat nhieu cart lien tiep (< 10 ticks) tren cung 1 vi tri rail, giu tick cua cart dau tien
        CartSetup existing = activePlayerCartSetup.get(uuid);
        CartSetup setupToUse;
        if (existing != null
                && (nowTick - existing.cartTick()) <= 10
                && existing.cartLoc().getWorld() != null
                && existing.cartLoc().getWorld().equals(cart.getWorld())
                && existing.cartLoc().distanceSquared(cart.getLocation()) <= 2.25) {
            setupToUse = existing;
        } else {
            setupToUse = new CartSetup(uuid, nowTick, nowTick, cart.getLocation().clone());
            activePlayerCartSetup.put(uuid, setupToUse);
        }

        trackedCarts.put(cart.getUniqueId(), setupToUse);
    }

    /**
     * Ghi nhan mui ten ban ra tu Crossbow
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrossbowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.CROSSBOW) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;

        int nowTick = Bukkit.getCurrentTick();
        crossbowArrows.put(arrow.getUniqueId(), new ArrowShot(player.getUniqueId(), nowTick));
        lastCrossbowShotTicks.put(player.getUniqueId(), nowTick);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (main.getType() == Material.CROSSBOW || off.getType() == Material.CROSSBOW) {
            int nowTick = Bukkit.getCurrentTick();
            crossbowArrows.put(arrow.getUniqueId(), new ArrowShot(player.getUniqueId(), nowTick));
            lastCrossbowShotTicks.put(player.getUniqueId(), nowTick);
        }
    }

    /**
     * KET THUC DEM TICK: Khi mui ten tu Crossbow di qua lua va ban trung / kich no TNT Minecart
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHitCart(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(event.getHitEntity() instanceof ExplosiveMinecart cart)) return;

        ArrowShot shot = crossbowArrows.get(arrow.getUniqueId());
        if (shot == null) return;

        boolean arrowLit = arrow.getFireTicks() > 0 || hasFireBlockNearby(arrow.getLocation(), cart.getLocation());
        completeSetupIfValid(cart, shot.shooterUuid(), arrowLit);
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
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart cart)) return;
        CartSetup setup = trackedCarts.get(cart.getUniqueId());
        if (setup == null) return;

        Integer shotTick = lastCrossbowShotTicks.get(setup.ownerUuid());
        int nowTick = Bukkit.getCurrentTick();
        if (shotTick != null && shotTick >= setup.cartTick() && (nowTick - shotTick) <= 20) {
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
        if (shotTick != null && shotTick >= setup.cartTick() && (nowTick - shotTick) <= 20) {
            completeSetupIfValid(cart, setup.ownerUuid(), true);
        }
    }

    private void handleCartHitOrDestroy(ExplosiveMinecart cart, Entity attacker) {
        if (attacker instanceof AbstractArrow arrow) {
            ArrowShot shot = crossbowArrows.get(arrow.getUniqueId());
            if (shot != null) {
                boolean lit = arrow.getFireTicks() > 0 || hasFireBlockNearby(arrow.getLocation(), cart.getLocation());
                completeSetupIfValid(cart, shot.shooterUuid(), lit);
            }
        } else if (attacker instanceof Player player) {
            Integer shotTick = lastCrossbowShotTicks.get(player.getUniqueId());
            int nowTick = Bukkit.getCurrentTick();
            if (shotTick != null && (nowTick - shotTick) <= 15) {
                boolean lit = cart.getFireTicks() > 0 || isFireNear(cart.getLocation());
                completeSetupIfValid(cart, player.getUniqueId(), lit);
            }
        }
    }

    private void completeSetupIfValid(ExplosiveMinecart cart, UUID shooterUuid, boolean fireDetectedOnImpact) {
        XbowManager manager = plugin.getXbowManager();
        if (!manager.isEnabled()) return;

        CartSetup setup = trackedCarts.remove(cart.getUniqueId());
        if (setup == null || !setup.ownerUuid().equals(shooterUuid)) {
            return;
        }

        int nowTick = Bukkit.getCurrentTick();
        int maxTicks = manager.getMaxSetupTicks();
        int totalTicks = nowTick - setup.cartTick();

        if (totalTicks < 0 || totalTicks > maxTicks) {
            return;
        }

        Integer fireTick = lastFirePlacedTicks.get(shooterUuid);
        boolean placedFireRecently = (fireTick != null && fireTick >= setup.cartTick() - 40 && (nowTick - fireTick) <= maxTicks);

        if (!fireDetectedOnImpact && !placedFireRecently && !isFireNear(cart.getLocation())) {
            return;
        }

        // Xoa tat ca cart cung setupId de khong bao trung khi dat nhieu cart
        trackedCarts.entrySet().removeIf(e -> e.getValue().ownerUuid().equals(setup.ownerUuid())
                && e.getValue().setupId() == setup.setupId());
        activePlayerCartSetup.remove(setup.ownerUuid());

        Player player = Bukkit.getPlayer(setup.ownerUuid());
        if (player != null && player.isOnline()) {
            manager.recordAndDisplayXbow(player, Math.max(1, totalTicks));
        }
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        activePlayerCartSetup.remove(uuid);
        lastCartClickTicks.remove(uuid);
        lastFirePlacedTicks.remove(uuid);
        lastCrossbowShotTicks.remove(uuid);
        plugin.getXbowManager().stopActionBarTask(uuid);
    }

    private record CartSetup(UUID ownerUuid, int setupId, int cartTick, Location cartLoc) {
    }

    private record ArrowShot(UUID shooterUuid, int shotTick) {
    }
}
