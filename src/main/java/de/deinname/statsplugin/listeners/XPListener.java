package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerLevel;
import de.deinname.statsplugin.StatsManager;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class XPListener implements Listener {
    private final StatsManager statsManager;

    public XPListener(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        // Nur wenn ein Spieler getötet hat
        Player killer = event.getEntity().getKiller();

        if (killer == null) {
            return;
        }

        // Debug
        killer.sendMessage("§7[Debug] Du hast " + event.getEntityType() + " getötet!");

        // Vanilla XP deaktivieren (wir haben unser eigenes System)
        event.setDroppedExp(0);

        // XP basierend auf Mob-Typ
        double xp = getXPForEntity(event.getEntity());

        killer.sendMessage("§7[Debug] XP-Wert: " + xp);

        if (xp > 0) {
            PlayerLevel level = statsManager.getLevel(killer);

            if (level == null) {
                killer.sendMessage("§c[Debug] FEHLER: Level ist null!");
                return;
            }

            killer.sendMessage("§7[Debug] Füge " + xp + " XP hinzu...");
            level.addXP(xp);
        }
    }

    // Berechnet XP-Wert für verschiedene Mobs
    private double getXPForEntity(LivingEntity entity) {
        // Basis-XP nach Mob-Typ
        return switch (entity.getType()) {
            // Schwache Mobs
            case CHICKEN, COW, PIG, SHEEP, RABBIT -> 5.0;

            // Normale Mobs
            case ZOMBIE, SKELETON, SPIDER, CAVE_SPIDER -> 10.0;
            case CREEPER -> 15.0;

            // Stärkere Mobs
            case ENDERMAN, WITCH -> 20.0;
            case BLAZE, GHAST -> 25.0;

            // Elite Mobs
            case WITHER_SKELETON -> 30.0;
            case PIGLIN_BRUTE -> 35.0;

            // Mini-Bosses
            case RAVAGER -> 50.0;
            case ELDER_GUARDIAN -> 75.0;

            // Bosses
            case WARDEN -> 150.0;
            case ENDER_DRAGON -> 500.0;
            case WITHER -> 400.0;

            // Spieler (PvP)
            case PLAYER -> {
                Player victim = (Player) entity;
                PlayerLevel victimLevel = statsManager.getLevel(victim);
                if (victimLevel != null) {
                    yield victimLevel.getLevel() * 10.0;
                }
                yield 50.0; // Fallback wenn Level null ist
            }

            // Alle anderen
            default -> 5.0;
        };
    }
}