package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class PlayerListener implements Listener {
    private final StatsManager statsManager;

    public PlayerListener(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Stats laden/erstellen
        statsManager.getStats(event.getPlayer());
        // Health anwenden
        statsManager.applyHealth(event.getPlayer());

        event.getPlayer().sendMessage("§aStats-System geladen!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Stats beim Verlassen speichern und aus dem RAM entfernen
        event.getPlayer().sendMessage("§eSpeichere deine Stats...");
        statsManager.removeStats(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage("§aStats gespeichert!");
    }

}