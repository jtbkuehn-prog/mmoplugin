package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import io.papermc.paper.event.player.PlayerArmSwingEvent; // <-- Paper API
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import de.deinname.statsplugin.util.DamageNumbers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AttackListener implements Listener {
    private final StatsManager statsManager;
    private final DamageNumbers numbers;
    private final Map<UUID, Long> lastAttack = new HashMap<>();

    public AttackListener(StatsManager statsManager, DamageNumbers numbers) {
        this.statsManager = statsManager;
        this.numbers = numbers;
    }

    // 1) Linksklick (bleibt für Nahkampf)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        performAttack(event.getPlayer());
    }

    // 2) Fallback: Arm-Swing (nur Main-Hand, Paper) -> fängt auch "Luftschläge" jenseits Vanilla-Reach
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // nur Main-Hand
        Player p = event.getPlayer();
        if (isUseItem(p.getInventory().getItemInMainHand())) return; // Spawn-Eier, Bogen, etc. ignorieren
        performAttack(p);
    }

    private boolean isUseItem(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        if (m.name().endsWith("_SPAWN_EGG")) return true;
        return switch (m) {
            case ENDER_PEARL, SNOWBALL, EGG, SPLASH_POTION, LINGERING_POTION,
                 BOW, CROSSBOW, TRIDENT, FISHING_ROD, FLINT_AND_STEEL -> true;
            default -> false;
        };
    }

    private void performAttack(Player player) {
        PlayerStats stats = statsManager.getStats(player);

        // Cooldown über AttackSpeed
        double aps = Math.max(0.1, stats.getAttackSpeed());
        long cooldownMs = (long) Math.ceil(1000.0 / aps);
        long now = System.currentTimeMillis();
        long last = lastAttack.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownMs) return;

        // Raycast bis zu deiner Custom-Range
        Vector dir = player.getEyeLocation().getDirection();
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), dir, stats.getRange(), 0.5,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );
        if (result == null || result.getHitEntity() == null) return;

        Entity target = result.getHitEntity();
        boolean isCrit = stats.rollCrit();
        double damage = stats.calculateDamage(isCrit);

        if (target instanceof LivingEntity living) {
            // Den nachfolgenden Damage-Event durch den DamageListener passieren lassen
            DamageListener.allowOnce(player.getUniqueId(), living.getUniqueId());
            living.damage(damage, player);

            numbers.show(living.getWorld(), living.getLocation(), damage, isCrit);

            if (isCrit) {
                player.sendMessage("§e§l⚡ CRITICAL HIT! §f" + String.format("%.1f", damage));
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                        30, 0.5, 0.5, 0.5, 0.1);
            } else {
                player.sendMessage("§7Hit §f" + String.format("%.1f", damage) +
                        " §7(APS: " + String.format("%.1f", aps) + ")");
            }
            lastAttack.put(player.getUniqueId(), now);
        }
    }
}
