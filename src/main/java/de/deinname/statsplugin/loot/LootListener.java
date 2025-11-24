package de.deinname.statsplugin.loot;

import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.mobs.CustomMobType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class LootListener implements Listener {

    private final JavaPlugin plugin;
    private final LootTableManager loot;
    private final CustomMobManager customMobManager;

    public LootListener(JavaPlugin plugin,
                        LootTableManager loot,
                        CustomMobManager customMobManager) {
        this.plugin = plugin;
        this.loot = loot;
        this.customMobManager = customMobManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();

        String tableName = null;

        // 1) Custom-Mob checken
        if (customMobManager != null) {
            CustomMobType type = customMobManager.getType(dead);
            if (type != null) {
                String mobId = type.getId(); // ggf. anpassen (id() etc.)

                // Debug: in Konsole sehen, welche ID der Mob hat
                plugin.getLogger().info("[Loot] Custom-Mob gestorben: " + mobId);

                tableName = loot.getTableForCustomMobId(mobId);

                if (tableName == null) {
                    plugin.getLogger().info("[Loot] Keine Loot-Tabelle für Custom-Mob-ID: " + mobId);
                } else {
                    plugin.getLogger().info("[Loot] Nutze Tabelle '" + tableName + "' für Custom-Mob " + mobId);
                }
            }
        }

        // 2) Wenn keine Custom-Mob-Tabelle gefunden → Vanilla-Mob Mapping versuchen
        if (tableName == null) {
            String vanillaTable = loot.getTableForVanilla(dead.getType());
            if (vanillaTable != null) {
                tableName = vanillaTable;
                plugin.getLogger().info("[Loot] Nutze Vanilla-Tabelle '" + tableName + "' für " + dead.getType());
            }
        }

        // Wenn WEDER Custom noch Vanilla-Tabelle → nichts machen (Vanilla-Drops bleiben)
        if (tableName == null) {
            return;
        }

        // 3) Loot generieren
        List<ItemStack> items = loot.generate(tableName);

        // 4) Vanilla-Drops IMMER entfernen, wenn wir eine Tabelle gefunden haben
        e.getDrops().clear();
        e.setDroppedExp(0); // optional: keine Vanilla-XP

        if (items.isEmpty()) {
            // Debug: Tabelle existiert, aber keine Items generiert
            plugin.getLogger().info("[Loot] Tabelle '" + tableName + "' hat nichts generiert.");
            return;
        }

        e.getDrops().addAll(items);
        plugin.getLogger().info("[Loot] " + items.size() + " Items aus Tabelle '" + tableName + "' gedroppt.");
    }
}
