package de.deinname.statsplugin.abilities;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet den temporären Damage-Multiplikator pro Spieler.
 * - activate(...) setzt einen Multiplikator für X Ticks
 * - isActive/getMultiplier für Abfragen
 * - Listener @MONITOR multipliziert den finalen Schaden (sofern kein anderer Listener danach überschreibt)
 *
 * HINWEIS: Registriere diesen Listener NACH deinen eigenen Damage-/Combat-Listenern.
 */
public class DamageBoostState implements Listener {

    private static final Map<UUID, Double> MULT = new HashMap<>();

    public static void activate(Plugin plugin, UUID id, int durationTicks, double multiplier) {
        MULT.put(id, multiplier);
        Bukkit.getScheduler().runTaskLater(plugin, () -> MULT.remove(id), durationTicks);
    }

    public static boolean isActive(UUID id) {
        return MULT.containsKey(id);
    }

    public static double getMultiplier(UUID id) {
        return MULT.getOrDefault(id, 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        Double m = MULT.get(p.getUniqueId());
        if (m == null || m <= 0.0) return;
        e.setDamage(e.getDamage() * m);
    }
}
