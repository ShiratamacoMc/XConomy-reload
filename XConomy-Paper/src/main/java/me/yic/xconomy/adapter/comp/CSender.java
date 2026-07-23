package me.yic.xconomy.adapter.comp;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.adapter.iSender;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class CSender implements iSender {
    private final CommandSender sender;

    public CSender(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public boolean isOp() {
        return sender.isOp();
    }

    @Override
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    @Override
    public CPlayer toPlayer() {
        return new CPlayer((Player) sender);
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public void sendMessage(String message) {
        if (sender instanceof Player) {
            new CPlayer((Player) sender).sendMessage(message);
        } else {
            Bukkit.getGlobalRegionScheduler().run(
                    XConomy.getInstance(), task -> sender.sendMessage(message));
        }
    }

    @Override
    public void sendMessage(String[] message) {
        if (sender instanceof Player) {
            new CPlayer((Player) sender).sendMessage(message);
        } else {
            Bukkit.getGlobalRegionScheduler().run(
                    XConomy.getInstance(), task -> sender.sendMessage(message));
        }
    }
}
