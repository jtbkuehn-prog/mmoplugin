package de.deinname.statsplugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public class StatsStorage {
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final Gson gson;

    public StatsStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Ordner erstellen falls nicht vorhanden
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
            plugin.getLogger().info("Playerdata Ordner erstellt: " + dataFolder.getPath());
        }
    }

    // === STATS SPEICHERN/LADEN ===

    // Stats speichern
    public void saveStats(PlayerStats stats) {
        File file = getPlayerFile(stats.getPlayerId());

        plugin.getLogger().info("Versuche Stats zu speichern für: " + stats.getPlayerId() + " nach " + file.getAbsolutePath());

        try {
            // Lade existierende Daten (falls Level schon gespeichert wurde)
            JsonObject json;
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    json = gson.fromJson(reader, JsonObject.class);
                }
            } else {
                json = new JsonObject();
            }

            // Stats-Daten hinzufügen/überschreiben
            json.addProperty("uuid", stats.getPlayerId().toString());
            json.addProperty("damage", stats.getDamage());
            json.addProperty("critChance", stats.getCritChance());
            json.addProperty("critDamage", stats.getCritDamage());
            json.addProperty("attackSpeed", stats.getAttackSpeed());
            json.addProperty("range", stats.getRange());
            json.addProperty("health", stats.getHealth());
            json.addProperty("armor", stats.getArmor());
            json.addProperty("mana", stats.getMana());
            json.addProperty("manareg", stats.getManaregen());
            json.addProperty("healthreg", stats.getHealthregen());
            json.addProperty("lastSaved", System.currentTimeMillis());

            // Speichern
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(json, writer);
            }

            plugin.getLogger().info("✓ Stats erfolgreich gespeichert für: " + stats.getPlayerId());

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Speichern der Stats für " + stats.getPlayerId(), e);
        }
    }

    // Stats laden
    public PlayerStats loadStats(UUID playerId) {
        File file = getPlayerFile(playerId);

        plugin.getLogger().info("Versuche Stats zu laden für: " + playerId + " von " + file.getAbsolutePath());

        // Wenn keine Datei existiert, neue Stats erstellen
        if (!file.exists()) {
            plugin.getLogger().info("Keine gespeicherten Stats gefunden für: " + playerId + " - Erstelle neue");
            return new PlayerStats(playerId);
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            PlayerStats stats = new PlayerStats(playerId);

            if (json.has("damage")) stats.setDamage(json.get("damage").getAsDouble());
            if (json.has("critChance")) stats.setCritChance(json.get("critChance").getAsDouble());
            if (json.has("critDamage")) stats.setCritDamage(json.get("critDamage").getAsDouble());
            if (json.has("attackSpeed")) stats.setAttackSpeed(json.get("attackSpeed").getAsDouble());
            if (json.has("range")) stats.setRange(json.get("range").getAsDouble());
            if (json.has("health")) stats.setHealth(json.get("health").getAsDouble());
            if (json.has("armor")) stats.setArmor(json.get("armor").getAsDouble());
            if (json.has("mana")) stats.setArmor(json.get("mana").getAsDouble());
            if (json.has("manaregen")) stats.setArmor(json.get("manaregen").getAsDouble());
            if (json.has("healthregen")) stats.setArmor(json.get("healthregen").getAsDouble());

            plugin.getLogger().info("✓ Stats erfolgreich geladen für: " + playerId);
            return stats;

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Stats für " + playerId, e);
            return new PlayerStats(playerId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Korrupte Stats-Datei für " + playerId + " - Erstelle neue", e);
            return new PlayerStats(playerId);
        }
    }

    // === LEVEL SPEICHERN/LADEN ===

    // Level speichern
    public void saveLevel(PlayerLevel level, UUID playerId) {
        File file = getPlayerFile(playerId);

        plugin.getLogger().info("Versuche Level zu speichern für: " + playerId);

        try {
            // Lade existierende Daten (falls Stats schon gespeichert wurden)
            JsonObject json;
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    json = gson.fromJson(reader, JsonObject.class);
                }
            } else {
                json = new JsonObject();
                json.addProperty("uuid", playerId.toString());
            }

            // Level-Daten hinzufügen/überschreiben
            json.addProperty("level", level.getLevel());
            json.addProperty("xp", level.getXP());
            json.addProperty("skillPoints", level.getSkillPoints());
            json.addProperty("lastSaved", System.currentTimeMillis());

            // Speichern
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(json, writer);
            }

            plugin.getLogger().info("✓ Level erfolgreich gespeichert für: " + playerId);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Speichern des Levels für " + playerId, e);
        }
    }

    // Level laden
    public void loadLevel(PlayerLevel level, UUID playerId) {
        File file = getPlayerFile(playerId);

        plugin.getLogger().info("Versuche Level zu laden für: " + playerId);

        if (!file.exists()) {
            plugin.getLogger().info("Kein Level gefunden für: " + playerId + " - Nutze Default (Level 1)");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            if (json.has("level")) {
                level.setLevel(json.get("level").getAsInt());
            }
            if (json.has("xp")) {
                level.setXP(json.get("xp").getAsDouble());
            }
            if (json.has("skillPoints")) {
                level.setSkillPoints(json.get("skillPoints").getAsInt());
            }

            plugin.getLogger().info("✓ Level erfolgreich geladen für: " + playerId);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden des Levels für " + playerId, e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Korrupte Level-Daten für " + playerId + " - Nutze Defaults", e);
        }
    }

    // === HILFSMETHODEN ===

    // Stats löschen
    public boolean deleteStats(UUID playerId) {
        File file = getPlayerFile(playerId);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                plugin.getLogger().info("Stats gelöscht für: " + playerId);
            }
            return deleted;
        }
        return false;
    }

    // Prüft ob Stats existieren
    public boolean hasStats(UUID playerId) {
        return getPlayerFile(playerId).exists();
    }

    // Gibt die Datei für einen Spieler zurück
    private File getPlayerFile(UUID playerId) {
        return new File(dataFolder, playerId.toString() + ".json");
    }

    // Alle Stats speichern
    public void saveAll(StatsManager manager) {
        int count = 0;
        for (PlayerStats stats : manager.getAllStats()) {
            saveStats(stats);
            PlayerLevel lvl = manager.getLevel(stats.getPlayerId());
            if (lvl != null) {
                saveLevel(lvl, stats.getPlayerId());
            }
            count++;
        }
        plugin.getLogger().info("[Storage] Alle Stats & Level gespeichert (" + count + " Spieler)");
    }

}