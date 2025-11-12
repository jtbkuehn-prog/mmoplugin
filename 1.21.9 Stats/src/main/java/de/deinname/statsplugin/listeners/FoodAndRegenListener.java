package de.deinname.statsplugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/** Deaktiviert Hunger & Vanilla-Regeneration. Eigene Reg läuft im Plugin-Tick. */
public class FoodAndRegenListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        e.setCancelled(true);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        switch (e.getRegainReason()) {
            case SATIATED:
            case REGEN:
            case MAGIC_REGEN:
            case EATING:
                e.setCancelled(true);
                break;
            default:
                // Tränke etc. dürfen weiter wirken. Wenn alles blocken: e.setCancelled(true);
        }
    }
}
