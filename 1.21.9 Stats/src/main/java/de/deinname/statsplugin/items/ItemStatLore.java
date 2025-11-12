package de.deinname.statsplugin.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Baut die sichtbare Lore aus den in NBT gespeicherten Stats.
 * Versteckt gleichzeitig Vanilla-Attribute (HIDE_ATTRIBUTES) und
 * entfernt vorhandene Attribute-Modifier, damit nur dein System zählt.
 */
public final class ItemStatLore {

    private ItemStatLore() {}

    public static void updateLore(ItemStack is, ItemStatKeys keys) {
        if (is == null || !is.hasItemMeta()) return;

        ItemMeta meta = is.getItemMeta();

        // Vanilla-Attribute in der Anzeige verstecken
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // (Optional, aber empfohlen) vorhandene Vanilla-Attribute-Modifier entfernen,
        // damit keine doppelten/unerwarteten Effekte auftreten.
        meta.setAttributeModifiers(null);

        // Stats aus NBT lesen
        ItemStats s = ItemStatUtils.read(is, keys);

        // Lore aufbauen
        List<Component> lore = new ArrayList<>();
        addLine(lore, s.damage(), "+%s Damage");
        addLine(lore, s.critChance(), "+%s%% Crit Chance");
        addLine(lore, s.critDamage(), "+%s%% Crit Damage");
        addLine(lore, s.health(), "+%s Health");
        addLine(lore, s.armor(), "+%s Armor");
        addLine(lore, s.range(), "+%s Range");
        addLine(lore, s.manaMax(), "+%s Mana");
        addLine(lore, s.manaRegen(), "+%s Mana Regeneration/s");

        meta.lore(lore);
        is.setItemMeta(meta);
    }

    private static void addLine(List<Component> lore, double v, String fmt) {
        if (Math.abs(v) < 1e-9) return;
        String shown = formatNumber(v, fmt);
        lore.add(Component.text(shown, NamedTextColor.GRAY));
    }

    private static String formatNumber(double v, String fmt) {
        boolean isInt = Math.abs(v - Math.rint(v)) < 1e-9;
        String num = isInt ? String.valueOf((int) Math.rint(v))
                : String.valueOf(((double) Math.round(v * 10)) / 10.0);
        return String.format(fmt, num);
    }
}
