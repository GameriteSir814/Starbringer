package com.powerweapon;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class WeaponManager {

    private final PowerWeapon plugin;
    private final NamespacedKey multiplierKey;

    public WeaponManager(PowerWeapon plugin) {
        this.plugin = plugin;
        this.multiplierKey = new NamespacedKey(plugin, "total_multiplier");
    }

    /** Marks this ONE item. (Plain items have no meta yet, so we use getItemMeta() directly.) */
    public boolean markItem(ItemStack item, double totalMultiplier) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(multiplierKey, PersistentDataType.DOUBLE, totalMultiplier);
        item.setItemMeta(meta);
        return true;
    }

    public boolean unmarkItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(multiplierKey, PersistentDataType.DOUBLE)) return false;
        meta.getPersistentDataContainer().remove(multiplierKey);
        item.setItemMeta(meta);
        return true;
    }

    /** Multiplier for this exact item (tag first), else for its weapon TYPE (config), else -1. */
    public double getTotalMultiplier(ItemStack item) {
        if (item == null || item.getType().isAir()) return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Double tagged = meta.getPersistentDataContainer().get(multiplierKey, PersistentDataType.DOUBLE);
            if (tagged != null) return tagged;
        }
        return plugin.getConfig().getDouble("types." + item.getType().name(), -1);
    }

    public boolean isPowerWeapon(ItemStack item) {
        return getTotalMultiplier(item) > 0;
    }

    public void setTypeMultiplier(Material m, double mult) {
        plugin.getConfig().set("types." + m.name(), mult);
        plugin.saveConfig();
    }

    public boolean removeType(Material m) {
        if (!plugin.getConfig().isSet("types." + m.name())) return false;
        plugin.getConfig().set("types." + m.name(), null);
        plugin.saveConfig();
        return true;
    }
}
