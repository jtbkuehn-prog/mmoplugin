package de.deinname.statsplugin.items;


import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;


public final class ItemStatKeys {
    public final NamespacedKey DAMAGE;
    public final NamespacedKey CRIT_CHANCE;
    public final NamespacedKey CRIT_DAMAGE;
    public final NamespacedKey HEALTH;
    public final NamespacedKey ARMOR;
    public final NamespacedKey RANGE;
    public final NamespacedKey MANA_MAX;
    public final NamespacedKey MANA_REGEN;
    public final NamespacedKey ABILITY; // z.B. "damage_boost"


    public ItemStatKeys(Plugin plugin){
        DAMAGE = new NamespacedKey(plugin, "damage");
        CRIT_CHANCE = new NamespacedKey(plugin, "crit_chance");
        CRIT_DAMAGE = new NamespacedKey(plugin, "crit_damage");
        HEALTH = new NamespacedKey(plugin, "health");
        ARMOR = new NamespacedKey(plugin, "armor");
        RANGE = new NamespacedKey(plugin, "range");
        MANA_MAX = new NamespacedKey(plugin, "mana_max");
        MANA_REGEN = new NamespacedKey(plugin, "mana_regen");
        ABILITY = new NamespacedKey(plugin, "ability");
    }
}