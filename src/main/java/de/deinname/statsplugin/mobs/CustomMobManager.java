package de.deinname.statsplugin.mobs;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;

public class CustomMobManager {

    private final JavaPlugin plugin;
    private final NamespacedKey KEY_MOB_ID;
    private final NamespacedKey KEY_MOB_LEVEL;
    private final NamespacedKey KEY_MOB_XP;
    private final NamespacedKey KEY_MOB_MAX_HP;
    private final NamespacedKey KEY_MOB_CUR_HP;
    private final NamespacedKey KEY_MOB_ARMOR;


    public CustomMobManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.KEY_MOB_ID    = new NamespacedKey(plugin, "mmomob_id");
        this.KEY_MOB_LEVEL = new NamespacedKey(plugin, "mmomob_level");
        this.KEY_MOB_XP    = new NamespacedKey(plugin, "mmomob_xp");
        this.KEY_MOB_MAX_HP = new NamespacedKey(plugin, "mmomob_max_hp");
        this.KEY_MOB_CUR_HP = new NamespacedKey(plugin, "mmomob_cur_hp");
        this.KEY_MOB_ARMOR  = new NamespacedKey(plugin, "mmomob_armor");
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

            setAttribute(le, Attribute.MAX_HEALTH, 20);
            le.setHealth(20);
            setAttribute(le, Attribute.ATTACK_DAMAGE, dmg);
            //setAttribute(le, Attribute.ARMOR, 0.0);
            setAttribute(le, Attribute.MOVEMENT_SPEED, spd);

            PersistentDataContainer pdc = le.getPersistentDataContainer();
            pdc.set(KEY_MOB_ID,    PersistentDataType.STRING,  type.getId());
            pdc.set(KEY_MOB_LEVEL, PersistentDataType.INTEGER, level);
            pdc.set(KEY_MOB_XP,    PersistentDataType.INTEGER, xp);
            pdc.set(KEY_MOB_MAX_HP,   PersistentDataType.DOUBLE,  hp);
            pdc.set(KEY_MOB_CUR_HP,   PersistentDataType.DOUBLE,  hp);
            pdc.set(KEY_MOB_ARMOR,    PersistentDataType.DOUBLE,  arm);

            updateHpName(le);
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

    public double getMaxHp(LivingEntity le) {
        Double v = le.getPersistentDataContainer().get(KEY_MOB_MAX_HP, PersistentDataType.DOUBLE);
        return v == null ? le.getAttribute(Attribute.MAX_HEALTH).getValue() : v;
    }

    public double getCurrentHp(LivingEntity le) {
        Double v = le.getPersistentDataContainer().get(KEY_MOB_CUR_HP, PersistentDataType.DOUBLE);
        if (v == null) return getMaxHp(le);
        return v;
    }

    public void setCurrentHp(LivingEntity le, double value) {
        double max = getMaxHp(le);
        double clamped = Math.max(0.0, Math.min(max, value));
        le.getPersistentDataContainer().set(KEY_MOB_CUR_HP, PersistentDataType.DOUBLE, clamped);
    }

    public double getArmor(LivingEntity le) {
        Double v = le.getPersistentDataContainer().get(KEY_MOB_ARMOR, PersistentDataType.DOUBLE);
        return v == null ? 0.0 : v;
    }


    public void updateHpName(LivingEntity mob) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();

        String id   = pdc.get(KEY_MOB_ID, PersistentDataType.STRING);
        Integer lvl = pdc.get(KEY_MOB_LEVEL, PersistentDataType.INTEGER);

        if (id == null || lvl == null) return;

        CustomMobType type = getType(mob);
        if (type == null) return;

        double maxHp = getMaxHp(mob);
        double hp    = getCurrentHp(mob);

        int hpNow = (int) Math.ceil(hp);
        int hpMax = (int) Math.ceil(maxHp);

        String name =
                "§7[§eLv. " + lvl + "§7] §r" +
                        type.getDisplayName() +
                        " §c" + hpNow + "§7/§c" + hpMax;

        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
    }


}
