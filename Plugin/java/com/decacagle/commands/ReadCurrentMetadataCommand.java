package com.decacagle.commands;

import com.decacagle.APIManager;
import com.decacagle.DecaDB;
import com.decacagle.data.DataUtilities;
import com.decacagle.endpoints.FileReader;
import com.decacagle.endpoints.QueryHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class ReadCurrentMetadataCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.GREEN + "Reading metadata.. ");

        String metadata = APIManager.getMetadata((Player)sender, sender);
        sender.sendMessage(metadata);

        DecaDB.instance.getLogger().log(Level.INFO, metadata);

        return true;
    }
}
