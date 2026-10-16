package com.powerweapon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PowerWeaponCommand implements TabExecutor {

    private final WeaponManager wm;

    public PowerWeaponCommand(WeaponManager wm) { this.wm = wm; }

    private void msg(Player p, String s, NamedTextColor c) { p.sendMessage(Component.text(s, c)); }

    private void usage(Player p) {
        msg(p, "/pw add <multiplier>            - mark the item in your hand", NamedTextColor.RED);
        msg(p, "/pw remove                      - unmark the item in your hand", NamedTextColor.RED);
        msg(p, "/pw type <material> <multiplier> - ALL items of that type (e.g. mace 4)", NamedTextColor.RED);
        msg(p, "/pw untype <material>           - undo a type rule", NamedTextColor.RED);
        msg(p, "/pw list                        - show type rules", NamedTextColor.RED);
    }

    private double parse(Player p, String s) {
        try {
            double d = Double.parseDouble(s);
            if (d <= 1.0) { msg(p, "Multiplier must be greater than 1.", NamedTextColor.RED); return -1; }
            return d;
        } catch (NumberFormatException e) { msg(p, "Invalid number.", NamedTextColor.RED); return -1; }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (!p.isOp()) { msg(p, "Only operators can do this.", NamedTextColor.RED); return true; }
        if (a.length < 1) { usage(p); return true; }

        switch (a[0].toLowerCase()) {
            case "add" -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { msg(p, "Hold the weapon first.", NamedTextColor.RED); return true; }
                if (a.length < 2) { usage(p); return true; }
                double m = parse(p, a[1]); if (m < 0) return true;
                if (!wm.markItem(hand, m)) { msg(p, "Couldn't mark that item.", NamedTextColor.RED); return true; }
                p.getInventory().setItemInMainHand(hand);
                msg(p, "This " + hand.getType().name() + " now multiplies your size by " + m + "x on a kill.", NamedTextColor.GREEN);
            }
            case "remove" -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (wm.unmarkItem(hand)) {
                    p.getInventory().setItemInMainHand(hand);
                    msg(p, "Item unmarked.", NamedTextColor.YELLOW);
                } else msg(p, "This item isn't individually marked.", NamedTextColor.RED);
            }
            case "type" -> {
                if (a.length < 3) { usage(p); return true; }
                Material mat = Material.matchMaterial(a[1]);
                if (mat == null || mat.isAir()) { msg(p, "Unknown material '" + a[1] + "'.", NamedTextColor.RED); return true; }
                double m = parse(p, a[2]); if (m < 0) return true;
                wm.setTypeMultiplier(mat, m);
                msg(p, "Every " + mat.name() + " now multiplies your size by " + m + "x on a kill.", NamedTextColor.GREEN);
            }
            case "untype" -> {
                if (a.length < 2) { usage(p); return true; }
                Material mat = Material.matchMaterial(a[1]);
                if (mat != null && wm.removeType(mat)) msg(p, "Type rule removed.", NamedTextColor.YELLOW);
                else msg(p, "No rule for that type.", NamedTextColor.RED);
            }
            case "list" -> {
                var sec = PowerWeapon.getInstance().getConfig().getConfigurationSection("types");
                if (sec == null || sec.getKeys(false).isEmpty()) msg(p, "No type rules.", NamedTextColor.YELLOW);
                else for (String k : sec.getKeys(false)) msg(p, k + " = " + sec.getDouble(k) + "x", NamedTextColor.AQUA);
            }
            default -> usage(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("add", "remove", "type", "untype", "list"));
        else if (a.length == 2 && (a[0].equalsIgnoreCase("type") || a[0].equalsIgnoreCase("untype"))) {
            for (Material m : Material.values()) if (!m.isLegacy() && m.isItem()) out.add(m.name().toLowerCase());
        }
        String pre = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.startsWith(pre));
        return out;
    }
}
