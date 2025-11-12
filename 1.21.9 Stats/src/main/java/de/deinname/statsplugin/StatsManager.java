package de.deinname.statsplugin;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class StatsManager {
    private final Map<UUID, PlayerStats> playerStats;
    private final Map<UUID, PlayerLevel> playerLevels;
    private StatsStorage storage;

    public StatsManager() {
        this.playerStats = new HashMap<>();
        this.playerLevels = new HashMap<>();
    }


    public void setStorage(StatsStorage storage) {
        this.storage = storage;
    }

    // Merker für zuletzt angewendete Item-Boni
    private static final class ItemBonus { double d, cc, cd, hp, ar, rg, as; ItemBonus(double d,double cc,double cd,double hp,double ar,double rg, double as){this.d=d;this.cc=cc;this.cd=cd;this.hp=hp;this.ar=ar;this.rg=rg;this.as=as;} }
    private final java.util.Map<java.util.UUID, ItemBonus> lastItemBonus = new java.util.HashMap<>();

    // Lädt oder erstellt Stats für einen Spieler
    public PlayerStats getStats(Player player) {
        return playerStats.computeIfAbsent(player.getUniqueId(), uuid -> {
            if (storage != null) {
                return storage.loadStats(uuid);
            }
            return new PlayerStats(uuid);
        });
    }

    // Lädt oder erstellt Level für einen Spieler
    public PlayerLevel getLevel(Player player) {
        return playerLevels.computeIfAbsent(player.getUniqueId(), uuid -> {
            PlayerStats stats = getStats(player);
            PlayerLevel level = new PlayerLevel(player, stats);
            if (storage != null) {
                storage.loadLevel(level, uuid);
            }
            level.updateXPBar();
            return level;
        });
    }

    // Stats für UUID holen
    public PlayerStats getStats(UUID playerId) {
        return playerStats.get(playerId);
    }

    // Level für UUID holen
    public PlayerLevel getLevel(UUID playerId) {
        return playerLevels.get(playerId);
    }

    // Prüft ob Spieler Stats hat
    public boolean hasStats(UUID playerId) {
        return playerStats.containsKey(playerId);
    }

    // Entfernt Stats (z.B. beim Verlassen) und speichert sie
    public void removeStats(UUID playerId) {
        PlayerStats stats = playerStats.remove(playerId);
        PlayerLevel level = playerLevels.remove(playerId);
        if (stats != null && storage != null) {
            storage.saveStats(stats);
            if (level != null) {
                storage.saveLevel(level, playerId);
            }
        }
    }

    private final Map<UUID, Double> healthRegen = new ConcurrentHashMap<>();
    public double getHealthRegen(UUID id) { return healthRegen.getOrDefault(id, 0.0); }
    public void setHealthRegen(UUID id, double v) { healthRegen.put(id, Math.max(0.0, v)); }


    // Setzt Health des Spielers basierend auf Stats
    public void applyHealth(Player player) {
        PlayerStats stats = getStats(player);
        player.setMaxHealth(stats.getHealth());
    }

    // Alle Stats zurücksetzen
    public void resetStats(Player player) {
        PlayerStats stats = new PlayerStats(player.getUniqueId());
        playerStats.put(player.getUniqueId(), stats);
        applyHealth(player);
    }

    // Alle Stats clearen (z.B. beim Plugin-Disable) und speichern
    public void clearAll() {
        if (storage != null) {
            storage.saveAll(this);
        }
        playerStats.clear();
    }

    public int getPlayerCount() {
        return playerStats.size();
    }

    // Gibt alle Stats zurück (für das Speichern)
    public Collection<PlayerStats> getAllStats() {
        return playerStats.values();
    }

    // Speichert die Stats eines bestimmten Spielers
    public void saveStats(UUID playerId) {
        PlayerStats stats = playerStats.get(playerId);
        PlayerLevel level = playerLevels.get(playerId);
        if (stats != null && storage != null) {
            storage.saveStats(stats);
            if (level != null) {
                storage.saveLevel(level, playerId);
            }
        }
    }

    // Speichert alle Stats
    public void saveAll() {
        if (storage != null) {
            storage.saveAll(this);
        }
    }

    public void applyItemBonuses(java.util.UUID id, double damage, double critC, double critD, double health, double armor, double range, double attackspeed){
        PlayerStats s = playerStats.get(id);
        if (s == null) return;
        ItemBonus prev = lastItemBonus.getOrDefault(id, new ItemBonus(0,0,0,0,0,0,0));
        double dDamage = damage - prev.d;
        double dCritC = critC - prev.cc;
        double dCritD = critD - prev.cd;
        double dHealth = health - prev.hp;
        double dArmor = armor - prev.ar;
        double dRange = range - prev.rg;
        double dAttackspeed = attackspeed - prev.rg;


        if (dDamage != 0) s.addDamage(dDamage);
        if (dCritC != 0) s.addCritChance(dCritC);
        if (dCritD != 0) s.addCritDamage(dCritD);
        if (dArmor != 0) s.addArmor(dArmor);
        if (dRange != 0) s.addRange(dRange);
        if (dHealth != 0) s.addHealth(dHealth);
        if (dAttackspeed != 0) s.addAttackSpeed(dAttackspeed);


        lastItemBonus.put(id, new ItemBonus(damage, critC, critD, health, armor, range, attackspeed));
    }

}