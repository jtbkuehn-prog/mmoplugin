package de.deinname.statsplugin;

import java.util.UUID;

public class PlayerStats {
    private final UUID playerId;

    // Base Stats
    private double damage;
    private double critChance;
    private double critDamage;
    private double range;
    private double health;
    private double armor;
    private double attackSpeed;
    private double mana;
    private double manaregen;
    private double healthregen;

    public PlayerStats(UUID playerId) {
        this.playerId = playerId;
        // Default Werte
        this.damage = 5.0;
        this.critChance = 5.0; // 5%
        this.critDamage = 150.0; // 150% = 1.5x Schaden
        this.range = 3.0;
        this.health = 20.0;
        this.armor = 0.0;
        this.attackSpeed = 2.0;
        this.mana = 50.0;
        this.manaregen = 2.0;
        this.healthregen = 2.0;
    }

    // Getter
    public UUID getPlayerId() { return playerId; }
    public double getDamage() { return damage; }
    public double getCritChance() { return critChance; }
    public double getCritDamage() { return critDamage; }
    public double getRange() { return range; }
    public double getHealth() { return health; }
    public double getArmor() { return armor; }
    public double getAttackSpeed() { return attackSpeed; }
    public double getMana() { return mana; }
    public double getManaregen() { return manaregen; }
    public double getHealthregen() { return healthregen; }

    // Setter
    public void setDamage(double damage) { this.damage = damage; }
    public void setCritChance(double critChance) { this.critChance = Math.min(100, critChance); }
    public void setCritDamage(double critDamage) { this.critDamage = critDamage; }
    public void setRange(double range) { this.range = range; }
    public void setHealth(double health) { this.health = Math.max(1, health); }
    public void setArmor(double armor) { this.armor = armor; }
    public void setAttackSpeed(double attackSpeed) { this.attackSpeed = Math.max(0.1, attackSpeed); }
    public void setMana(double mana) { this.mana = Math.max(0.1, mana); }
    public void setManaregen(double manaregen) { this.manaregen = Math.max(0.1, manaregen); }
    public void setHealthregen(double healthregen) { this.healthregen = Math.max(0.1, healthregen); }

    // Add/Remove Methoden für Items/Buffs
    public void addDamage(double amount) { this.damage += amount; }
    public void addCritChance(double amount) {
        this.critChance = Math.min(100, this.critChance + amount);
    }
    public void addCritDamage(double amount) { this.critDamage += amount; }
    public void addRange(double amount) { this.range += amount; }
    public void addHealth(double amount) { this.health += amount; }
    public void addArmor(double amount) { this.armor += amount; }
    public void addAttackSpeed(double amount) { setAttackSpeed(this.attackSpeed + amount); }
    public void addMana(double amount) { setMana(this.mana + amount); }
    public void addManaregen(double amount) { setManaregen(this.manaregen + amount); }
    public void addHealthregen(double amount) { setHealthregen(this.healthregen + amount); }

    // Berechnet ob ein Crit erfolgt
    public boolean rollCrit() {
        return Math.random() * 100 < critChance;
    }

    // Berechnet finalen Schaden mit Crit
    public double calculateDamage(boolean isCrit) {
        if (isCrit) {
            return damage * (critDamage / 100.0);
        }
        return damage;
    }

    // Berechnet Schaden nach Armor-Reduktion
    public double calculateDamageReduction(double incomingDamage) {
        // Formel: Schaden * (100 / (100 + Armor))
        double reduction = 100.0 / (100.0 + armor);
        return incomingDamage * reduction;
    }

    @Override
    public String toString() {
        return String.format(
                "§6=== Stats ===\n" +
                        "§cDamage: §f%.1f\n" +
                        "§eCrit Chance: §f%.1f%%\n" +
                        "§eCrit Damage: §f%.0f%%\n" +
                        "§dAttack Speed: §f%.1f/s\n" +
                        "§bRange: §f%.1f\n" +
                        "§aHealth: §f%.1f\n" +
                        "§7Armor: §f%.1f",
                        "§7Mana: §f%.1f",
                        "§7Manaregen: §f%.1f",
                        "§7Healthregen: §f%.1f",
                damage, critChance, critDamage, attackSpeed, range, health, armor, mana, manaregen, healthregen
        );
    }

}