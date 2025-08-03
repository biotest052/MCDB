package com.decacagle.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class SetUrlCommand implements CommandExecutor {

    public static String url = "http://localhost:8080";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /seturl \"<url>\"");
            return false;
        }

        String fullInput = String.join(" ", args);

        if (fullInput.startsWith("\"") && fullInput.endsWith("\"")) {
            fullInput = fullInput.substring(1, fullInput.length() - 1);
        }

        if (!fullInput.startsWith("http://") && !fullInput.startsWith("https://")) {
            sender.sendMessage(ChatColor.RED + "Invalid URL. Must start with http:// or https://");
            return true;
        }

        this.url = fullInput;
        sender.sendMessage(ChatColor.GREEN + "URL set to: " + ChatColor.YELLOW + url);
        return true;
    }

    public String getUrl() {
        return url;
    }
}
