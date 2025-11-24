package de.deinname.statsplugin.mobs;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;

public enum CustomMobType {

    ZOMBIE_WARRIOR(
            "zombie_warrior",
            ChatColor.DARK_GREEN + "Zombie Warrior",
            EntityType.ZOMBIE,
            40.0,   // HP bei Level 1
            6.0,    // Damage bei Level 1
            200.0,    // Armor
            0.24,   // MovementSpeed
            10      // XP bei Level 1
    ),

    SKELETON_ARCHER(
            "skeleton_archer",
            ChatColor.GRAY + "Skeleton Archer",
            EntityType.SKELETON,
            30.0,
            5.0,
            0.0,
            0.27,
            12
    ),

    SKELETON_PRINCE(
        "skeleton_prince",
        ChatColor.GOLD + "" + ChatColor.BOLD + "Richard, the Skeleton Prince",
        EntityType.SKELETON,
        25000.0, // sehr viel HP
                50.0,    // Grundschaden
                0.0,    // Armor
                0.30,    // etwas schneller
                500      // viel XP
    );


    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final double baseHealth;
    private final double baseDamage;
    private final double baseArmor;
    private final double baseSpeed;
    private final int baseXp;

    CustomMobType(String id,
                  String displayName,
                  EntityType entityType,
                  double baseHealth,
                  double baseDamage,
                  double baseArmor,
                  double baseSpeed,
                  int baseXp) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.baseArmor = baseArmor;
        this.baseSpeed = baseSpeed;
        this.baseXp = baseXp;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public EntityType getEntityType() { return entityType; }
    public double getBaseHealth() { return baseHealth; }
    public double getBaseDamage() { return baseDamage; }
    public double getBaseArmor() { return baseArmor; }
    public double getBaseSpeed() { return baseSpeed; }
    public int getBaseXp() { return baseXp; }

    /** Simple Scaling: Level 1 = base, Level N = base * (1 + 0.15*(N-1)) */
    public double scale(double base, int level) {
        if (level <= 1) return base;
        double mult = 1.0 + 0.15 * (level - 1);
        return base * mult;
    }

    public double healthAt(int level) { return scale(baseHealth, level); }
    public double damageAt(int level) { return scale(baseDamage, level); }
    public double armorAt(int level)  { return scale(baseArmor, level); }
    public double speedAt(int level)  { return scale(baseSpeed, 1); }
    public int xpAt(int level)        { return (int) Math.round(scale(baseXp, level)); }

    public static CustomMobType byId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase();
        for (CustomMobType t : values()) {
            if (t.id.equalsIgnoreCase(lower)) return t;
        }
        return null;
    }
}
