package me.yic.xconomy.adapter.comp;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.adapter.iPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

@SuppressWarnings("unused")
public class CPlayer implements iPlayer {
    private final Player player;

    public CPlayer(Player player) {
        this.player = player;
    }

    public CPlayer(UUID uuid) {
        this.player = Bukkit.getPlayer(uuid);
    }

    @Override
    public boolean isOp() {
        return player.isOp();
    }

    @Override
    public void kickPlayer(String reason) {
        runOnPlayer(() -> player.kickPlayer(reason));
    }

    @Override
    public void sendMessage(String message) {
        runOnPlayer(() -> player.sendMessage(message));
    }

    @Override
    public void sendMessage(String[] message) {
        runOnPlayer(() -> player.sendMessage(message));
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public boolean isOnline() {
        return player != null && player.isOnline();
    }

    private void runOnPlayer(Runnable runnable) {
        if (player == null) {
            return;
        }
        player.getScheduler().run(XConomy.getInstance(), task -> runnable.run(), null);
    }
}
