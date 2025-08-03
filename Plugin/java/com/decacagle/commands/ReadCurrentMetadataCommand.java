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

import java.util.Base64;
import java.util.logging.Level;

public class ReadCurrentMetadataCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GREEN + "Reading metadata.. ");

            String metadata = APIManager.getMetadata((Player)sender, sender);
            sender.sendMessage(metadata);

            DecaDB.instance.getLogger().log(Level.INFO, metadata);
        }
        else
        {
            boolean base64decode = Boolean.parseBoolean(args[0]);

            String metadata1 = APIManager.getMetadata((Player)sender, sender);
            if (base64decode) {
                byte[] decoded = Base64.getDecoder().decode(metadata1);
                String metadata = new String(decoded);
                sender.sendMessage(metadata);

                DecaDB.instance.getLogger().log(Level.INFO, metadata);
            }
            else
            {
                sender.sendMessage(metadata1);

                DecaDB.instance.getLogger().log(Level.INFO, metadata1);
            }
        }
        return true;
    }
}
