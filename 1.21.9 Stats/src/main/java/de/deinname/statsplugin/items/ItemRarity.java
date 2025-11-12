package de.deinname.statsplugin.items;

import net.kyori.adventure.text.format.TextColor;

/** Raritäten mit HEX-Farben. Passe die HEX-Werte frei an. */
public enum ItemRarity {
    COMMON   ("#C0C0C0"),
    UNCOMMON ("#55FF55"),
    RARE     ("#5555FF"),
    EPIC     ("#AA00FF"),
    LEGENDARY("#FFAA00"),
    MYTHIC   ("#FF3366");

    private final String hex;
    ItemRarity(String hex) { this.hex = hex; }

    public TextColor color() {
        try { return TextColor.fromHexString(hex); }
        catch (Exception e) { return TextColor.fromHexString("#FFFFFF"); }
    }

    public static ItemRarity from(String s) {
        if (s == null) return COMMON;
        try { return ItemRarity.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return COMMON; }
    }
}
