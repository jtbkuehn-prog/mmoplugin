package de.deinname.statsplugin.mobs;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomMobManager {

    private final JavaPlugin plugin;
    private final NamespacedKey KEY_MOB_ID;
    private final NamespacedKey KEY_MOB_LEVEL;
    private final NamespacedKey KEY_MOB_XP;

    public CustomMobManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.KEY_MOB_ID    = new NamespacedKey(plugin, "mmomob_id");
        this.KEY_MOB_LEVEL = new NamespacedKey(plugin, "mmomob_level");
        this.KEY_MOB_XP    = new NamespacedKey(plugin, "mmomob_xp");
    }

    public NamespacedKey keyId()    { return KEY_MOB_ID; }
    public NamespacedKey keyLevel() { return KEY_MOB_LEVEL; }
    public NamespacedKey keyXp()    { return KEY_MOB_XP; }

    /**
     * Spawnt einen CustomMob als den im Typ definierten Vanilla-EntityType.
     */
    public LivingEntity spawnMob(CustomMobType type, int level, Location loc) {
        if (type == null || level <= 0 || loc == null) return null;
        var world = loc.getWorld();
        if (world == null) return null;

        var clazz = type.getEntityType().getEntityClass();
        if (clazz == null) return null;

        var entity = world.spawn(loc, clazz, e -> {
            if (!(e instanceof LivingEntity le)) return;

            double hp   = type.healthAt(level);
            double dmg  = type.damageAt(level);
            double arm  = type.armorAt(level);
            double spd  = type.speedAt(level);
            int xp      = type.xpAt(level);

            setAttribute(le, Attribute.MAX_HEALTH, hp);
            le.setHealth(hp);
            setAttribute(le, Attribute.ATTACK_DAMAGE, dmg);
            setAttribute(le, Attribute.ARMOR, arm);
            setAttribute(le, Attribute.MOVEMENT_SPEED, spd);

            PersistentDataContainer pdc = le.getPersistentDataContainer();
            pdc.set(KEY_MOB_ID,    PersistentDataType.STRING,  type.getId());
            pdc.set(KEY_MOB_LEVEL, PersistentDataType.INTEGER, level);
            pdc.set(KEY_MOB_XP,    PersistentDataType.INTEGER, xp);

            String name = "§7[§eLv. " + level + "§7] §r" + type.getDisplayName();
            le.setCustomName(name);
            le.setCustomNameVisible(true);
        });

        if (entity instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    private void setAttribute(LivingEntity le, Attribute attr, double value) {
        AttributeInstance inst = le.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    public CustomMobType getType(LivingEntity le) {
        if (le == null) return null;
        String id = le.getPersistentDataContainer().get(KEY_MOB_ID, PersistentDataType.STRING);
        return CustomMobType.byId(id);
    }

    public int getLevel(LivingEntity le) {
        if (le == null) return 0;
        Integer lvl = le.getPersistentDataContainer().get(KEY_MOB_LEVEL, PersistentDataType.INTEGER);
        return lvl == null ? 0 : lvl;
    }

    public int getXp(LivingEntity le) {
        if (le == null) return 0;
        Integer xp = le.getPersistentDataContainer().get(KEY_MOB_XP, PersistentDataType.INTEGER);
        return xp == null ? 0 : xp;
    }
}
