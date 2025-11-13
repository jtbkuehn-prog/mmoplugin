package de.deinname.statsplugin.items;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemStatUtils {
    private ItemStatUtils(){}

    public static ItemStats read(ItemStack is, ItemStatKeys keys){
        if (is == null || is.getType().isAir() || !is.hasItemMeta()) return ItemStats.zero();
        ItemMeta meta = is.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        double dmg     = get(pdc, keys.DAMAGE);
        double cc      = get(pdc, keys.CRIT_CHANCE);
        double cd      = get(pdc, keys.CRIT_DAMAGE);
        double hp      = get(pdc, keys.HEALTH);
        double armor   = get(pdc, keys.ARMOR);
        double range   = get(pdc, keys.RANGE);
        double manaMax = get(pdc, keys.MANA_MAX);
        double manaReg = get(pdc, keys.MANA_REGEN);
        double hpr     = get(pdc, keys.HEALTH_REGEN);
        double as_     = get(pdc, keys.ATTACKSPEED);

        return new ItemStats(dmg, cc, cd, hp, armor, range, manaMax, manaReg, hpr, as_);
    }

    public static void writeStat(ItemStack is, NamespacedKey key, double value){
        if (is == null || is.getType().isAir()) return;
        ItemMeta meta = is.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        is.setItemMeta(meta);
    }

    public static void writeAbility(ItemStack is, ItemStatKeys keys, String abilityName){
        if (is == null || is.getType().isAir()) return;
        ItemMeta meta = is.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (abilityName == null || abilityName.isEmpty()) {
            pdc.remove(keys.ABILITY);
        } else {
            pdc.set(keys.ABILITY, PersistentDataType.STRING, abilityName);
        }
        is.setItemMeta(meta);
    }

    public static String readAbility(ItemStack is, ItemStatKeys keys){
        if (is == null || !is.hasItemMeta()) return null;
        return is.getItemMeta().getPersistentDataContainer().get(keys.ABILITY, PersistentDataType.STRING);
    }

    private static double get(PersistentDataContainer pdc, NamespacedKey k){
        Double v = pdc.get(k, PersistentDataType.DOUBLE);
        return v == null ? 0.0 : v;
    }
}
