package de.deinname.statsplugin.items;


import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;


public final class ItemStatUtils {
    private ItemStatUtils(){}


    public static ItemStats read(ItemStack is, ItemStatKeys keys){
        if (is == null || !is.hasItemMeta()) return ItemStats.zero();
        ItemMeta meta = is.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return new ItemStats(
                get(pdc, keys.DAMAGE),
                get(pdc, keys.CRIT_CHANCE),
                get(pdc, keys.CRIT_DAMAGE),
                get(pdc, keys.HEALTH),
                get(pdc, keys.ARMOR),
                get(pdc, keys.RANGE),
                get(pdc, keys.MANA_MAX),
                get(pdc, keys.MANA_REGEN),
                get(pdc, keys.HEALTH_REGEN),
                get(pdc, keys.ATTACKSPEED)
        );
    }


    public static void writeStat(ItemStack is, NamespacedKey key, double value){
        if (is == null) return;
        ItemMeta meta = is.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        is.setItemMeta(meta);
    }


    public static void writeAbility(ItemStack is, ItemStatKeys keys, String abilityId){
        if (is == null) return;
        ItemMeta meta = is.getItemMeta();
        if (abilityId == null || abilityId.isEmpty()) {
            meta.getPersistentDataContainer().remove(keys.ABILITY);
        } else {
            meta.getPersistentDataContainer().set(keys.ABILITY, PersistentDataType.STRING, abilityId);
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