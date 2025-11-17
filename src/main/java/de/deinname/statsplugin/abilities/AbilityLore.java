package de.deinname.statsplugin.abilities;

import de.deinname.statsplugin.items.ItemStatKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        // NAME + RIGHT CLICK
        lore.add(Component.text()
                .append(Component.text("Ability: ", NamedTextColor.GOLD))
                .append(Component.text(def.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(" (RIGHT CLICK)", NamedTextColor.GRAY))
                .build());

        // DESCRIPTION
        lore.add(Component.text(def.getDescription(), NamedTextColor.GRAY));

        // MANA & COOLDOWN
        lore.add(Component.text()
                .append(Component.text("Mana: ", NamedTextColor.AQUA))
                .append(Component.text(String.valueOf(mana), NamedTextColor.AQUA))
                .append(Component.text("   Cooldown: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(
                        String.format(Locale.US, "%.1fs", cooldown),
                        NamedTextColor.GREEN
                ))
                .build());

        meta.lore(lore);
        item.setItemMeta(meta);
    }
}
