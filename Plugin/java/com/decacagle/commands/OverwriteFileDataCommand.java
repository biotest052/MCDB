package com.decacagle.commands;

import com.decacagle.APIManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class OverwriteFileDataCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /overwritefiledata \"<file path>\"");
            return false;
        }

        String fullInput = String.join(" ", args);

        if (fullInput.startsWith("\"") && fullInput.endsWith("\"")) {
            fullInput = fullInput.substring(1, fullInput.length() - 1);
        }

        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(fullInput));
            byte[] encodedString = Base64.getEncoder().encode(fileBytes);
            String encodeString = new String(encodedString);

            if (APIManager.writeMetadata((Player)sender, sender, encodeString, fullInput)) {
                sender.sendMessage(ChatColor.GREEN + "File overwritten!");
            }
            else
            {
                sender.sendMessage(ChatColor.RED + "Error overwriting chunk, either file couldnt get written or something else.");
            }
        }
        catch (IOException e) {
            System.err.println("Error reading or encoding file: " + e.getMessage());
            sender.sendMessage(ChatColor.RED + "Error overwriting: " + e.getMessage());
        }

        return true;
    }
}
