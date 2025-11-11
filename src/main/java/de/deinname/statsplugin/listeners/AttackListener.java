package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.util.DamageNumbers;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;

/**
 * Leichter, nicht-invasiver Attack-Listener:
 * - KEINE Schadensberechnung (die macht ausschließlich der DamageListener).
 * - KEIN Vanilla-Damage-Bypass (allowOnce() wurde entfernt).
 * - Nur kleines Feedback beim Armschwung.
 *
 * Vorteil: Keine Konflikte/Doppelhits, keine Abhängigkeit zu DamageListener-Interna.
 * Wenn du später wieder Reichweiten-Raycast etc. auf den Armschwung packen willst,
 * kannst du das hier sauber nachrüsten, ohne in die Schadenslogik einzugreifen.
 */
public class AttackListener implements Listener {

    private final StatsManager statsManager;
    private final DamageNumbers numbers;

    public AttackListener(StatsManager statsManager, DamageNumbers numbers) {
        this.statsManager = statsManager;
        this.numbers = numbers;
    }

    /**
     * Reagiert auf den Armschwung des Spielers.
     * Aktuell nur leichtes Feedback, greift NICHT in die Schadenslogik ein.
     */
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();

        // Nur wenn Spieler NICHT gerade blockt oder einen Block abbaut
        if (p.isBlocking() || p.getTargetBlockExact(4) != null && p.getTargetBlockExact(4).getType().isSolid()) return;

        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1.0, 0), 1, 0.1, 0.1, 0.1, 0.0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 1.2f);
    }
}
