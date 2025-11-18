package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.PlayerLevel;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.mobs.CustomMobType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Vergibt konfigurierbare XP beim Kill und deaktiviert Vanilla-EXP komplett.
 *
 * Priorität:
 *  1) Custom-Mob mit PDC (mmomob_id/level/xp) → XP aus CustomMobManager + hartes remove()
 *  2) exakter Eintrag in xp.mobs (z.B. WITCH)
 *  3) Gruppen-Fallback (HOSTILE / PASSIVE / NEUTRAL), wenn vorhanden
 *  4) xp.mobs.default
 *
 * Anzeige im Chat ist konfigurierbar (xp.announce.chat).
 */
public class XPListener implements Listener {

    private final JavaPlugin plugin;
    private final StatsManager stats;
    private final CustomMobManager mobManager;

    public XPListener(JavaPlugin plugin, StatsManager stats, CustomMobManager mobManager) {
        this.plugin = plugin;
        this.stats = stats;
        this.mobManager = mobManager;
    }

    // 1) Vanilla-EXP (grüne Orbs / Player-Level) komplett ausschalten
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerExp(PlayerExpChangeEvent e) {
        // verhindert, dass der Spieler Vanilla-EXP gutgeschrieben bekommt
        e.setAmount(0);
    }

    // 2) Eigene XP-Vergabe
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();

        // Keine Vanilla-Orbs droppen lassen
        e.setDroppedExp(0);

        Player killer = dead.getKiller();
        if (killer == null) return; // wir vergeben hier nur bei "echtem" Killer

        int xp = resolveXp(dead);
        if (xp <= 0) return;

        // Deine Level-Logik
        PlayerLevel lvl = stats.getLevel(killer);
        lvl.addXP(xp);

        // Chat-Toast optional
        if (plugin.getConfig().getBoolean("xp.announce.chat", true)) {
            String hex = plugin.getConfig().getString("xp.announce.color", "#FFD166");
            killer.sendMessage(Component.text("+" + xp + " XP")
                    .color(TextColor.fromHexString(hex)));
        }

        // Wenn es ein Custom-Mob ist → sicher und sauber sterben lassen
        if (mobManager != null) {
            CustomMobType t = mobManager.getType(dead);
            if (t != null) {
                // Health auf 0 setzen (triggert Death-Animation)
                dead.setHealth(0.0);

                // Mob für den Rest der Phase komplett deaktivieren:
                dead.setAI(false);
                dead.setInvulnerable(true);
                dead.setSilent(true);

                // Verzögert entfernen (nach 1.5 Sekunden)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!dead.isDead()) {
                        dead.remove();
                    }
                }, 30L);
            }
        }

    }

    // ---- XP-Bestimmung: CustomMob > Config-Mapping ----

    private int resolveXp(LivingEntity entity) {
        // 1) Custom-Mob? → nimm seine eigene XP
        if (mobManager != null) {
            CustomMobType t = mobManager.getType(entity);
            if (t != null) {
                int xp = mobManager.getXp(entity);
                if (xp > 0) return xp;
            }
        }

        // 2) normales Vanilla-Mob → alte Logik nach EntityType
        return resolveXpByType(entity.getType());
    }

    private int resolveXpByType(EntityType type) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("xp.mobs");
        if (sec == null) return plugin.getConfig().getInt("xp.mobs.default", 0);

        String name = type.name(); // z.B. WITCH, ZOMBIE, SHEEP, IRON_GOLEM

        // 1) Exakter Eintrag hat Vorrang
        if (sec.isInt(name)) return sec.getInt(name);

        // 2) Gruppen-Fallback
        String group = groupOf(type);
        if (group != null && sec.isInt(group)) return sec.getInt(group);

        // 3) Default
        return plugin.getConfig().getInt("xp.mobs.default", 0);
    }

    private String groupOf(EntityType type) {
        // Hostiles
        switch (type) {
            case ZOMBIE: case HUSK: case DROWNED:
            case SKELETON: case STRAY:
            case SPIDER: case CAVE_SPIDER:
            case CREEPER:
            case ENDERMAN:
            case WITCH:
            case SLIME: case MAGMA_CUBE:
            case PHANTOM:
            case SILVERFISH:
            case ZOMBIFIED_PIGLIN:
                return "HOSTILE";
        }
        // Passive
        switch (type) {
            case COW: case SHEEP: case PIG:
            case CHICKEN: case RABBIT: case GOAT:
            case HORSE:
            case SQUID: case GLOW_SQUID:
            case TURTLE: case BEE:
                return "PASSIVE";
        }
        // Neutral / Utility (z. B. Golems, Wölfe …)
        switch (type) {
            case IRON_GOLEM:
            case SNOW_GOLEM:
            case WOLF:
            case DOLPHIN:
                return "NEUTRAL";
        }
        return null;
    }
}
