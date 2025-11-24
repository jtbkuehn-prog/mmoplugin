package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.combat.CustomReach;
import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.util.DamageNumbers;
import de.deinname.statsplugin.abilities.DamageBoostState;
import org.bukkit.EntityEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Color;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * - Für normale Waffen: Extended-Reach mit CustomReach + DamageListener.
 * - Für Mage-Waffen (CustomModelData 2001): eigener Beam-Schuss mit
 *   hoher Range, 50% Schaden, kein Knockback, aber Hurt-Animation.
 */
public class AttackListener implements Listener {

    private final StatsManager statsManager;
    private final DamageNumbers numbers;
    private final CustomMobManager mobManager;

    private static final double VANILLA_REACH_FUZZ = 4.5;

    // eigener Cooldown für Mage-Beam (APS)
    private final Map<UUID, Long> lastBeamAttackNs = new HashMap<>();

    // CustomModelData der Mage-Waffe
    private static final int MAGE_MODEL_ID = 2001;

    public AttackListener(StatsManager statsManager, DamageNumbers numbers, CustomMobManager customMobManager) {
        this.statsManager = statsManager;
        this.numbers = numbers;
        this.mobManager = customMobManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();

        PlayerStats stats = statsManager.getStats(p);
        if (stats == null) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null) return;

        // 🔮 1) Mage-Waffe? → Beam-Logik
        if (isMageWeapon(hand)) {
            handleMageBeamSwing(p, stats);
            return;
        }

        // ⚔ 2) Normale Waffe → Extended-Reach wie bisher
        double range = Math.max(0.0, stats.getRange());
        if (range <= VANILLA_REACH_FUZZ) return; // Vanilla-Reach reicht aus

        Vector dir = p.getEyeLocation().getDirection();
        RayTraceResult hit = p.getWorld().rayTrace(
                p.getEyeLocation(),
                dir,
                range,
                FluidCollisionMode.NEVER,
                true,  // ignore passable
                0.2,   // entity size margin
                entity -> entity instanceof LivingEntity && entity != p
        );

        if (hit == null) return;
        Entity hitEntity = hit.getHitEntity();
        if (!(hitEntity instanceof LivingEntity target)) return;

        double dist = p.getEyeLocation().distance(hit.getHitPosition().toLocation(p.getWorld()));
        if (dist <= VANILLA_REACH_FUZZ) {
            // innerhalb Vanilla → DamageListener + Bukkit machen das
            return;
        }

        // Extended-Hit: DamageListener darf Range NICHT abbrechen.
        CustomReach.allowOnce(p.getUniqueId(), target);

