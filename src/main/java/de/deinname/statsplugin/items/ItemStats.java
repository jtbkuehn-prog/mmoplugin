package de.deinname.statsplugin.items;


public record ItemStats(
        double damage,
        double critChance,
        double critDamage,
        double health,
        double armor,
        double range,
        double manaMax,
        double manaRegen
) {
    public static ItemStats zero(){ return new ItemStats(0,0,0,0,0,0,0,0); }
    public ItemStats add(ItemStats o){
        return new ItemStats(
                damage + o.damage,
                critChance + o.critChance,
                critDamage + o.critDamage,
                health + o.health,
                armor + o.armor,
                range + o.range,
                manaMax + o.manaMax,
                manaRegen + o.manaRegen
        );
    }
}