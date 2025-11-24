package de.deinname.statsplugin.abilities;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.mana.ManaManager;
import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.util.DamageNumbers;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;


import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ExplosiveArrowAbility implements Listener {

    private static final String ABILITY_ID = "explosive_arrow";
    private static final String TAG = "ability_explosive_arrow";

    private final JavaPlugin plugin;
    private final ManaManager mana;
    private final ItemStatKeys keys;
    private final StatsManager statsManager;
    private final DamageNumbers numbers;
    private final CustomMobManager customMobManager;

    // Cooldown pro Spieler
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ExplosiveArrowAbility(JavaPlugin plugin,
                                 ManaManager mana,
                                 ItemStatKeys keys,
                                 StatsManager statsManager,
                                 DamageNumbers numbers,
                                 CustomMobManager customMobManager) {
        this.plugin = plugin;
        this.mana = mana;
        this.keys = keys;
        this.statsManager = statsManager;
        this.numbers = numbers;
        this.customMobManager = customMobManager;
    }

    // ----------------------------------------------------
// 0) Arm-Swing (egal ob auf Luft gezielt) → Ability
//    Fix dafür, dass LEFT_CLICK_AIR nicht immer feuert
// ----------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player p = e.getPlayer();

        // Hand-Item checken
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String ability = pdc.get(keys.ABILITY, PersistentDataType.STRING);
        if (ability == null || !ability.equalsIgnoreCase(ABILITY_ID)) return;

        // Hier NICHT canceln – der eigentliche Hit (wenn einer zustande kommt)
        // wird in onMeleeHit(EntityDamageByEntityEvent) abgefangen.
        tryActivateAbility(p);
    }


    // ----------------------------------------------------
    // 2) Linksklick auf Entity (Melee-Hit) → KEIN Schaden,
    //    nur Ability + Animation
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMeleeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;

        // Main-Hand-Item mit Ability?
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;
        ItemMeta meta = hand.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String ability = pdc.get(keys.ABILITY, PersistentDataType.STRING);
        if (ability == null || !ability.equalsIgnoreCase(ABILITY_ID)) return;

        // Melee-Schaden komplett unterdrücken
        e.setCancelled(true);

        // Ability wie bei Linksklick auslösen
        tryActivateAbility(p);
    }

    // ----------------------------------------------------
    // Gemeinsame Aktivierungslogik
    // ----------------------------------------------------
    private void tryActivateAbility(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String ability = pdc.get(keys.ABILITY, PersistentDataType.STRING);
        if (ability == null || !ability.equalsIgnoreCase(ABILITY_ID)) return;

        // Mana + Cooldown aus dem Item lesen (Fallback: 50 Mana, 5s)
        Integer manaCostObj = pdc.get(keys.ABILITY_MANA, PersistentDataType.INTEGER);
        int manaCost = manaCostObj != null ? manaCostObj : 50;

        Double cdObj = pdc.get(keys.ABILITY_COOLDOWN, PersistentDataType.DOUBLE);
        double cooldownSec = cdObj != null ? cdObj : 5.0;

        // Cooldown checken
        if (isOnCooldown(p, cooldownSec)) {
            return;
        }

        // Mana checken
        if (!mana.consume(p, manaCost)) {
            p.sendMessage("§cNicht genug Mana! (§b" + manaCost + "§c benötigt)");
            return;
        }

        // Cooldown setzen
        setCooldown(p);

        // Pfeil spawnen
        shootExplosiveArrow(p);

        // Kleiner Effekt
        Location eye = p.getEyeLocation();
        p.getWorld().spawnParticle(Particle.CRIT, eye, 10, 0.2, 0.2, 0.2, 0.1);
        p.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
    }

    private boolean isOnCooldown(Player p, double cooldownSec) {
        if (cooldownSec <= 0) return false;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long cdMillis = (long) (cooldownSec * 1000);

        Long last = cooldowns.get(id);
        if (last != null && now - last < cdMillis) {
            double remain = (cdMillis - (now - last)) / 1000.0;
            p.sendMessage("§cAbility noch §e" + String.format(Locale.US, "%.1f", remain) + "s §cauf Cooldown.");
            return true;
        }
        return false;
    }

    private void setCooldown(Player p) {
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private void shootExplosiveArrow(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        p.getWorld().spawn(eye.add(dir.multiply(0.4)), Arrow.class, proj -> {
            proj.setShooter(p);
            proj.setVelocity(dir.multiply(3.0)); // Speed des Pfeils
            proj.setCritical(true);
            proj.setGravity(true);
            proj.addScoreboardTag(TAG); // Marker für unsere Ability
            proj.setDamage(0.0);        // Vanilla-Damage aus
        });
    }

    // ----------------------------------------------------
    // 3) Pfeil schlägt ein → Explosion + AoE-Damage
    // ----------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (!arrow.getScoreboardTags().contains(TAG)) return; // nur unsere Ability-Pfeile

        if (!(arrow.getShooter() instanceof Player shooter)) return;

        Location loc = arrow.getLocation();
        var world = loc.getWorld();
        if (world == null) return;

        // Pfeil entfernen
        arrow.remove();

        // Visuelle Explosion (kein Blockschaden)
        world.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        PlayerStats atk = statsManager.getStats(shooter);
        if (atk == null) return;

        // Schaden wie beim normalen Hit
        boolean crit = rollCrit(atk.getCritChance());
        double damage = atk.getDamage() * 2;
        if (crit) {
            damage *= (atk.getCritDamage() / 100.0);
        }

        // Damage-Boost Fähigkeit berücksichtigen (falls aktiv)
        UUID aid = shooter.getUniqueId();
        if (DamageBoostState.isActive(aid)) {
            damage *= DamageBoostState.getMultiplier(aid);
        }

        double radius = 3.0; // AoE-Radius

        for (Entity ent : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(ent instanceof LivingEntity target)) continue;
            if (ent.equals(shooter)) continue; // sich selbst nicht treffen

            applyAbilityDamage(shooter, target, damage, crit);
        }
    }

    private boolean rollCrit(double critChancePercent) {
        if (critChancePercent <= 0) return false;
        return ThreadLocalRandom.current().nextDouble(0.0, 100.0) < critChancePercent;
    }

    /**
     * Schaden anwenden:
     * - Spieler: über dein Custom-HP-System (PlayerStats.currentHealth)
     * - Mobs (auch Custom-Mobs): direkt Health runter + Name/HP updaten
     * - DamageNumbers anzeigen
     */
    private void applyAbilityDamage(Player attacker, LivingEntity victim, double damage, boolean crit) {
        if (damage <= 0.0) return;

        if (victim instanceof Player def) {
            return;

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
                    victim.setHealth(0);
                } else {
                    customMobManager.setCurrentHp(le, newHp);

                    // kleine Hurt-Animation
                    le.playHurtAnimation(1.0f);
                }

                customMobManager.updateHpName(le);

             } else {
                double before = victim.getHealth();
                double after = Math.max(0.0, before - damage);
                victim.setHealth(after);

                // HP-Name bei Custom-Mobs aktualisieren
                if (customMobManager != null) {
                    customMobManager.updateHpName(victim);
                }
            }
        }

        // Damage Numbers anzeigen (wie im DamageListener)
        var rand = ThreadLocalRandom.current();
        double xOff = (rand.nextDouble() - 0.5) * 0.6;
        double zOff = (rand.nextDouble() - 0.5) * 0.6;

        Location base = victim.getLocation().add(0, victim.getHeight(), 0);
        base.add(xOff, 0, zOff);

        numbers.show(victim.getWorld(), base, damage, crit);
    }
    private double applyArmor(double dmg, double armor) {
        if (armor <= 0) return dmg;
        // Beispiel-Formel: je mehr Armor, desto weniger Damage, aber mit abnehmendem Nutzen
        double reduction = armor / (armor + 1000.0); // 1000 = "Skalierungs-Konstante"
        double finalDmg = dmg * (1.0 - reduction);
        return Math.max(0.0, finalDmg);
    }


}
