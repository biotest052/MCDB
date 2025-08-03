package com.decacagle;

import com.decacagle.commands.ReadCurrentMetadataCommand;
import com.decacagle.commands.SetUrlCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DecaDB extends JavaPlugin {
    private APIManager httpServer = null;
    public static DecaDB instance = null;

    @Override
    public void onEnable() {
        getLogger().info("DecaDB v1.0 launched successfully!");

        instance = this;

        if (httpServer == null) {
            httpServer = new APIManager(getLogger(), getServer().getWorld("world"), this);
        }

        getCommand("seturl").setExecutor(new SetUrlCommand());
        getCommand("readcurrentmetadata").setExecutor(new ReadCurrentMetadataCommand());
    }

    @Override
    public void onDisable() {
        getLogger().info("DecaDB v1.0 disabled successfully!");

        instance = null;
    }
}