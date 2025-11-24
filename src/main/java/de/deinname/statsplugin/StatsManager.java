package de.deinname.statsplugin;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;


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
    private static final class ItemBonus { double d, cc, cd, hp, ar, rg, as, sp; ItemBonus(double d,double cc,double cd,double hp,double ar,double rg, double as,double sp){this.d=d;this.cc=cc;this.cd=cd;this.hp=hp;this.ar=ar;this.rg=rg;this.as=as;this.sp=sp;} }
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
    public void applyNoVanillaCooldown(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;

        // Standard ist 4.0 – je höher, desto kürzer der Vanilla-Cooldown.
        // 1024.0 => praktisch instant voll.
        attr.setBaseValue(1024.0);
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

    private final Map<UUID, Double> itemHealthRegen = new ConcurrentHashMap<>();

    public void setItemHealthRegen(UUID id, double v) {
        itemHealthRegen.put(id, Math.max(0.0, v));
    }

    public double getItemHealthRegen(UUID id) {
        return itemHealthRegen.getOrDefault(id, 0.0);
    }

    // Gesamt-HealthRegen = Base (in PlayerStats) + Item
    public double getTotalHealthRegen(Player p) {
        double base = getStats(p).getHealthRegen();        // aus PlayerStats (via /stats set)
        double item = getItemHealthRegen(p.getUniqueId()); // aus Items
        return Math.max(0.0, base) + Math.max(0.0, item);
    }

    public void applyHealth(Player p) {
        PlayerStats s = getStats(p);
        if (s == null) return;

        double max = s.getHealth();
        if (max <= 0) max = 1.0;

        // currentHealth clampen
        if (s.getCurrentHealth() <= 0 || s.getCurrentHealth() > max) {
            s.setCurrentHealth(max);
        }

        // Bukkit-Health minimal auf "lebt" halten (z.B. 20 Herzen oder 1 HP)
        var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0); // Vanilla sagt 20 HP, aber wir ignorieren das als echten Wert
        }
        if (p.getHealth() <= 0.0) {
            p.setHealth(20.0);
        } else if (p.getHealth() > 20.0) {
            p.setHealth(20.0);
        }
    }


    public void applySpeed(Player p) {
        PlayerStats s = getStats(p);
        if (s == null) return;

        AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;

        double base = 0.1; // Vanilla-Default
        double bonusPercent = s.getSpeed();
        double value = base * (bonusPercent / 100.0);

        attr.setBaseValue(value);
    }

    // Alle Stats zurücksetzen
    public void resetStats(Player player) {
        PlayerStats stats = new PlayerStats(player.getUniqueId());
        playerStats.put(player.getUniqueId(), stats);
        applyHealth(player);
        applySpeed(player);
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

    public void applyItemBonuses(java.util.UUID id,
                                 double damage,
                                 double critC,
                                 double critD,
                                 double health,
                                 double armor,
                                 double range,
                                 double attackspeed,
                                 double speed) {

        PlayerStats s = playerStats.get(id);
        if (s == null) return;

        // Hier KEIN + und KEIN setBase mehr:
        s.setItemDamage(damage);
        s.setItemCritChance(critC);
        s.setItemCritDamage(critD);
        s.setItemHealth(health);
        s.setItemArmor(armor);
        s.setItemRange(range);
        s.setItemAttackSpeed(attackspeed);
        s.setItemSpeed(speed);
    }


}