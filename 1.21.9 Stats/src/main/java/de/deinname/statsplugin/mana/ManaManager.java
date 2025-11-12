package de.deinname.statsplugin.mana;


import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;


public class ManaManager {
    private final NamespacedKey CUR, MAX, REG;


    public ManaManager(Plugin plugin){
        CUR = new NamespacedKey(plugin, "mana_current");
        MAX = new NamespacedKey(plugin, "mana_max");
        REG = new NamespacedKey(plugin, "mana_regen");
    }


    public void init(Player p, double baseMax, double baseRegen){
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (!pdc.has(MAX, PersistentDataType.DOUBLE)) pdc.set(MAX, PersistentDataType.DOUBLE, baseMax);
        if (!pdc.has(REG, PersistentDataType.DOUBLE)) pdc.set(REG, PersistentDataType.DOUBLE, baseRegen);
        if (!pdc.has(CUR, PersistentDataType.DOUBLE)) pdc.set(CUR, PersistentDataType.DOUBLE, baseMax);
    }


    public double get(Player p){ return get(p, CUR, 0.0); }
    public double getMax(Player p){ return get(p, MAX, 0.0); }
    public double getRegen(Player p){ return get(p, REG, 0.0); }


    public void setMax(Player p, double v){ set(p, MAX, Math.max(0, v)); clamp(p); }
    public void setRegen(Player p, double v){ set(p, REG, Math.max(0, v)); }


    public boolean consume(Player p, double v){
        double c = get(p);
        if (c < v) return false;
        set(p, CUR, c - v); return true;
    }
    public void add(Player p, double v){ set(p, CUR, Math.min(getMax(p), get(p)+v)); }


    public void tick(Player p){ add(p, getRegen(p)); }


    private void clamp(Player p){ set(p, CUR, Math.min(get(p), getMax(p))); }
    private double get(Player p, NamespacedKey k, double def){
        Double v = p.getPersistentDataContainer().get(k, PersistentDataType.DOUBLE);
        return v == null ? def : v;
    }
    private void set(Player p, NamespacedKey k, double v){ p.getPersistentDataContainer().set(k, PersistentDataType.DOUBLE, v); }
}