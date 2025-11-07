package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.util.DamageNumbers;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DamageListener implements Listener {
    private final StatsManager statsManager;
    private final DamageNumbers numbers;

    private static final Map<String, Long> ALLOW = new ConcurrentHashMap<>();
    private static final long ALLOW_WINDOW_MS = 1000;

    private final Map<UUID, Long> lastAttack = new ConcurrentHashMap<>();

    public DamageListener(StatsManager statsManager, DamageNumbers numbers) {
        this.statsManager = statsManager;
        this.numbers = numbers;
    }

    public static void allowOnce(UUID attacker, UUID victim) {
        ALLOW.put(attacker + "->" + victim, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof LivingEntity victim)) return;

        String key = attacker.getUniqueId() + "->" + victim.getUniqueId();
        Long ts = ALLOW.get(key);

        if (ts != null && (System.currentTimeMillis() - ts) <= ALLOW_WINDOW_MS) {
            // Hit kam vom AttackListener (Extended-Range) -> einfach durchlassen
            ALLOW.remove(key);
            return;
        }

        // Vanilla Nahkampf -> unser System übernimmt
        PlayerStats atk = statsManager.getStats(attacker);

        double aps = Math.max(0.1, atk.getAttackSpeed());
        long cooldownMs = (long) Math.ceil(1000.0 / aps);
        long now = System.currentTimeMillis();
        long last = lastAttack.getOrDefault(attacker.getUniqueId(), 0L);
        if (now - last < cooldownMs) {
            e.setCancelled(true);
            return;
        }

        double dist = attacker.getLocation().distance(e.getEntity().getLocation());
        if (dist > atk.getRange()) {
            e.setCancelled(true);
            attacker.sendMessage("§cZu weit entfernt! (Max: " +
                    String.format("%.1f", atk.getRange()) + " | Aktuell: " + String.format("%.1f", dist) + ")");
            return;
        }

        boolean crit = atk.rollCrit();
        double damage = atk.calculateDamage(crit);

        if (victim instanceof Player def) {
            var defStats = statsManager.getStats(def);
            damage = defStats.calculateDamageReduction(damage);
        }

        e.setDamage(Math.max(0.0, damage));

        // Floating numbers zeigen
        numbers.show(victim.getWorld(), victim.getLocation(), damage, crit);

        // (Optional) Feedback-Particle
        if (crit) {
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0),
                    25, 0.4, 0.4, 0.4, 0.05);
        }

        lastAttack.put(attacker.getUniqueId(), now);
    }
}
