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
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class XbowListener implements Listener {

    private final VCarterUtils plugin;

    // Luu tick dat Rail cua tung nguoi choi
    private final Map<UUID, RailPlacement> playerRailPlacements = new ConcurrentHashMap<>();
    // Luu tick nguoi choi click dat TNT Minecart len Rail
    private final Map<UUID, Integer> lastCartClickTicks = new ConcurrentHashMap<>();
    // Luu thong tin cua tung TNT Minecart: Map<CartUUID, CartSetup>
    private final Map<UUID, CartSetup> trackedCarts = new ConcurrentHashMap<>();
    // Luu tick nguoi choi dat Lua (Flint & Steel / Fire Charge)
    private final Map<UUID, Integer> lastFirePlacedTicks = new ConcurrentHashMap<>();
    // Luu mui ten ban ra tu Crossbow: Map<ArrowUUID, ArrowShot>
    private final Map<UUID, ArrowShot> crossbowArrows = new ConcurrentHashMap<>();

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
     * BUOC 1 & 3: Nguoi choi dat Rail hoac dat Lua xuong dat
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        XbowManager manager = plugin.getXbowManager();
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Material type = block.getType();
        int currentTick = Bukkit.getCurrentTick();

        if (isRail(type)) {
            playerRailPlacements.put(player.getUniqueId(), new RailPlacement(
                    player.getUniqueId(),
                    block.getLocation(),
                    currentTick,
                    currentTick
            ));
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
     * BUOC 2: Nguoi choi dat TNT Minecart len Rail
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
        int maxTicks = manager.getMaxSetupTicks();

        RailPlacement rail = playerRailPlacements.get(uuid);
        if (rail == null || (nowTick - rail.railTick()) > maxTicks) {
            return;
        }

        trackedCarts.put(cart.getUniqueId(), new CartSetup(uuid, rail.setupId(), rail.railTick(), nowTick));
    }

    /**
     * BUOC 4: Nguoi choi ban Crossbow
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrossbowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.CROSSBOW) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;

        int nowTick = Bukkit.getCurrentTick();
        crossbowArrows.put(arrow.getUniqueId(), new ArrowShot(player.getUniqueId(), nowTick));
    }

    /**
     * BUOC 5: Mui ten tu Crossbow bay qua lua va ban trung / lam no TNT Minecart
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHitCart(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(event.getHitEntity() instanceof ExplosiveMinecart cart)) return;

        tryCompleteXbow(arrow, cart);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getVehicle() instanceof ExplosiveMinecart cart)) return;
        if (!(event.getAttacker() instanceof AbstractArrow arrow)) return;

        tryCompleteXbow(arrow, cart);
    }

    private void tryCompleteXbow(AbstractArrow arrow, ExplosiveMinecart cart) {
        XbowManager manager = plugin.getXbowManager();
        if (!manager.isEnabled()) return;

        ArrowShot shot = crossbowArrows.get(arrow.getUniqueId());
        if (shot == null) return;

        CartSetup setup = trackedCarts.remove(cart.getUniqueId());
        if (setup == null || !setup.ownerUuid().equals(shot.shooterUuid())) {
            return;
        }

        int nowTick = Bukkit.getCurrentTick();
        int maxTicks = manager.getMaxSetupTicks();
        int totalTicks = nowTick - setup.railTick();

        if (totalTicks < 0 || totalTicks > maxTicks) {
            return;
        }

        // Kiem tra mui ten dang chay HOAC nguoi choi vua dat lua trong setup nay
        boolean arrowOnFire = arrow.getFireTicks() > 0;
        Integer fireTick = lastFirePlacedTicks.get(shot.shooterUuid());
        boolean placedFireInSetup = (fireTick != null && fireTick >= setup.railTick() - 20 && (nowTick - fireTick) <= maxTicks);

        if (!arrowOnFire && !placedFireInSetup && !hasFireBlockNearby(arrow.getLocation(), cart.getLocation())) {
            return;
        }

        // Xoa cac cart cung setupId de khong bi bao trung neu dat nhieu cart tren 1 rail
        trackedCarts.entrySet().removeIf(e -> e.getValue().ownerUuid().equals(setup.ownerUuid())
                && e.getValue().setupId() == setup.setupId());
        playerRailPlacements.remove(setup.ownerUuid());

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
        playerRailPlacements.remove(uuid);
        lastCartClickTicks.remove(uuid);
        lastFirePlacedTicks.remove(uuid);
        plugin.getXbowManager().stopActionBarTask(uuid);
    }

    private record RailPlacement(UUID ownerUuid, Location railLoc, int railTick, int setupId) {
    }

    private record CartSetup(UUID ownerUuid, int setupId, int railTick, int cartTick) {
    }

    private record ArrowShot(UUID shooterUuid, int shotTick) {
    }
}
