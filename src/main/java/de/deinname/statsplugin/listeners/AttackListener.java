package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.combat.CustomReach;
import de.deinname.statsplugin.util.DamageNumbers;
import de.deinname.statsplugin.PlayerStats;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Nicht-invasiv + Extended-Reach:
 * - Vanilla-Hits <~4.5 Blöcke laufen ganz normal über EntityDamageByEntityEvent.
 * - Für Treffer >4.5 bis zum Stat-Range machen wir einen Raycast beim Swing
 *   und setzen ein einmaliges Override-Flag (CustomReach.allowOnce).
 *   Dann darf der DamageListener DIESEN Treffer ohne Range-Abbruch verarbeiten.
 */
public class AttackListener implements Listener {

    private final StatsManager statsManager;
    private final DamageNumbers numbers;

    // Grenzwert: Alles darüber ist "Extended" und braucht unser Override
    private static final double VANILLA_REACH_FUZZ = 4.5;

    public AttackListener(StatsManager statsManager, DamageNumbers numbers) {
        this.statsManager = statsManager;
        this.numbers = numbers;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();

        // Optional: Kein Swing-Feedback beim Blockbauen
        // (einfacher Heuristik-Check)
        // if (p.getTargetBlockExact(4) != null) return;

        // Stat-Range holen
        PlayerStats s = statsManager.getStats(p);
        if (s == null) return;
        double range = Math.max(0.0, s.getRange());
        if (range <= VANILLA_REACH_FUZZ) return; // alles <= ~4.5 handled Vanilla

        // Raycast bis zu unserem Stat-Range
        Vector dir = p.getEyeLocation().getDirection();
        RayTraceResult hit = p.getWorld().rayTrace(
                p.getEyeLocation(),
                dir,
                range,
                FluidCollisionMode.NEVER,
                true, // ignore passable
                0.2,  // entity size margin
                entity -> entity instanceof LivingEntity && entity != p
        );

        if (hit == null) return;
        Entity hitEntity = hit.getHitEntity();
        if (!(hitEntity instanceof LivingEntity target)) return;

        double dist = p.getEyeLocation().distance(hit.getHitPosition().toLocation(p.getWorld()));
        if (dist <= VANILLA_REACH_FUZZ) {
            // innerhalb Vanilla → nichts tun; normale Mechanics/Event reichen
            return;
        }

        // Jetzt kommt ein Extended-Treffer:
        // -> DamageListener soll Range NICHT abbrechen, sondern verarbeiten.
        CustomReach.allowOnce(p.getUniqueId(), target);

        // Kleines Feedback (optional)
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1.0, 0), 1, 0.1, 0.1, 0.1, 0.0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 1.1f);

        // WICHTIG: Wir lösen KEINEN Schaden hier aus.
        // Der eigentliche Schaden passiert zentral in DamageListener
        // (durch den normalen EntityDamageByEntityEvent auf Vanilla-Reach NICHT,
        //  aber bei Extended trifft Vanilla nicht automatisch; deswegen:
        //  Wir "simulieren" einen Hit, indem wir target.damage(0, p) triggern,
        //  so dass das Event läuft und unser DamageListener drankommt.)
        target.damage(0.0, p); // feuert EntityDamageByEntityEvent mit Angreifer p
    }
}
