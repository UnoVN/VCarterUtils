package me.nh4n.vcarterutils.hook;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.listener.KillDeathListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActionBarFilter implements Listener {

    private static final String HANDLER_NAME = "vcarter_actionbar_filter";
    private final VCarterUtils plugin;
    private static Method systemChatOverlayMethod = null;
    private static Field systemChatOverlayField = null;

    // Track outgoing score action bar packet dispatches per player
    private final Map<UUID, ConcurrentLinkedQueue<Long>> pendingScorePackets = new ConcurrentHashMap<>();

    public ActionBarFilter(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) {
            injectPlayer(p);
        }
    }

    public void unregisterAll() {
        pendingScorePackets.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            uninjectPlayer(p);
        }
    }

    public void markScoreActionBar(UUID uuid) {
        if (uuid != null) {
            pendingScorePackets.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>()).add(System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        injectPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        uninjectPlayer(event.getPlayer());
    }

    public void injectPlayer(Player player) {
        if (!plugin.getConfig().getBoolean("score.action-bar.anti-flicker", true)) {
            return;
        }
        try {
            Channel channel = getChannel(player);
            if (channel == null || !channel.isOpen()) return;

            final UUID playerUuid = player.getUniqueId();
            channel.eventLoop().execute(() -> {
                try {
                    if (!channel.isOpen()) return;

                    if (channel.pipeline().get(HANDLER_NAME) != null) {
                        channel.pipeline().remove(HANDLER_NAME);
                    }

                    ChannelDuplexHandler handler = new ChannelDuplexHandler() {
                        @Override
                        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            if (isActionBarPacket(msg)) {
                                KillDeathListener kdl = plugin.getKillDeathListener();
                                if (kdl != null && kdl.isScoreActive(playerUuid)) {
                                    if (!isOurScoreActionBar(playerUuid, msg)) {
                                        // Drop foreign action bar to prevent flickering over 1v1 score counter
                                        promise.setSuccess();
                                        return;
                                    }
                                }
                            }
                            super.write(ctx, msg, promise);
                        }
                    };

                    if (channel.pipeline().get("packet_handler") != null) {
                        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                    } else {
                        channel.pipeline().addLast(HANDLER_NAME, handler);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public void uninjectPlayer(Player player) {
        try {
            pendingScorePackets.remove(player.getUniqueId());
            Channel channel = getChannel(player);
            if (channel != null && channel.isOpen()) {
                channel.eventLoop().execute(() -> {
                    try {
                        if (channel.pipeline().get(HANDLER_NAME) != null) {
                            channel.pipeline().remove(HANDLER_NAME);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isOurScoreActionBar(UUID playerUuid, Object msg) {
        ConcurrentLinkedQueue<Long> queue = pendingScorePackets.get(playerUuid);
        if (queue != null) {
            long now = System.currentTimeMillis();
            Long timestamp;
            while ((timestamp = queue.peek()) != null) {
                if (now - timestamp > 1000L) {
                    queue.poll();
                } else {
                    queue.poll();
                    return true;
                }
            }
        }

        // Secondary fallback check: inspect packet text for score format
        String text = extractPacketText(msg);
        return text != null && text.contains(" - ");
    }

    private String extractPacketText(Object msg) {
        if (msg == null) return "";
        try {
            for (Method m : msg.getClass().getMethods()) {
                if ((m.getName().equals("text") || m.getName().equals("content") || m.getName().equals("getText")) && m.getParameterCount() == 0) {
                    Object comp = m.invoke(msg);
                    if (comp != null) {
                        try {
                            Method getStringMethod = comp.getClass().getMethod("getString");
                            return (String) getStringMethod.invoke(comp);
                        } catch (Throwable ignored) {
                            return comp.toString();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return msg.toString();
    }

    private boolean isActionBarPacket(Object msg) {
        if (msg == null) return false;
        String simpleName = msg.getClass().getSimpleName();
        if (simpleName.equals("ClientboundSetActionBarTextPacket")
                || simpleName.equals("PacketPlayOutSetActionBarText")
                || simpleName.contains("ActionBar")) {
            return true;
        }

        if (simpleName.equals("ClientboundSystemChatPacket")) {
            try {
                if (systemChatOverlayMethod != null) {
                    return Boolean.TRUE.equals(systemChatOverlayMethod.invoke(msg));
                }
                if (systemChatOverlayField != null) {
                    return Boolean.TRUE.equals(systemChatOverlayField.get(msg));
                }
                // Record accessor overlay()
                for (Method m : msg.getClass().getMethods()) {
                    if (m.getName().equals("overlay") && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        systemChatOverlayMethod = m;
                        return Boolean.TRUE.equals(m.invoke(msg));
                    }
                }
                for (Field f : msg.getClass().getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        f.setAccessible(true);
                        systemChatOverlayField = f;
                        return f.getBoolean(msg);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private static Channel getChannel(Player player) {
        try {
            Method getHandleMethod = player.getClass().getMethod("getHandle");
            Object entityPlayer = getHandleMethod.invoke(player);

            Object packetListener = null;
            for (Field f : entityPlayer.getClass().getFields()) {
                if (f.getName().equals("connection") || f.getType().getSimpleName().contains("PacketListener")) {
                    packetListener = f.get(entityPlayer);
                    if (packetListener != null) break;
                }
            }
            if (packetListener == null) {
                for (Field f : entityPlayer.getClass().getDeclaredFields()) {
                    if (f.getName().equals("connection") || f.getType().getSimpleName().contains("PacketListener")) {
                        f.setAccessible(true);
                        packetListener = f.get(entityPlayer);
                        if (packetListener != null) break;
                    }
                }
            }

            if (packetListener == null) return null;

            Object connection = null;
            for (Field f : packetListener.getClass().getFields()) {
                if (f.getName().equals("connection") || f.getType().getSimpleName().contains("Connection")) {
                    connection = f.get(packetListener);
                    if (connection != null) break;
                }
            }
            if (connection == null) {
                for (Field f : packetListener.getClass().getDeclaredFields()) {
                    if (f.getName().equals("connection") || f.getType().getSimpleName().contains("Connection")) {
                        f.setAccessible(true);
                        connection = f.get(packetListener);
                        if (connection != null) break;
                    }
                }
            }

            if (connection == null) return null;

            for (Field f : connection.getClass().getFields()) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    return (Channel) f.get(connection);
                }
            }
            for (Field f : connection.getClass().getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (Channel) f.get(connection);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
