package com.decacagle.commands;

import com.decacagle.APIManager;
import com.decacagle.DecaDB;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Base64;
import java.util.logging.Level;

public class ReadCurrentMetadataCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
            return true;
        }

        if (args.length == 0) {
            String metadata = APIManager.getMetadata(player, sender);
            sender.sendMessage(metadata);
            DecaDB.instance.getLogger().log(Level.INFO, metadata);
        } else {
            boolean base64decode = Boolean.parseBoolean(args[0]);
            String metadata = APIManager.getMetadata(player, sender);

            if (base64decode) {
                try {
                    byte[] decoded = Base64.getDecoder().decode(metadata);
                    String result = new String(decoded);
                    sender.sendMessage(result);
                    DecaDB.instance.getLogger().log(Level.INFO, result);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Metadata is not valid Base64: " + e.getMessage());
                }
            } else {
                sender.sendMessage(metadata);
                DecaDB.instance.getLogger().log(Level.INFO, metadata);
            }
        }
        return true;
    }
}