package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.abilities.DamageBoostState;
import de.deinname.statsplugin.combat.CustomReach;
import de.deinname.statsplugin.util.DamageNumbers;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

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
     * Zentrale Schadenslogik für Entity-zu-Entity-Schaden:
     * - Wenn Damager Spieler: benutze Stats, Crit, DamageBoost, Range.
     * - Wenn Damager kein Spieler: benutze Vanilla-FinalDamage als Basis.
     * - Wenn Ziel Spieler: wende Schaden auf Custom-HP an (currentHealth).
     * - Wenn Ziel Mob: wende Schaden auf dessen Health an (setHealth).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        Entity entity = e.getEntity();

        if (!(entity instanceof LivingEntity victim)) {
            return;
        }

        double damage;
        boolean crit = false;

        // --- 1) Wenn Damager ein Spieler ist -> eigene Stats, Range, Crit, DamageBoost ---
        if (damager instanceof Player attacker) {
            PlayerStats atk = statsManager.getStats(attacker);
            if (atk == null) return;

            // Extended-Reach-Override?
            boolean allowExtended = CustomReach.consumeIfMatches(attacker.getUniqueId(), victim);

            // Range-Check (nur wenn kein Override)
            if (!allowExtended) {
                double dist = attacker.getEyeLocation().distance(
                        victim.getLocation().add(0, victim.getHeight() * 0.5, 0)
                );
                if (dist > atk.getRange()) {
                    e.setCancelled(true);
                    attacker.sendActionBar(net.kyori.adventure.text.Component.text(
                            "Zu weit: " + String.format("%.1f", dist) + " / " + String.format("%.1f", atk.getRange())
                    ));
                    return;
                }
            }

            // Basis-Schaden + Crit
            crit = rollCrit(atk.getCritChance());
            damage = atk.getDamage();
            if (crit) {
                damage *= (atk.getCritDamage() / 100.0);
            }

            // Damage-Boost
            UUID aid = attacker.getUniqueId();
            if (DamageBoostState.isActive(aid)) {
                damage *= DamageBoostState.getMultiplier(aid);
            }

        } else {
            // --- 2) Damager ist KEIN Spieler (Zombie, Skelett, etc.) ---
            // Wir nehmen einfach den Vanilla-FinalDamage als Basis.
            damage = e.getFinalDamage();
        }

        // --- 3) Schaden anwenden je nach Ziel-Typ ---

        if (victim instanceof Player def) {
            // Custom-HP für Spieler
            PlayerStats defStats = statsManager.getStats(def);
            if (defStats == null) return;

            double maxHp = defStats.getHealth();          // Max-HP (Base + Items)
            if (maxHp <= 0) maxHp = 1.0;

            double curHp = defStats.getCurrentHealth();   // aktuelle HP
            if (curHp <= 0 || curHp > maxHp) {
                curHp = maxHp;
            }

            double newHp = curHp - damage;

            if (newHp <= 0) {
                // Spieler stirbt in unserem System
                defStats.setCurrentHealth(0.0);

                // Vanilla-Tod auslösen (Death-Screen, Respawn, etc.)
                e.setDamage(1000.0); // genug, um sicher zu töten
            } else {
                // Spieler überlebt
                defStats.setCurrentHealth(newHp);
                e.setDamage(0.0); // KEIN Vanilla-Schaden mehr auf Herzen
            }

        } else {
            // --- 4) Mobs / andere LivingEntities: Custom-Schaden direkt anwenden ---
            double before = victim.getHealth();
            double after = Math.max(0.0, before - damage);

            e.setDamage(0.0); // Vanilla-Schaden verhindern
            victim.setHealth(after);
        }

        // --- 5) Feedback: Damage-Number & Crit-Partikel ---
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

    /**
     * Allgemeiner Handler für NICHT-Entity-Schaden (Fall, Feuer, Lava, Explosion, etc.)
     * Alles, was nicht über EntityDamageByEntityEvent kommt, landet hier.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGenericDamage(EntityDamageEvent e) {
        // EntityDamageByEntityEvents werden bereits oben behandelt
        if (e instanceof EntityDamageByEntityEvent) return;

        if (!(e.getEntity() instanceof Player p)) return;

        PlayerStats ps = statsManager.getStats(p);
        if (ps == null) return;

        double maxHp = ps.getHealth();          // dein MaxHP (Base + Items)
        if (maxHp <= 0) maxHp = 1.0;

        double curHp = ps.getCurrentHealth();   // dein aktuelles HP
        if (curHp <= 0 || curHp > maxHp) {
            curHp = maxHp;                      // initialisieren/clampen
        }

        // --- Basis-Schaden von Vanilla holen ---
        double base = e.getFinalDamage();
        if (base <= 0.0) {
            e.setDamage(0.0);
            return;
        }

        // --- Skalierung je nach Ursache ---
        double dmg = base;
        double scale = 1.0;
        double maxFraction = 0.0; // 0 = kein Cap

        switch (e.getCause()) {
            case LAVA, FIRE, FIRE_TICK -> {
                // Lava/Feuer: eher DoT, aber nicht instant Kill
                scale = 0.25;          // halber Vanilla-Schaden
                maxFraction = 0.10;   // pro Tick max 10% MaxHP
            }
            case HOT_FLOOR -> { // Magma-Block
                scale = 0.25;         // Viertel des Vanilla-Schadens
                maxFraction = 0.05;   // max 5% MaxHP pro Tick
            }
            case CONTACT -> { // Kaktus
                scale = 0.25;
                maxFraction = 0.03;   // 3% HP max pro Tick
            }
            case POISON -> {
                scale = 0.3;
                maxFraction = 0.05;
            }
            // andere Ursachen behalten erstmal 1:1 Schaden
            default -> {
                scale = 1.0;
                maxFraction = 0.0;
            }
        }

        dmg = base * scale;
        if (maxFraction > 0.0) {
            dmg = Math.min(dmg, maxHp * maxFraction);
        }

        if (dmg <= 0.0) {
            e.setDamage(0.0);
            return;
        }

        double newHp = curHp - dmg;

        if (newHp <= 0) {
            // Im eigenen System tot
            ps.setCurrentHealth(0.0);

            // Bukkit wirklich töten lassen (Death-Screen / Respawn)
            e.setDamage(1000.0); // genug, um den Spieler sicher zu killen
        } else {
            // Spieler überlebt: nur Custom-HP runter, Vanilla-Schaden unterdrücken
            ps.setCurrentHealth(newHp);
            e.setDamage(0.0); // keine Vanilla-Herzen verlieren
        }
    }

}
