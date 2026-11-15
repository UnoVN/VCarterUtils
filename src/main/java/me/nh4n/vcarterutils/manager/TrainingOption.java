package me.nh4n.vcarterutils.manager;

import org.bukkit.Material;

public enum TrainingOption {
    GOD_MODE(
            "god-mode",
            "Invulnerability",
            "<white><sprite:items:item/golden_apple></white>",
            Material.GOLDEN_APPLE,
            10,
            "Take zero damage from falls, pearls, explosions, void, and all attacks."
    ),
    AUTO_REFILL(
            "auto-refill",
            "Auto Refill Hotbar",
            "<white><sprite:items:item/tnt_minecart></white>",
            Material.DISPENSER,
            12,
            "Auto-refills rails, TNT carts, projectiles, pearls, and items."
    ),
    INFINITE_TOTEM(
            "infinite-totem",
            "Infinite Totem",
            "<white><sprite:items:item/totem_of_undying></white>",
            Material.TOTEM_OF_UNDYING,
            14,
            "Auto-replenishes Totem of Undying on pop without losing it."
    ),
    CROSSBOW_RELOAD(
            "crossbow-reload",
            "Crossbow Auto Reload",
            "<white><sprite:items:item/crossbow_standby></white>",
            Material.CROSSBOW,
            16,
            "Crossbow never loses reload state and has infinite ammo."
    ),
    INFINITE_DURABILITY(
            "infinite-durability",
            "Infinite Durability",
            "<white><sprite:items:item/diamond_chestplate></white>",
            Material.DIAMOND_CHESTPLATE,
            20,
            "Weapons, armor, tools, and shields never lose durability."
    ),
    INFINITE_BLOCKS(
            "infinite-blocks",
            "Infinite Blocks & Rails",
            "<white><sprite:items:item/rail></white>",
            Material.OBSIDIAN,
            22,
            "Placing blocks and rails never depletes items from your hand."
    ),
    INFINITE_CONSUMABLES(
            "infinite-consumables",
            "Infinite Consumables",
            "<white><sprite:items:item/golden_apple></white>",
            Material.ENCHANTED_GOLDEN_APPLE,
            24,
            "Food, golden apples, and potions never deplete or leave trash."
    ),
    KEEP_INVENTORY(
            "keep-inventory",
            "Keep Inventory",
            "<white><sprite:items:item/recovery_compass_16></white>",
            Material.CHEST,
            31,
            "Keep inventory and experience on death while in training."
    );

    private final String key;
    private final String displayName;
    private final String spriteTag;
    private final Material icon;
    private final int slot;
    private final String description;

    TrainingOption(String key, String displayName, String spriteTag, Material icon, int slot, String description) {
        this.key = key;
        this.displayName = displayName;
        this.spriteTag = spriteTag;
        this.icon = icon;
        this.slot = slot;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSpriteTag() {
        return spriteTag;
    }

    public Material getIcon() {
        return icon;
    }

    public int getSlot() {
        return slot;
    }

    public String getDescription() {
        return description;
    }

    public static TrainingOption fromKey(String key) {
        if (key == null) return null;
        for (TrainingOption opt : values()) {
            if (opt.key.equalsIgnoreCase(key) || opt.name().equalsIgnoreCase(key)) {
                return opt;
            }
        }
        return null;
    }

    public static TrainingOption fromSlot(int slot) {
        for (TrainingOption opt : values()) {
            if (opt.slot == slot) {
                return opt;
            }
        }
        return null;
    }
}
