package com.kaustubh.localservers.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class LocalServersBridgePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("LocalServersBridge ready. Tunnel status is managed by manager.jar.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("Local server bridge is installed. Tunnel status is managed by the Local Servers dashboard.");
        return true;
    }
}
