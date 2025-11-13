package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.abilities.DamageBoostState;
import de.deinname.statsplugin.combat.CustomReach;
import de.deinname.statsplugin.util.DamageNumbers;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import de.deinname.statsplugin.listeners.AttackListener;

import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zentraler Schaden:
 * - Prüft zuerst Extended-Reach-Override (CustomReach).
 * - Wenn kein Override: klassischer Range-Check mit Eye->Center.
 * - Danach Crit, Armor, DamageBoost und final setDamage.
 */
public class DamageListener implements Listener {

    private final StatsManager statsManager;
    private final DamageNumbers numbers;

    // klassische Vanilla-Obergrenze, nur als Info
    private static final double VANILLA_REACH_FUZZ = 4.5;

    public DamageListener(StatsManager statsManager, DamageNumbers numbers) {
        this.statsManager = statsManager;
        this.numbers = numbers;
    }

    // Früh rein, damit Range/Overrides vor Rest greifen
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        PlayerStats atk = statsManager.getStats(attacker);
        if (atk == null) return;

        // --- 1) Extended-Reach-Override? ---
        boolean allowExtended = CustomReach.consumeIfMatches(attacker.getUniqueId(), victim);

        // --- 2) Range-Check (Eye -> Center), nur wenn KEIN Override ---
        if (!allowExtended) {
            double dist = attacker.getEyeLocation().distance(victim.getLocation().add(0, victim.getHeight() * 0.5, 0));
            if (dist > atk.getRange()) {
                e.setCancelled(true);
                attacker.sendActionBar(net.kyori.adventure.text.Component.text(
                        "Zu weit: " + String.format("%.1f", dist) + " / " + String.format("%.1f", atk.getRange())
                ));
                return;
            }
        }

        // --- 3) Eigene Schadensberechnung ---
        boolean crit = rollCrit(atk.getCritChance());
        double damage = atk.getDamage();
        if (crit) damage *= (atk.getCritDamage() / 100.0);

        if (victim instanceof Player def) {
            PlayerStats defStats = statsManager.getStats(def);
            if (defStats != null) damage = Math.max(0.0, damage - defStats.getArmor());
        }

        // Damage-Boost anwenden
        UUID aid = attacker.getUniqueId();
        if (DamageBoostState.isActive(aid)) {
            damage *= DamageBoostState.getMultiplier(aid);
        }

// 4) Vanilla-Schaden ausschalten
// (wir setzen ihn auf 0, lassen das Event aber NICHT cancelled -> Killer bleibt für XP)
        e.setDamage(0.0);

// 5) Eigenen Schaden auf die HP anwenden
        double before = victim.getHealth();
        double after = Math.max(0.0, before - damage);
        victim.setHealth(after);

// 6) Feedback: Damage-Number & Crit-Partikel
        numbers.show(victim.getWorld(), victim.getLocation(), damage, crit);
        if (crit) {
            victim.getWorld().spawnParticle(
                    Particle.CRIT,
                    victim.getLocation().add(0, 1, 0),
                    25, 0.4, 0.4, 0.4, 0.05
            );
        }
    }

    private boolean rollCrit(double critChancePercent) {
        if (critChancePercent <= 0) return false;
        return ThreadLocalRandom.current().nextDouble(0.0, 100.0) < critChancePercent;
    }
}
