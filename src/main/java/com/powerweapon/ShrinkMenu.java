package com.powerweapon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Left-click (air/block) while holding a power weapon opens a number-key menu.
 * Press 1-9 to shrink to that size. Each pick costs hearts of CURRENT health.
 * The weapon never deals damage; hitting something also just opens the menu.
 */
public class ShrinkMenu implements Listener {

    private static final NamespacedKey SIZE_KEY = NamespacedKey.fromString("evolutionsmp:size");
    private static final double[] DEFAULT_SIZES = {4, 3, 2, 1.5, 1, 0.75, 0.5, 0.25, 0.125, 0.0625};

    private static class Menu {
        final List<Double> options;
        long expires;
        Menu(List<Double> o, long e) { options = o; expires = e; }
    }

    private final PowerWeapon plugin;
    private final WeaponManager wm;
    private final Map<UUID, Menu> open = new HashMap<>();

    public ShrinkMenu(PowerWeapon plugin, WeaponManager wm) {
        this.plugin = plugin;
        this.wm = wm;
        new BukkitRunnable() {
            @Override public void run() { refresh(); }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private double currentSize(Player p) {
        Double d = p.getPersistentDataContainer().get(SIZE_KEY, PersistentDataType.DOUBLE);
        if (d != null) return d;
        AttributeInstance a = p.getAttribute(Attribute.SCALE);
        return a != null ? a.getBaseValue() : 1.0;
    }

    private List<Double> buildOptions(double current) {
        List<Double> cfg = plugin.getConfig().getDoubleList("size-options");
        List<Double> all = new ArrayList<>();
        if (cfg.isEmpty()) for (double d : DEFAULT_SIZES) all.add(d); else all.addAll(cfg);
        all.sort(Comparator.reverseOrder());
        List<Double> out = new ArrayList<>();
        for (double d : all) {
            if (d < current - 1e-6) out.add(d);
            if (out.size() == 9) break;
        }
        return out;
    }

    private static String fmt(double d) {
        return (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private void show(Player p, Menu m) {
        int cost = plugin.getConfig().getInt("heart-cost", 1);
        p.sendMessage(Component.text("--- Shrink menu (press a number key) --- cost: " + cost + " heart(s) each", NamedTextColor.GOLD));
        for (int i = 0; i < m.options.size(); i++) {
            p.sendMessage(Component.text("  [" + (i + 1) + "] ", NamedTextColor.YELLOW)
                    .append(Component.text(fmt(m.options.get(i)) + "x", NamedTextColor.WHITE)));
        }
        p.sendMessage(Component.text("Left-click again to close.", NamedTextColor.GRAY));
    }

    private void close(Player p, String why) {
        if (open.remove(p.getUniqueId()) != null && why != null && p.isOnline())
            p.sendActionBar(Component.text(why, NamedTextColor.GRAY));
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(open.keySet())) {
            Player p = Bukkit.getPlayer(id);
            Menu m = open.get(id);
            if (p == null || m == null) { open.remove(id); continue; }
            if (now > m.expires) { close(p, "Shrink menu closed."); continue; }
            if (!wm.isPowerWeapon(p.getInventory().getItemInMainHand())) { close(p, "Shrink menu closed."); continue; }
            StringBuilder sb = new StringBuilder("Shrink to: ");
            for (int i = 0; i < m.options.size(); i++) sb.append('[').append(i + 1).append("] ").append(fmt(m.options.get(i))).append("x  ");
            p.sendActionBar(Component.text(sb.toString().trim(), NamedTextColor.GOLD));
        }
    }

    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!wm.isPowerWeapon(p.getInventory().getItemInMainHand())) return;

        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);

        toggle(p);
    }

    private void toggle(Player p) {
        if (open.containsKey(p.getUniqueId())) { close(p, "Shrink menu closed."); return; }

        List<Double> opts = buildOptions(currentSize(p));
        if (opts.isEmpty()) { p.sendMessage(Component.text("You're already at the smallest size.", NamedTextColor.RED)); return; }
        Menu m = new Menu(opts, System.currentTimeMillis() + plugin.getConfig().getInt("menu-seconds", 15) * 1000L);
        open.put(p.getUniqueId(), m);
        show(p, m);
    }

    /** This weapon never hurts anything: hitting a mob/player just opens the menu instead. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!wm.isPowerWeapon(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        toggle(p);
    }

    @EventHandler
    public void onNumberKey(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        Menu m = open.get(p.getUniqueId());
        if (m == null) return;
        e.setCancelled(true); // keep the weapon in hand

        int idx = e.getNewSlot();
        if (idx >= m.options.size()) { p.sendActionBar(Component.text("No option " + (idx + 1), NamedTextColor.RED)); return; }

        double target = m.options.get(idx);
        double now = currentSize(p);
        if (target >= now - 1e-6) { close(p, null); return; }

        double cost = plugin.getConfig().getInt("heart-cost", 1) * 2.0;
        if (p.getHealth() <= cost) {
            p.sendMessage(Component.text("Not enough health to shrink (needs more than " + (int) (cost / 2) + " heart(s)).", NamedTextColor.RED));
            return;
        }

        p.setHealth(p.getHealth() - cost);
        p.getPersistentDataContainer().set(SIZE_KEY, PersistentDataType.DOUBLE, target);
        AttributeInstance a = p.getAttribute(Attribute.SCALE);
        if (a != null) a.setBaseValue(target);
        p.sendMessage(Component.text("Shrunk to " + fmt(target) + "x (-" + (int) (cost / 2) + " heart).", NamedTextColor.GREEN));

        // refresh the list so only still-lower sizes remain
        List<Double> opts = buildOptions(target);
        if (opts.isEmpty()) { close(p, "Smallest size reached."); return; }
        m.options.clear(); m.options.addAll(opts);
        m.expires = System.currentTimeMillis() + plugin.getConfig().getInt("menu-seconds", 15) * 1000L;
        show(p, m);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { open.remove(e.getPlayer().getUniqueId()); }
}
