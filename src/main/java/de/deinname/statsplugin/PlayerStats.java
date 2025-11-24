package de.deinname.statsplugin;

import java.util.UUID;


public class PlayerStats {


    // ==== META-DATEN ====
    private final UUID playerId;
    private int level = 1;
    private double xp = 0.0;
    private int skillPoints = 0;

    public PlayerStats(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getLevel() {
        return level;
    }
    public void setLevel(int level) {
        this.level = level;
    }

    public double getXp() {
        return xp;
    }
    public void setXp(double xp) {
        this.xp = xp;
    }

    public int getSkillPoints() {
        return skillPoints;
    }
    public void setSkillPoints(int skillPoints) {
        this.skillPoints = skillPoints;
    }


    // ==== BASIS-WERTE (werden gespeichert) ====
    private double baseDamage      = 5.0;
    private double baseCritChance  = 5.0;
    private double baseCritDamage  = 150.0;
    private double baseAttackSpeed = 1.0;
    private double baseRange       = 4.5;
    private double baseHealth      = 20.0;
    private double baseArmor       = 0.0;
    private double baseMana        = 50.0;
    private double baseManaRegen   = 1.0;
    private double baseHealthRegen = 1.0;
    private double baseSpeed = 100.0;      // % Laufgeschwindigkeit (0 = normal)

    // ==== ITEM-BONI (werden NICHT gespeichert) ====
    private double itemDamage      = 0.0;
    private double itemCritChance  = 0.0;
    private double itemCritDamage  = 0.0;
    private double itemAttackSpeed = 0.0;
    private double itemRange       = 0.0;
    private double itemHealth      = 0.0;
    private double itemArmor       = 0.0;
    private double itemMana        = 0.0;
    private double itemManaRegen   = 0.0;
    private double itemHealthRegen = 0.0;
    private double itemSpeed = 0.0;

    // ==== CURRENT HP (MMO-Health, unabhängig von Bukkit) ====
    private double currentHealth = -1.0; // -1 = noch nicht initialisiert



    // ==== ITEM-BONI zurücksetzen (wird von ItemRecalcListener genutzt) ====
    public void clearItemBonuses() {
        itemDamage = 0.0;
        itemCritChance = 0.0;
        itemCritDamage = 0.0;
        itemAttackSpeed = 0.0;
        itemRange = 0.0;
        itemHealth = 0.0;
        itemArmor = 0.0;
        itemMana = 0.0;
        itemManaRegen = 0.0;
        itemHealthRegen = 0.0;
        itemSpeed = 0.0;
    }

    // ==== Item-Bonus hinzufügen (pro Stat) ====
    public void addItemDamage(double v)      { itemDamage      += v; }
    public void addItemCritChance(double v)  { itemCritChance  += v; }
    public void addItemCritDamage(double v)  { itemCritDamage  += v; }
    public void addItemAttackSpeed(double v) { itemAttackSpeed += v; }
    public void addItemRange(double v)       { itemRange       += v; }
    public void addItemHealth(double v)      { itemHealth      += v; }
    public void addItemArmor(double v)       { itemArmor       += v; }
    public void addItemMana(double v)        { itemMana        += v; }
    public void addItemManaRegen(double v)   { itemManaRegen   += v; }
    public void addItemHealthRegen(double v) { itemHealthRegen += v; }
    public void addItemSpeed(double v)       { itemSpeed += v; }


    // ==== TOTAL-WERTE (Base + Items) – werden überall im Combat verwendet ====

    public double getDamage()      { return baseDamage      + itemDamage; }
    public double getCritChance()  { return baseCritChance  + itemCritChance; }
    public double getCritDamage()  { return baseCritDamage  + itemCritDamage; }
    public double getAttackSpeed() { return baseAttackSpeed + itemAttackSpeed; }
    public double getRange()       { return baseRange       + itemRange; }
    public double getHealth()      { return baseHealth      + itemHealth; }
    public double getArmor()       { return baseArmor       + itemArmor; }
    public double getMana()        { return baseMana        + itemMana; }
    public double getManaRegen()   { return baseManaRegen   + itemManaRegen; }
    public double getHealthRegen() { return baseHealthRegen + itemHealthRegen; }
    public double getSpeed() { return baseSpeed + itemSpeed; }


    // ==== BASE-GETTER/SETTER (für /stats & SPEICHERN) ====

    public double getBaseDamage() { return baseDamage; }
    public void setBaseDamage(double v) { this.baseDamage = v; }

    public double getBaseCritChance() { return baseCritChance; }
    public void setBaseCritChance(double v) { this.baseCritChance = v; }

    public double getBaseCritDamage() { return baseCritDamage; }
    public void setBaseCritDamage(double v) { this.baseCritDamage = v; }

    public double getBaseAttackSpeed() { return baseAttackSpeed; }
    public void setBaseAttackSpeed(double v) { this.baseAttackSpeed = v; }

    public double getBaseRange() { return baseRange; }
    public void setBaseRange(double v) { this.baseRange = v; }

    public double getBaseHealth() { return baseHealth; }
    public void setBaseHealth(double v) { this.baseHealth = v; }

    public double getBaseArmor() { return baseArmor; }
    public void setBaseArmor(double v) { this.baseArmor = v; }

    public double getBaseMana() { return baseMana; }
    public void setBaseMana(double v) { this.baseMana = v; }

    public double getBaseManaRegen() { return baseManaRegen; }
    public void setBaseManaRegen(double v) { this.baseManaRegen = v; }

    public double getBaseHealthRegen() { return baseHealthRegen; }
    public void setBaseHealthRegen(double v) { this.baseHealthRegen = v; }

    public double getBaseSpeed() { return baseSpeed; }
    public void setBaseSpeed(double v) { this.baseSpeed = v; }



    public void setItemDamage(double v) { this.itemDamage = v; }

    public void setItemCritChance(double v) { this.itemCritChance = v; }

    public void setItemCritDamage(double v) { this.itemCritDamage = v; }

    public void setItemAttackSpeed(double v) { this.itemAttackSpeed = v; }

    public void setItemRange(double v) { this.itemRange = v; }

    public void setItemHealth(double v) { this.itemHealth = v; }

    public void setItemArmor(double v) { this.itemArmor = v; }

    public void setItemMana(double v) { this.itemMana = v; }

    public void setItemManaRegen(double v) { this.itemManaRegen = v; }

    public void setItemHealthRegen(double v) { this.itemHealthRegen = v; }

    public void setItemSpeed(double v) { this.itemSpeed = v; }



    public double getCurrentHealth() {
        // Fallback, falls noch nie gesetzt:
        if (currentHealth <= 0) {
            currentHealth = getHealth(); // Max-HP als Startwert
        }
        return currentHealth;
    }

    public void setCurrentHealth(double value) {
        double max = getHealth();
        if (max <= 0) max = 1.0;
        this.currentHealth = Math.max(0.0, Math.min(value, max));
    }

    public void heal(double amount) {
        setCurrentHealth(getCurrentHealth() + amount);
    }

    public void damage(double amount) {
        setCurrentHealth(getCurrentHealth() - amount);
    }

    public double getTotalMana() {
        return baseMana + itemMana; // siehe applyItemBonuses
    }

    public double getTotalManaRegen() {
        return baseManaRegen + itemManaRegen;
    }


    // ==== KOMPATIBLE SETTER (falls irgendwo setDamage(...) etc. genutzt werden) ====

    public void setDamage(double v)      { this.baseDamage      = v; }
    public void setCritChance(double v)  { this.baseCritChance  = v; }
    public void setCritDamage(double v)  { this.baseCritDamage  = v; }
    public void setAttackSpeed(double v) { this.baseAttackSpeed = v; }
    public void setRange(double v)       { this.baseRange       = v; }
    public void setHealth(double v)      { this.baseHealth      = v; }
    public void setArmor(double v)       { this.baseArmor       = v; }
    public void setMana(double v)        { this.baseMana        = v; }
    public void setManaRegen(double v)   { this.baseManaRegen   = v; }
    public void setHealthRegen(double v) { this.baseHealthRegen = v; }
    public void setSpeed(double v) { this.baseSpeed = v; }
}
