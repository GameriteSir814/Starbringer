package com.powerweapon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This does NOT reimplement size tracking - it reads and writes the exact
 * same "evolutionsmp:size" PersistentDataContainer key that the EvolutionSMP
 * plugin itself uses, so the two stay in sync without needing a compile-time
 * dependency on EvolutionSMP's code.
 *
 * A power weapon kill should set your size to an exact TOTAL multiplier of
 * what it was right before the kill (e.g. "quadruple" = totalMultiplier 4),
 * regardless of what growth-curve stage you're on. To do that reliably:
 *
 * 1. At LOWEST priority (before anyone else touches size), snapshot the
 *    player's size as it was BEFORE this kill.
 * 2. Let EvolutionSMP's own listener run normally in between - kills/hearts/
 *    growth-stage still advance normally, only the final size gets overridden.
 * 3. At MONITOR priority (after everyone else), overwrite size with
 *    (snapshot x totalMultiplier), ignoring whatever the normal curve set it to.
 */
public class DeathListener implements Listener {

    private static final NamespacedKey EVOLUTION_SIZE_KEY = NamespacedKey.fromString("evolutionsmp:size");

    private final PowerWeapon plugin;
    private final WeaponManager weaponManager;
    private final Map<UUID, Double> preKillSize = new HashMap<>();

    public DeathListener(PowerWeapon plugin, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeathCapture(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null || EVOLUTION_SIZE_KEY == null) return;
        if (!weaponManager.isPowerWeapon(killer.getInventory().getItemInMainHand())) return;

        Double currentSize = killer.getPersistentDataContainer().get(EVOLUTION_SIZE_KEY, PersistentDataType.DOUBLE);
        if (currentSize != null) {
            preKillSize.put(killer.getUniqueId(), currentSize);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathOverride(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        Double before = preKillSize.remove(killer.getUniqueId());
        if (before == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        double totalMultiplier = weaponManager.getTotalMultiplier(weapon);
        if (totalMultiplier <= 0) return;

        double maxSize = plugin.getConfig().getDouble("max-size", 4.0);
        double newSize = Math.min(before * totalMultiplier, maxSize);

        killer.getPersistentDataContainer().set(EVOLUTION_SIZE_KEY, PersistentDataType.DOUBLE, newSize);

        AttributeInstance attr = killer.getAttribute(Attribute.SCALE);
        if (attr != null) attr.setBaseValue(newSize);

        killer.sendMessage(Component.text("POWER KILL! ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Your weapon boosted your growth! New size: " + String.format("%.4f", newSize) + "x", NamedTextColor.GOLD)));
    }
}
