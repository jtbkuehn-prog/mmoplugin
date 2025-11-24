package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.abilities.DamageBoostState;
import de.deinname.statsplugin.combat.CustomReach;
import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.mobs.CustomMobType;
import de.deinname.statsplugin.util.DamageNumbers;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;


import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.HashMap;

public class DamageListener implements Listener {

    private final StatsManager statsManager;
    private final DamageNumbers numbers;
    private final CustomMobManager customMobManager;
    private final Map<UUID, UUID> lastHit = new HashMap<>();

    // eigener Cooldown pro Spieler (Zeit in Nanosekunden)
    private final Map<UUID, Long> lastAttackTimeNs = new HashMap<>();
    private final NamespacedKey KEY_BOW_FORCE;

    public DamageListener(StatsManager statsManager,
                          DamageNumbers numbers,
                          CustomMobManager customMobManager, JavaPlugin plugin) {
        this.statsManager = statsManager;
        this.numbers = numbers;
        this.customMobManager = customMobManager;
        this.KEY_BOW_FORCE = new NamespacedKey(plugin, "bow_force");
    }

    /**
     * Zentrale Schadenslogik für Entity-zu-Entity-Schaden:
     * - Wenn Damager Spieler (Melee) oder Projektil von Spieler:
     *      → benutze Stats, Crit, DamageBoost, (Attack-Speed nur für Melee), Range nur für Melee.
     * - Wenn Damager Mob/Projectile: benutze bei Custom-Mobs deren damageAt(level),
     *   sonst Vanilla-FinalDamage.
     * - Wenn Ziel Spieler: wende Schaden auf Custom-HP an (currentHealth).
     * - Wenn Ziel Mob: wende Schaden auf dessen Health an (setHealth).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        Entity entity = e.getEntity();
        if (entity instanceof Player && damager instanceof Player) {
            e.setCancelled(true);
            return;}

        if (!(entity instanceof LivingEntity victim)) {
            return;
        }

        // Wenn Damager Spieler ist und Mage-Waffe in der Hand hat → Melee ignorieren,
// denn der Schaden kommt vom Beam im AttackListener.
        Player attacker = null;
        boolean isProjectileAttack = false;

        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
            isProjectileAttack = true;
        }

// Melee mit Mage-Waffe komplett canceln
        if (attacker != null && !isProjectileAttack) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            if (isMageWeapon(hand)) {
                e.setCancelled(true);
                return;
            }
        }


        if (attacker != null && !isProjectileAttack) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            if (isBowWeapon(hand)) {
                e.setCancelled(true);
                return;
            }
        }


        // Explosive-Arrow-Pfeile NICHT nochmal normal verarbeiten
        if (damager instanceof Projectile proj &&
                proj.getScoreboardTags().contains("ability_explosive_arrow")) {
            e.setCancelled(true);
            return;
        }


        // Herausfinden, ob es ein Spieler-Angriff ist (Melee oder Projektil)
        attacker = null;
        isProjectileAttack = false;

        if (damager instanceof Player p) {

            attacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
            isProjectileAttack = true;
        }

        double damage;
        boolean crit = false;


        // --- 1) Spieler-Angriff (Melee oder Projektil) ---
        if (attacker != null) {
            lastHit.put(victim.getUniqueId(), attacker.getUniqueId());
            PlayerStats atk = statsManager.getStats(attacker);
            if (atk == null) return;

            // Attack-Speed-Cooldown NUR für Melee
            if (!isProjectileAttack) {
                if (!canAttackNow(attacker, atk)) {
                    e.setCancelled(true);
                    return;
                }
            }

            // Extended-Reach/Range NUR für Melee
            if (!isProjectileAttack) {
                boolean allowExtended = CustomReach.consumeIfMatches(attacker.getUniqueId(), victim);

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

            // ---- Bogen-Schaden nach Aufladung skalieren ----
            if (isProjectileAttack && e.getDamager() instanceof Arrow arrow) {
                PersistentDataContainer pdc = arrow.getPersistentDataContainer();
                Float forceObj = pdc.get(KEY_BOW_FORCE, PersistentDataType.FLOAT);

                if (forceObj != null) {
                    double force = forceObj; // 0.0–1.0

                    // Nur bei voll aufgeladenem Schuss 100% Schaden,
                    // sonst linear weniger:
                    // force = 1.0 → 100%
                    // force = 0.5 → 50% usw.
                    damage *= force;

                    // Optional: minimaler Schaden, falls du kein 0-Damage willst:
                    // double scaled = 0.2 + 0.8 * force; // 20–100%
                    // damage *= scaled;
                }
            }


        } else {
            // --- 2) Damager ist KEIN Spieler (Zombie, Skeleton, Custom-Mob, etc.) ---
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

            double armor = defStats.getArmor();
            double finalDmg = applyArmor(damage, armor);
            damage = finalDmg;


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
                if (customMobManager != null
                    && victim instanceof LivingEntity le
                    && customMobManager.getType(le) != null) {

                    // ---- Custom-Mob-HP/Damage-System ----
                    double maxHp = customMobManager.getMaxHp(le);
                    if (maxHp <= 0) maxHp = 1.0;

                    double curHp = customMobManager.getCurrentHp(le);
                    if (curHp <= 0 || curHp > maxHp) {
                        curHp = maxHp;
                    }

                    // Armor anwenden (einfaches Beispiel)
                    double armor = customMobManager.getArmor(le);
                    double finalDmg = applyArmor(damage, armor);
                    damage = finalDmg;

                    double newHp = curHp - finalDmg;

                    if (newHp <= 0) {
                       customMobManager.setCurrentHp(le, 0.0);

                        // Vanilla-Tod auslösen, damit XP/Loot-Listener sauber greifen
                        e.setDamage(1000.0);
                    } else {
                       customMobManager.setCurrentHp(le, newHp);
                       e.setDamage(0.0); // keine Vanilla-HP runter

                        // kleine Hurt-Animation
                     le.playHurtAnimation(1.0f);
                    }
                        customMobManager.updateHpName(le);
                } else {
                    // ---- normale Vanilla-Mobs wie bisher ----
                    double before = victim.getHealth();
                    double after = Math.max(0.0, before - damage);

                    e.setDamage(0.0);
                    victim.setHealth(after);
                }
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

        // Sicherheits-Clamp
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
                return false;
            }
        }

        lastAttackTimeNs.put(id, now);
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
        double playerArmor = ps.getArmor();
        double SipDamage = applyArmor(dmg, playerArmor);
        dmg = SipDamage;
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

    private static final int MAGE_MODEL_ID = 2001;

    private boolean isMageWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == MAGE_MODEL_ID;
    }

    private static final int BOW_MODEL_ID = 3001;

    private boolean isBowWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == BOW_MODEL_ID;
    }


    private double applyArmor(double damage, double armor) {
        if (armor <= 0) return damage;
        // Beispiel-Formel: je mehr Armor, desto weniger Damage, aber mit abnehmendem Nutzen
        double reduction = armor / (armor + 1000.0); // 1000 = "Skalierungs-Konstante"
        double finalDmg = damage * (1.0 - reduction);
        return Math.max(0.0, finalDmg);
    }


}
