package de.deinname.statsplugin.dungeons;

import de.deinname.statsplugin.mobs.CustomMobManager;
import de.deinname.statsplugin.mobs.CustomMobType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import de.deinname.statsplugin.loot.LootTableManager;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DungeonManager {

    private final JavaPlugin plugin;
    private final World world;
    private int nextInstanceX;
    private final int INSTANCE_SIZE = 500;
    private final LootTableManager lootManager;
    private final NamespacedKey KEY_DUNGEON_CHEST;
    private final NamespacedKey KEY_DUNGEON_CHEST_ROLLED;

    private final Map<Integer, Integer> instances = new HashMap<>();
    private int nextInstanceId = 1;

    private final SchematicPaste dungeonTemplate;
    private final int TEMPLATE_Y = 80; // Höhe, auf der der Dungeon eingefügt wird

    private final CustomMobManager customMobManager;

    public DungeonManager(JavaPlugin plugin, CustomMobManager customMobManager, LootTableManager lootManager) {
        this.plugin = plugin;
        this.customMobManager = customMobManager;
        this.lootManager = lootManager;
        this.world = Bukkit.getWorld("dungeons_world");

        if (world == null) {
            throw new IllegalStateException("Dungeon-Welt existiert nicht!");
        }

        this.KEY_DUNGEON_CHEST = new NamespacedKey(plugin, "dungeon_loot_table");
        this.KEY_DUNGEON_CHEST_ROLLED = new NamespacedKey(plugin, "dungeon_loot_rolled");
        // Schematic laden aus WorldEdit-Ordner
        File schematicFile = new File("plugins/FastAsyncWorldEdit/schematics/dungeon.schem");

        plugin.getLogger().info("Lade Schematic von: " + schematicFile.getAbsolutePath());

        if (!schematicFile.exists()) {
            throw new IllegalStateException("Dungeon-Schematic nicht gefunden!");
        }

        try {
            this.dungeonTemplate = new SchematicPaste(schematicFile);
        } catch (Exception ex) {
            throw new IllegalStateException("Konnte Dungeon-Schematic nicht laden!", ex);
        }
    }

    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public void saveReturnLocation(Player p) {
        returnLocations.put(p.getUniqueId(), p.getLocation());
    }

    public void returnPlayer(Player p) {
        Location loc = returnLocations.remove(p.getUniqueId());
        if (loc != null) p.teleport(loc);
    }



    public int createInstance() {
        int id = nextInstanceId++;
        int baseX = nextInstanceX;
        nextInstanceX += INSTANCE_SIZE;

        instances.put(id, baseX);

        plugin.getLogger().info("Erstelle Dungeon-Instanz #" + id + " bei X=" + baseX);

        // Dungeon pasten
        try {
            dungeonTemplate.paste(world, baseX, 80, 0);
        } catch (Exception ex) {
            ex.printStackTrace();
            return id;
        }

        // DANACH: Kisten im Bereich markieren
        markDungeonChests(baseX, 80, 0, INSTANCE_SIZE, 80);

        // --- Marker scannen ---
        scanAndSpawn(baseX, 80, 0);

        return id;
    }


    public Location getSpawnPoint(int instanceId) {
        Integer baseX = instances.get(instanceId);
        if (baseX == null) return null;

        // Hier spawnst du Spieler z.B. im Start-Raum des gedupten Dungeons
        return new Location(world, baseX + 5, TEMPLATE_Y + 1, 5);
    }

    public void teleportToInstance(Player p, int instanceId) {
        Location loc = getSpawnPoint(instanceId);
        if (loc != null) {
            p.teleport(loc);
        } else {
            p.sendMessage("§cDungeon-Instanz nicht gefunden.");
        }
    }

    private void scanAndSpawn(int baseX, int baseY, int baseZ) {
        if (world == null) {
            plugin.getLogger().warning("[Dungeon] scanAndSpawn: world == null!");
            return;
        }

        int size = INSTANCE_SIZE;

        plugin.getLogger().info("[Dungeon] Scanne Instanz-Bereich X=[" + baseX + ";" + (baseX + size) +
                "], Z=[" + baseZ + ";" + (baseZ + size) + "], Y=[" + baseY + ";" + (baseY + 200) + "]");

        int mobMarkers = 0;
        int chestMarkers = 0;
        int bossMarkers = 0;

        for (int x = baseX; x < baseX + size; x++) {
            for (int y = baseY; y < baseY + 200; y++) {
                for (int z = baseZ; z < baseZ + size; z++) {

                    var block = world.getBlockAt(x, y, z);
                    var type = block.getType();

                    switch (type) {

                        // === MOB-MARKER: EMERALD_BLOCK ===
                        case WHITE_SHULKER_BOX -> {
                            mobMarkers++;

                            // Marker löschen
                            block.setType(org.bukkit.Material.AIR, false);

                            // Beispiel: Zombie Warrior Level 5
                            spawnMobHere(CustomMobType.ZOMBIE_WARRIOR, 5, x, y, z);
                        }


                        case RED_SHULKER_BOX -> {
                            mobMarkers++;

                            // Marker löschen
                            block.setType(org.bukkit.Material.AIR, false);

                            // Beispiel: Zombie Warrior Level 5
                            spawnBossHere(CustomMobType.SKELETON_PRINCE, 10, x, y, z);
                        }

                        default -> {
                            // andere Blöcke ignorieren
                        }
                    }
                }
            }
        }
    }


    private void spawnMobHere(CustomMobType type, int level, int x, int y, int z) {
        if (type == null) {
            plugin.getLogger().warning("[Dungeon] spawnMobHere: type == null bei " + x + "," + y + "," + z);
            return;
        }
        if (world == null) {
            plugin.getLogger().warning("[Dungeon] spawnMobHere: world == null!");
            return;
        }

        // leicht zentrieren + 1 Block über Marker
        org.bukkit.Location loc = new org.bukkit.Location(
                world,
                x + 0.5,
                y + 1,
                z + 0.5
        );

        plugin.getLogger().info("[Dungeon] Spawne Mob " + type.getId() +
                " Lv." + level + " bei " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());

        var mob = customMobManager.spawnMob(type, level, loc);
        if (mob == null) {
            plugin.getLogger().warning("[Dungeon] spawnMobHere: spawnMob() hat null zurückgegeben!");
        }
    }





    private void spawnBossHere(CustomMobType type, int level, int x, int y, int z) {
        if (type == null) {
            return;
        }
        if (world == null) {
            return;
        }

        // leicht zentrieren + 1 Block über Marker
        org.bukkit.Location loc = new org.bukkit.Location(
                world,
                x + 0.5,
                y + 1,
                z + 0.5
        );
        var mob = customMobManager.spawnMob(type, level, loc);
        if (mob == null) {
            plugin.getLogger().warning("[Dungeon] spawnMobHere: spawnMob() hat null zurückgegeben!");
        }
        //Location loc = new Location(world, x, y, z);
        //bossManager.spawnRichard(loc);
    }


    private void markDungeonChests(int baseX, int baseY, int baseZ, int sizeX, int sizeZ) {
        // Hier grob den Bereich abscannen – Y-Range je nach Dungeon-Höhe anpassen
        int minY = baseY - 20;
        int maxY = baseY + 40;

        int chests = 0;

        for (int x = baseX; x < baseX + sizeX; x++) {
            for (int z = baseZ; z < baseZ + sizeZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!(b.getState() instanceof Chest chest)) continue;

                    // Dungeon-Chest markieren → z.B. Loot-Tabelle "dungeon_basic"
                    PersistentDataContainer pdc = chest.getPersistentDataContainer();
                    pdc.set(KEY_DUNGEON_CHEST, PersistentDataType.STRING, "dungeon_basic");
                    chest.update(); // BlockState zurückschreiben

                    chests++;
                }
            }
        }

        plugin.getLogger().info("[Dungeon] Markierte " + chests + " Dungeon-Kisten.");
    }

    public NamespacedKey getDungeonChestKey() {
        return KEY_DUNGEON_CHEST;
    }

    public NamespacedKey getDungeonChestRolledKey() {
        return KEY_DUNGEON_CHEST_ROLLED;
    }

}
