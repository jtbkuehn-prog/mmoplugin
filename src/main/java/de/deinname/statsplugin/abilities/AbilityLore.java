package de.deinname.statsplugin.abilities;

import de.deinname.statsplugin.items.ItemStatKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AbilityLore {

    private AbilityLore() {}

    public static void appendAbilityLore(ItemStack item, ItemStatKeys keys) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String id = pdc.get(keys.ABILITY, PersistentDataType.STRING);
        if (id == null || id.isEmpty()) {
            return;
        }

        AbilityDefinition def = AbilityDefinition.byId(id);
        if (def == null) {
            return;
        }

        // values
        int mana = pdc.getOrDefault(keys.ABILITY_MANA, PersistentDataType.INTEGER, 0);
        double cooldown = pdc.getOrDefault(keys.ABILITY_COOLDOWN, PersistentDataType.DOUBLE, 0.0);

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        // gap
        lore.add(Component.text(""));

        // Click-Hinweis abhängig vom Typ
        String clickHint = id.equalsIgnoreCase("explosive_arrow")
                ? " (LEFT CLICK)"
                : " (RIGHT CLICK)";

// NAME + Click-Hinweis
        lore.add(Component.text()
                .append(Component.text("Ability: ", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(def.getDisplayName(), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(clickHint, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .build());


        // DESCRIPTION
        lore.add(Component.text(def.getDescription(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        // MANA & COOLDOWN
        lore.add(Component.text()
                .append(Component.text("Mana: ", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(String.valueOf(mana), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
                .append(Component.text("   Cooldown: ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(
                        String.format(Locale.US, "%.1fs", cooldown),
                        NamedTextColor.GREEN
                ).decoration(TextDecoration.ITALIC, false))
                .build());

        meta.lore(lore);
        item.setItemMeta(meta);
    }
}
