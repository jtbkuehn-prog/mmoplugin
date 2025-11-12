package de.deinname.statsplugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.entity.Player;

/**
 * Deaktiviert Vanilla-Hunger & natürliche Regeneration vollständig.
 * Eigene Regeneration läuft über den Plugin-Tick.
 */
public class FoodAndRegenListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        e.setCancelled(true);
        // fixiere auf voll (Hunger aus)
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        // NATURAL/SATIATED/REGEN ect. blocken — unsere eigene Regeneration übernimmt das
        switch (e.getRegainReason()) {
            case SATIATED:
            case REGEN:
            case MAGIC_REGEN:
            case EATING:
            case ENDER_CRYSTAL:
            case WITHER_SPAWN:
                e.setCancelled(true);
                break;
            default:
                // andere Gründe (z.B. Instant-Health-Tränke) kannst du erlauben;
                // wenn ALLES blockiert werden soll: e.setCancelled(true);
                break;
        }
    }
}
