package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.abilities.DamageBoostState;
import de.deinname.statsplugin.util.DamageNumbers;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DamageListener implements Listener {

    private final StatsManager statsManager;
    private final DamageNumbers numbers;

    public DamageListener(StatsManager statsManager, DamageNumbers numbers) {
        this.statsManager = statsManager;
        this.numbers = numbers;
    }

    /**
     * Vanilla-Treffer abfangen und unseren Schaden setzen.
     * Priorität HIGHEST, damit wir nach der Vanilla-Berechnung dran sind,
     * aber noch vor MONITOR-Listenern (z. B. DamageNumbers-Anzeigen).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        // Stats des Angreifers holen
        PlayerStats atk = statsManager.getStats(attacker);
        if (atk == null) return;

        // Range-Check (Abbruch + Meldung, wenn zu weit)
        double dist = attacker.getLocation().distance(victim.getLocation());
        if (dist > atk.getRange()) {
            e.setCancelled(true);
            attacker.sendMessage("§cZu weit entfernt! (Max: " +
                    String.format("%.1f", atk.getRange()) +
                    " | Aktuell: " + String.format("%.1f", dist) + ")");
            return;
        }

        // Crit-Roll (CritChance in %)
        boolean crit = rollCrit(atk.getCritChance());

        // Basis-Schaden berechnen
        double damage = atk.getDamage();
        if (crit) {
            damage *= (1.0 + atk.getCritDamage() / 100.0);
        }

        // Rüstung des Ziels (wenn Spieler) einfach abziehen (sehr simple Formel)
        if (victim instanceof Player def) {
            PlayerStats defStats = statsManager.getStats(def);
            if (defStats != null) {
                // simple armor mitigation (linear): damage -= armor
                damage = Math.max(0.0, damage - defStats.getArmor());
            }
        }

        // >>> Damage-Boost korrekt anwenden (KEIN 'finalDamage' mehr!)
        UUID aid = attacker.getUniqueId();
        if (DamageBoostState.isActive(aid)) {
            damage *= DamageBoostState.getMultiplier(aid);
        }

        // Final auf Event setzen
        e.setDamage(Math.max(0.0, damage));

        // Floating numbers & optional Crit-Particle
        numbers.show(victim.getWorld(), victim.getLocation(), damage, crit);
        if (crit) {
            victim.getWorld().spawnParticle(
                    Particle.CRIT, victim.getLocation().add(0, 1, 0),
                    25, 0.4, 0.4, 0.4, 0.05
            );
        }
    }

    private boolean rollCrit(double critChancePercent) {
        if (critChancePercent <= 0) return false;
        return ThreadLocalRandom.current().nextDouble(0.0, 100.0) < critChancePercent;
    }
}
