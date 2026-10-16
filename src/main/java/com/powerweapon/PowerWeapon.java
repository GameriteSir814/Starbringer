package com.powerweapon;

import org.bukkit.plugin.java.JavaPlugin;

public class PowerWeapon extends JavaPlugin {

    private static PowerWeapon instance;
    private WeaponManager weaponManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        weaponManager = new WeaponManager(this);

        getServer().getPluginManager().registerEvents(new DeathListener(this, weaponManager), this);
        getServer().getPluginManager().registerEvents(new ShrinkMenu(this, weaponManager), this);
        getCommand("pw").setExecutor(new PowerWeaponCommand(weaponManager));

        getLogger().info("PowerWeapon enabled.");
    }

    public static PowerWeapon getInstance() { return instance; }
    public WeaponManager getWeaponManager() { return weaponManager; }
}
