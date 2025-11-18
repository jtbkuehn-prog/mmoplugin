package de.deinname.statsplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class DamageNumbers {
    private final JavaPlugin plugin;

    public DamageNumbers(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(World world, Location base, double amount, boolean crit) {
        if (world == null || base == null) return;

        Location loc = base.clone().add(0, 0, 0);
        TextDisplay td = world.spawn(loc, TextDisplay.class, d -> {
            String text = crit ? "✦ " + String.format("%.1f", amount) : String.format("%.1f", amount);
            d.text(Component.text(text).color(crit ? NamedTextColor.GOLD : NamedTextColor.RED));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setBackgroundColor(null); // transparent
            d.setViewRange(32.0f);
        });

        final int lifetimeTicks = 16;   // ~0.8s
        final double risePerTick = 0.05;

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!td.isValid()) { cancel(); return; }
                td.teleport(td.getLocation().add(0, risePerTick, 0));
                if (++t >= lifetimeTicks) {
                    td.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
