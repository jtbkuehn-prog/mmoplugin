package de.deinname.statsplugin.abilities;


import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;


import java.util.*;


public class DamageBoostState implements Listener {
    private static final Map<UUID, Double> MULT = new HashMap<>();


    public static void activate(Plugin plugin, UUID id, int durationTicks, double multiplier){
        MULT.put(id, multiplier);
        Bukkit.getScheduler().runTaskLater(plugin, () -> MULT.remove(id), durationTicks);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e){
        if (!(e.getDamager() instanceof org.bukkit.entity.Player p)) return;
        Double m = MULT.get(p.getUniqueId());
        if (m == null) return;
        e.setDamage(e.getDamage() * m);
    }
}