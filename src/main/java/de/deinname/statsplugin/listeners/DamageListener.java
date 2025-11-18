package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.abilities.DamageBoostState;
import de.deinname.statsplugin.combat.CustomReach;
import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.mobs.CustomMobType;
import de.deinname.statsplugin.util.DamageNumbers;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.HashMap;

public class DamageListener implements Listener {

    private final StatsManager statsManager;
    private final DamageNumbers numbers;
    private final CustomMobManager customMobManager;


    // eigener Cooldown pro Spieler (Zeit in Nanosekunden)
    private final Map<UUID, Long> lastAttackTimeNs = new HashMap<>();

    public DamageListener(StatsManager statsManager,
                          DamageNumbers numbers,
                          CustomMobManager customMobManager) {
        this.statsManager = statsManager;
        this.numbers = numbers;
        this.customMobManager = customMobManager;
    }

    /**
     * Zentrale Schadenslogik für Entity-zu-Entity-Schaden:
     * - Wenn Damager Spieler: benutze Stats, Crit, DamageBoost, Range, Attack-Speed.
     * - Wenn Damager Mob/Projectile: benutze bei Custom-Mobs deren damageAt(level),
     *   sonst Vanilla-FinalDamage.
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

        // --- 1) Wenn Damager ein Spieler ist -> eigene Stats, Range, Crit, DamageBoost, Attack-Speed ---
        if (damager instanceof Player attacker) {
            PlayerStats atk = statsManager.getStats(attacker);
            if (atk == null) return;

            // Attack-Speed-Cooldown prüfen
            if (!canAttackNow(attacker, atk)) {
                // noch im Cooldown → Hit komplett ignorieren
                e.setCancelled(true);
                return;
            }

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
            // --- 2) Damager ist KEIN Spieler (Zombie, Skeleton, Projektil, etc.) ---
            damage = computeMobDamage(e);
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
        var rand = ThreadLocalRandom.current();
        double xOff = (rand.nextDouble() - 0.5) * 0.6;
        double zOff = (rand.nextDouble() - 0.5) * 0.6;

        var base = victim.getLocation().add(0, victim.getHeight() * 1, 0);
        base.add(xOff, 0, zOff);

        numbers.show(victim.getWorld(), base, damage, crit);

        if (crit) {
            victim.getWorld().spawnParticle(
                    Particle.CRIT,
                    victim.getLocation().add(0, victim.getHeight() + 0.5, 0),
                    25, 0.4, 0.4, 0.4, 0.05
            );
        }

    }

    private boolean rollCrit(double critChancePercent) {
        if (critChancePercent <= 0) return false;
        return ThreadLocalRandom.current().nextDouble(0.0, 100.0) < critChancePercent;
    }


    /**
     * Attack-Speed-Logik:
     * - attackSpeed = Angriffe pro Sekunde
     * - cooldownMillis = 1000 / attackSpeed
     * Wir benutzen Systemzeit, nicht Welt-Ticks → unabhängig von TPS & Welt.
     */
    private boolean canAttackNow(Player p, PlayerStats atk) {
        double aps = atk.getAttackSpeed(); // Angriffe pro Sekunde

        // Sicherheits-Clamp: niemals kleiner als 0.1 APS und niemals größer als z.B. 20 APS
        if (aps < 0.1) aps = 0.1;     // max 10 Sekunden Cooldown
        if (aps > 20.0) aps = 20.0;   // min 0.05s (1 Tick) Cooldown

        double cooldownSeconds = 1.0 / aps;
        long cooldownNs = (long) (cooldownSeconds * 1_000_000_000L);

        long now = System.nanoTime();
        UUID id = p.getUniqueId();
        Long last = lastAttackTimeNs.get(id);

        if (last != null) {
            long diff = now - last;
            if (diff < cooldownNs) {
                // DEBUG optional:
                // p.sendMessage("§cCD " + (diff/1_000_000) + "ms / " + (cooldownNs/1_000_000) + "ms");
                return false;
            }
        }

        // Hit ist erlaubt → Zeit aktualisieren
        lastAttackTimeNs.put(id, now);

        // DEBUG optional:
        // p.sendMessage("§aHit OK. APS=" + aps + " CD=" + (cooldownNs/1_000_000) + "ms");

        return true;
    }


    /**
     * Bestimmt den Schaden eines NICHT-Spieler-Damagers.
     * - Wenn die Quelle ein Custom-Mob ist (auch als Shooter eines Projektils):
     *      → damageAt(level) aus CustomMobType.
     * - Sonst: Vanilla-FinalDamage.
     */
    private double computeMobDamage(EntityDamageByEntityEvent e) {
        double vanilla = e.getFinalDamage();

        if (customMobManager == null) {
            return vanilla;
        }

        Entity damager = e.getDamager();
        LivingEntity src = null;

        // Projektile (z.B. Skeleton-Pfeile)
        if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) {
            src = shooter;
        }
        // Direkter Melee-Hit von einem Mob
        else if (damager instanceof LivingEntity le && !(damager instanceof Player)) {
            src = le;
        }

        if (src == null) {
            return vanilla;
        }

        CustomMobType type = customMobManager.getType(src);
        if (type == null) {
            return vanilla; // normaler Vanilla-Mob
        }

        int lvl = customMobManager.getLevel(src);
        if (lvl <= 0) lvl = 1;

        double dmg = type.damageAt(lvl);
        return dmg > 0 ? dmg : vanilla;
    }

    /**
     * Allgemeiner Handler für NICHT-Entity-Schaden (Fall, Feuer, Lava, Explosion, etc.)
     * Alles, was nicht über EntityDamageByEntityEvent kommt, landet hier.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGenericDamage(EntityDamageEvent e) {
        // EntityDamageByEntityEvents werden bereits oben in onDamage behandelt
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
                scale = 0.25;          // 1/4 des Vanilla-Schadens
                maxFraction = 0.10;   // pro Tick max 10% MaxHP
            }
            case HOT_FLOOR -> { // Magma-Block
                scale = 0.25;
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
