package de.deinname.statsplugin.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LootTableManager {

    private final JavaPlugin plugin;

    // Tabellen: Name -> LootTable
    private final Map<String, LootTable> tables = new HashMap<>();
    // Mapping: Vanilla-Mob -> Tabellen-Name
    private final Map<EntityType, String> mobTables = new HashMap<>();
    // Mapping: Custom-Mob-ID -> Tabellen-Name
    private final Map<String, String> customMobTables = new HashMap<>();

    public LootTableManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        tables.clear();
        mobTables.clear();
        customMobTables.clear();

        var cfg = plugin.getConfig();

        // --- Tabellen laden ---
        ConfigurationSection tblRoot = cfg.getConfigurationSection("loot.tables");
        if (tblRoot != null) {
            for (String name : tblRoot.getKeys(false)) {
                ConfigurationSection sec = tblRoot.getConfigurationSection(name);
                if (sec == null) continue;

                int rolls = sec.getInt("rolls", 1);
                List<LootEntry> entries = new ArrayList<>();

                var list = sec.getMapList("entries");
                for (Map<?, ?> raw : list) {
                    String template = asString(raw.get("template"));
                    int weight       = asInt(raw.get("weight"), 1);
                    int min          = asInt(raw.get("min"), 1);
                    int max          = asInt(raw.get("max"), min);

                    if (template == null || weight <= 0) continue;
                    if (max < min) max = min;

                    entries.add(new LootEntry(template, weight, min, max));
                }

                if (!entries.isEmpty()) {
                    tables.put(name, new LootTable(name, rolls, entries));
                    if (!entries.isEmpty()) {
                        LootTable table = new LootTable(name, rolls, entries);
                        tables.put(name, table);
                    }

                }
            }
        }

        // --- Vanilla-Mobs -> Tabelle ---
        ConfigurationSection mobSec = cfg.getConfigurationSection("loot.mobs");
        if (mobSec != null) {
            for (String key : mobSec.getKeys(false)) {
                String tableName = mobSec.getString(key);
                if (tableName == null || !tables.containsKey(tableName)) continue;
                try {
                    EntityType type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
                    mobTables.put(type, tableName);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("[Loot] Unbekannter EntityType in loot.mobs: " + key);
                }
            }
        }

        // --- Custom-Mobs -> Tabelle ---
        ConfigurationSection cmSec = cfg.getConfigurationSection("loot.custom_mobs");
        if (cmSec != null) {
            for (String mobId : cmSec.getKeys(false)) {
                String tableName = cmSec.getString(mobId);
                if (tableName == null || !tables.containsKey(tableName)) {
                    plugin.getLogger().warning("[Loot] loot.custom_mobs." + mobId +
                            " verweist auf unbekannte Tabelle: " + tableName);
                    continue;
                }
                customMobTables.put(mobId, tableName);
            }
        }


        plugin.getLogger().info("[Loot] Tabellen geladen: " + tables.size()
                + ", Vanilla-Mobs: " + mobTables.size()
                + ", Custom-Mobs: " + customMobTables.size());
    }

    private String asString(Object o) {
        return (o instanceof String s && !s.isEmpty()) ? s : null;
    }

    private int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    // ---- API ----

    public String getTableForVanilla(EntityType type) {
        return mobTables.get(type);
    }

    public String getTableForCustomMobId(String mobId) {
        if (mobId == null) return null;
        return customMobTables.get(mobId);
    }

    /**
     * Erzeugt Loot basierend auf einer Tabellen-ID.
     * Nutzt Item-Vorlagen aus config: saved-items.<template>
     */
    public List<ItemStack> generate(String tableName) {
        LootTable table = tables.get(tableName);
        if (table == null) {
            plugin.getLogger().warning("[Loot] Tabelle nicht gefunden: " + tableName);
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        plugin.getLogger().info("[Loot] Generate für Tabelle '" + tableName +
                "' (rolls=" + table.rolls() + ", entries=" + table.entries().size() + ")");

        for (int roll = 0; roll < table.rolls(); roll++) {
            LootEntry entry = pickWeighted(table.entries(), rnd);
            if (entry == null) {
                plugin.getLogger().warning("[Loot] Roll " + roll + " ergab null-Entry.");
                continue;
            }

            int amount = randomBetween(entry.min(), entry.max(), rnd);
            plugin.getLogger().info("[Loot] Roll " + roll + " → template=" + entry.template()
                    + " amount=" + amount);

            ItemStack base = plugin.getConfig().getItemStack("saved-items." + entry.template());
            if (base == null) {
                plugin.getLogger().warning("[Loot] Template nicht gefunden oder null: saved-items." + entry.template());
                continue;
            }

            plugin.getLogger().info("[Loot] Base-Item: type=" + base.getType()
                    + " amount=" + base.getAmount());

            for (int i = 0; i < amount; i++) {
                result.add(base.clone());
            }
        }

        plugin.getLogger().info("[Loot] Generate('" + tableName + "') → result.size=" + result.size());
        return result;
    }


    private int randomBetween(int min, int max, Random rnd) {
        if (max <= min) return min;
        return min + rnd.nextInt(max - min + 1);
    }

    private LootEntry pickWeighted(List<LootEntry> entries, Random rnd) {
        int total = entries.stream().mapToInt(LootEntry::weight).sum();
        if (total <= 0) return null;
        int r = rnd.nextInt(total);
        int acc = 0;
        for (LootEntry e : entries) {
            acc += e.weight();
            if (r < acc) return e;
        }
        return entries.get(entries.size() - 1);
    }

    // --- Records für interne Struktur ---

    public record LootEntry(String template, int weight, int min, int max) {}
    public record LootTable(String name, int rolls, List<LootEntry> entries) {}
}
