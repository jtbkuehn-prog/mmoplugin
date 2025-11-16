package de.deinname.statsplugin;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class PlayerLevel {
    private final Player player;
    private final PlayerStats stats;

    private int level;
    private double xp;
    private int skillPoints;

    // Level-Konstanten
    private static int MAX_LEVEL = 100;
    private static double XP_BASE = 100.0;
    private static double XP_MULTIPLIER = 1.15; // 15% mehr XP pro Level

    public static void configure(int maxLevel, double base, double multiplier) {
        MAX_LEVEL = maxLevel;
        XP_BASE = base;
        XP_MULTIPLIER = multiplier;
    }

    public PlayerLevel(Player player, PlayerStats stats) {
        this.player = player;
        this.stats = stats;
        this.level = 1;
        this.xp = 0;
        this.skillPoints = 0;
    }

    // Getter
    public int getLevel() { return level; }
    public double getXP() { return xp; }
    public int getSkillPoints() { return skillPoints; }

    // Setter
    public void setLevel(int level) {
        this.level = Math.min(level, MAX_LEVEL);
        updateXPBar();
    }

    public void setXP(double xp) {
        this.xp = Math.max(0, xp);
        updateXPBar();
    }

    public void setSkillPoints(int points) {
        this.skillPoints = Math.max(0, points);
    }

    // Berechnet benötigte XP für nächstes Level
    public double getRequiredXP() {
        return XP_BASE * Math.pow(XP_MULTIPLIER, level - 1);
    }

    // Berechnet benötigte XP für ein bestimmtes Level
    public static double getRequiredXPForLevel(int level) {
        return XP_BASE * Math.pow(XP_MULTIPLIER, level - 1);
    }

    // Fügt XP hinzu
    public void addXP(double amount) {
        if (level >= MAX_LEVEL) {
            player.sendMessage("§6Du hast bereits das Maximum Level erreicht!");
            return;
        }

        xp += amount;
        player.sendMessage("§a+§e" + String.format("%.0f", amount) + " XP");

        // Level-Up Check
        while (xp >= getRequiredXP() && level < MAX_LEVEL) {
            levelUp();
        }

        updateXPBar();
    }

    // Level-Up!
    private void levelUp() {
        xp -= getRequiredXP();
        level++;
        skillPoints += 2; // 2 Skill Points pro Level

        // Stats erhöhen (Auto-Scaling)
        stats.setBaseHealth(stats.getBaseHealth() + 5.0);    // +5 HP pro Level
        stats.setBaseDamage(stats.getBaseDamage() + 0.5);    // +0.5 Damage pro Level
        stats.setBaseArmor(stats.getBaseArmor() + 1.0);      // +1 Armor pro Level


        // Effekte
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.getWorld().spawnParticle(
                org.bukkit.Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                50, 0.5, 1, 0.5, 0.1
        );

        // Health auffrischen
        player.setMaxHealth(stats.getHealth());
        player.setHealth(stats.getHealth());

        // Nachricht
        player.sendMessage("§6§l============================");
        player.sendMessage("§e§l         LEVEL UP!");
        player.sendMessage("§a§l    Level " + (level - 1) + " → Level " + level);
        player.sendMessage("");
        player.sendMessage("§7Neue Stats:");
        player.sendMessage("§c  ❤ Health: §f" + String.format("%.0f", stats.getHealth()));
        player.sendMessage("§c  ⚔ Damage: §f" + String.format("%.1f", stats.getDamage()));
        player.sendMessage("§7  🛡 Armor: §f" + String.format("%.0f", stats.getArmor()));
        player.sendMessage("");
        player.sendMessage("§6+2 Skill Points! §7(Insgesamt: §e" + skillPoints + "§7)");
        player.sendMessage("§7Nutze §e/stats allocate §7um Stats zu verbessern!");
        player.sendMessage("§6§l============================");

        // Wenn Max Level erreicht
        if (level >= MAX_LEVEL) {
            player.sendMessage("");
            player.sendMessage("§6§l🎉 GRATULATION! 🎉");
            player.sendMessage("§eDu hast das Maximum Level erreicht!");
            player.sendMessage("");
        }

        updateXPBar();
    }

    // Aktualisiert die XP-Bar (Vanilla XP Bar)
    public void updateXPBar() {
        if (level >= MAX_LEVEL) {
            player.setLevel(level);
            player.setExp(1.0f);
            return;
        }

        player.setLevel(level);
        float progress = (float) (xp / getRequiredXP());
        player.setExp(Math.min(1.0f, Math.max(0.0f, progress)));
    }

    // Nutzt einen Skill Point um einen Stat zu erhöhen
    public boolean allocateSkillPoint(String statType, int amount) {
        if (skillPoints < amount) {
            player.sendMessage("§cNicht genug Skill Points! Du hast: " + skillPoints);
            return false;
        }

        double multiplier = amount;

        switch (statType.toLowerCase()) {
            case "health", "hp" -> {
                double inc = 10.0 * multiplier;
                stats.setBaseHealth(stats.getBaseHealth() + inc);
                skillPoints -= amount;
                player.sendMessage("§a+" + inc + " Health! §7(Verbleibende Points: §e" + skillPoints + "§7)");

                // MaxHealth auf neuen TOTAL-Wert setzen (Base + Items)
                player.setMaxHealth(stats.getHealth());
                return true;
            }
            case "damage", "dmg" -> {
                double inc = 1.0 * multiplier;
                stats.setBaseDamage(stats.getBaseDamage() + inc);
                skillPoints -= amount;
                player.sendMessage("§a+" + inc + " Damage! §7(Verbleibende Points: §e" + skillPoints + "§7)");
                return true;
            }
            case "armor", "def" -> {
                double inc = 2.0 * multiplier;
                stats.setBaseArmor(stats.getBaseArmor() + inc);
                skillPoints -= amount;
                player.sendMessage("§a+" + inc + " Armor! §7(Verbleibende Points: §e" + skillPoints + "§7)");
                return true;
            }
            case "critchance", "crit" -> {
                double inc = 1.0 * multiplier;
                stats.setBaseCritChance(stats.getBaseCritChance() + inc);
                skillPoints -= amount;
                player.sendMessage("§a+" + inc + "% Crit Chance! §7(Verbleibende Points: §e" + skillPoints + "§7)");
                return true;
            }
            case "critdamage", "critdmg" -> {
                double inc = 5.0 * multiplier;
                stats.setBaseCritDamage(stats.getBaseCritDamage() + inc);
                skillPoints -= amount;
                player.sendMessage("§a+" + inc + "% Crit Damage! §7(Verbleibende Points: §e" + skillPoints + "§7)");
                return true;
            }
            case "range" -> {
                double inc = 0.5 * multiplier;
                stats.setBaseRange(stats.getBaseRange() + inc);
                skillPoints -= amount;
                player.sendMessage("§a+" + inc + " Range! §7(Verbleibende Points: §e" + skillPoints + "§7)");
                return true;
            }
            default -> {
                player.sendMessage("§cUnbekannter Stat! Verfügbar: health, damage, armor, critchance, critdamage, range");
                return false;
            }
        }

    }

    // Reset Skill Points (gibt alle ausgegebenen Points zurück)
    public void resetSkillPoints() {
        // Berechne wie viele Points der Spieler insgesamt haben sollte
        int totalPoints = (level - 1) * 2; // 2 pro Level (außer Level 1)

        // Stats auf Base-Level zurücksetzen (nur das was durch Level kam)
        double baseHealth = 20.0 + (level - 1) * 5.0;
        double baseDamage = 5.0 + (level - 1) * 0.5;
        double baseArmor = 0.0 + (level - 1) * 1.0;

        stats.setHealth(baseHealth);
        stats.setDamage(baseDamage);
        stats.setArmor(baseArmor);
        stats.setCritChance(5.0);
        stats.setCritDamage(150.0);
        stats.setRange(3.0);

        skillPoints = totalPoints;

        player.setMaxHealth(stats.getHealth());
        player.setHealth(stats.getHealth());

        player.sendMessage("§aSkill Points wurden zurückgesetzt! Du hast jetzt §e" + skillPoints + " §aPoints zum Verteilen.");
    }

    @Override
    public String toString() {
        return String.format(
                "§6=== Level & XP ===\n" +
                        "§eLevel: §f%d §7/ §f%d\n" +
                        "§eXP: §f%.0f §7/ §f%.0f §7(%.1f%%)\n" +
                        "§eSkill Points: §f%d",
                level, MAX_LEVEL, xp, getRequiredXP(), (xp / getRequiredXP() * 100), skillPoints
        );
    }
}