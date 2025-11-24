package de.deinname.statsplugin.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class BowForceListener implements Listener {

    private final NamespacedKey KEY_BOW_FORCE;

    public BowForceListener(JavaPlugin plugin) {
        this.KEY_BOW_FORCE = new NamespacedKey(plugin, "bow_force");
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!(e.getProjectile() instanceof Arrow arrow)) return;

        double force = e.getForce(); // 0.0 .. 1.0

        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.set(KEY_BOW_FORCE, PersistentDataType.FLOAT, (float) force);
    }
}