        // Event auslösen, damit DamageListener den Schaden berechnet
        if (target instanceof Player && p instanceof  Player) {
            e.setCancelled(true);
            return; }
        target.damage(0.0, p);
    }

    // ====================== MAGE BEAM ======================

    private boolean isMageWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == MAGE_MODEL_ID;
    }

    private void handleMageBeamSwing(Player p, PlayerStats stats) {
        // Attack-Speed für Beam
        if (!canBeamNow(p, stats)) return;

        double statRange = Math.max(0.0, stats.getRange());
        double range = Math.max(10.0, statRange); // mindestens ~10 Blöcke

        // Startpunkt etwas unterhalb der Augen, damit der Strahl nicht das ganze Sichtfeld blockiert
        var start = p.getEyeLocation().clone().add(0, -0.3, 0);
        Vector dir = start.getDirection().normalize();

        RayTraceResult hit = p.getWorld().rayTrace(
                start,
                dir,
                range,
                FluidCollisionMode.NEVER,
                true,
                0.2,
                entity -> entity instanceof LivingEntity && entity != p
        );

        LivingEntity victim = null;
        var world = p.getWorld();
        Vector endVec;

        if (hit != null && hit.getHitEntity() instanceof LivingEntity le) {
            victim = le;
            endVec = hit.getHitPosition();
        } else {
            endVec = start.toVector().add(dir.multiply(range));
        }

        // Beam-Particles zeichnen
        drawBeam(start.toVector(), endVec, world);

        if (victim == null) return;

        // 50% deines normalen Schadens, inkl. Crit & DamageBoost
        double baseDamage = stats.getDamage() * 0.5;

        boolean crit = rollCrit(stats.getCritChance());
        double dmg = baseDamage;
        if (crit) {
            dmg *= (stats.getCritDamage() / 100.0);
        }

        if (DamageBoostState.isActive(p.getUniqueId())) {
            dmg *= DamageBoostState.getMultiplier(p.getUniqueId());
        }

        applyMageBeamDamage(p, victim, dmg, crit);
    }

    private boolean canBeamNow(Player p, PlayerStats atk) {
        double aps = atk.getAttackSpeed(); // Angriffe pro Sekunde
        if (aps < 0.1) aps = 0.1;
        if (aps > 20.0) aps = 20.0;

        double cooldownSeconds = 1.0 / aps;
        long cooldownNs = (long) (cooldownSeconds * 1_000_000_000L);

        long now = System.nanoTime();
        UUID id = p.getUniqueId();
        Long last = lastBeamAttackNs.get(id);

        if (last != null && now - last < cooldownNs) {
            return false;
        }

        lastBeamAttackNs.put(id, now);
        return true;
    }

    private void drawBeam(Vector start, Vector end, org.bukkit.World world) {
        Vector diff = end.clone().subtract(start);
        double length = diff.length();
        if (length <= 0) return;

        Vector step = diff.normalize().multiply(0.3); // Abstand zwischen Partikeln
        int points = (int) (length / 0.3);

        Vector pos = start.clone();
        for (int i = 0; i <= points; i++) {
            world.spawnParticle(Particle.ENCHANT, pos.getX(), pos.getY(), pos.getZ(), 1, 0, 0, 0, 0, null, true);
            pos.add(step);
        }

        //world.playSound(start.toLocation(world), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.4f);
    }

    private void applyMageBeamDamage(Player attacker, LivingEntity victim, double damage, boolean crit) {
        if (victim instanceof Player def) {
            return;

        } else {
            if (mobManager != null
                    && victim instanceof LivingEntity le
                    && mobManager.getType(le) != null) {

                // ---- Custom-Mob-HP/Damage-System ----
                double maxHp = mobManager.getMaxHp(le);
                if (maxHp <= 0) maxHp = 1.0;

                double curHp = mobManager.getCurrentHp(le);
                if (curHp <= 0 || curHp > maxHp) {
                    curHp = maxHp;
                }

                // Armor anwenden (einfaches Beispiel)
                double armor = mobManager.getArmor(le);
                double finalDmg = applyArmor(damage, armor);
                damage = finalDmg;
                double newHp = curHp - finalDmg;

                if (newHp <= 0) {
                    mobManager.setCurrentHp(le, 0.0);

                    // Vanilla-Tod auslösen, damit XP/Loot-Listener sauber greifen
                    victim.setHealth(0);
                } else {
                    mobManager.setCurrentHp(le, newHp);
                }

                mobManager.updateHpName(le);

            } else {
            double before = victim.getHealth();
            double after = Math.max(0.0, before - damage);
            victim.setHealth(after);
            // ➜ HP-Anzeige für Custom-Mobs aktualisieren
            if (mobManager != null) {
                mobManager.updateHpName(victim);
            }
        }
        }




        // Hurt-Animation ohne Knockback
        victim.playHurtAnimation(1);


        // Damage-Number wie bei normalem Hit
        var rand = ThreadLocalRandom.current();
        double xOff = (rand.nextDouble() - 0.5) * 0.6;
        double zOff = (rand.nextDouble() - 0.5) * 0.6;

        var base = victim.getLocation().add(0, victim.getHeight() * 1.0, 0);
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

    private double applyArmor(double dmg, double armor) {
        if (armor <= 0) return dmg;
        // Beispiel-Formel: je mehr Armor, desto weniger Damage, aber mit abnehmendem Nutzen
        double reduction = armor / (armor + 1000.0); // 1000 = "Skalierungs-Konstante"
        double finalDmg = dmg * (1.0 - reduction);
        return Math.max(0.0, finalDmg);
    }
}
