package com.decacagle;

import org.bukkit.plugin.java.JavaPlugin;

public class DecaDB extends JavaPlugin {

    private APIManager httpServer = null;

    @Override
    public void onEnable() {
        getLogger().info("DecaDB v1.0 launched successfully!");

        if (httpServer == null) {
            httpServer = new APIManager(getLogger(), getServer().getWorld("world"), this);
        }

    }

    @Override
    public void onDisable() {
        getLogger().info("DecaDB v1.0 disabled successfully!");
    }

}