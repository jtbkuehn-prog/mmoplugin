package de.deinname.statsplugin.items;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemRarityManager {

    private final Map<String, TextColor> colors = new HashMap<>();
    private final TextColor fallback = TextColor.fromHexString("#FFFFFF");

    public ItemRarityManager(JavaPlugin plugin) {
        // Defaults, falls keine Config vorhanden ist
        colors.put("COMMON",    TextColor.fromHexString("#C0C0C0"));
        colors.put("UNCOMMON",  TextColor.fromHexString("#55FF55"));
        colors.put("RARE",      TextColor.fromHexString("#5555FF"));
        colors.put("EPIC",      TextColor.fromHexString("#AA00FF"));
        colors.put("LEGENDARY", TextColor.fromHexString("#FFAA00"));
        colors.put("MYTHIC",    TextColor.fromHexString("#FF3366"));

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("rarities");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                String hex = sec.getString(k);
                try {
                    colors.put(k.toUpperCase(), TextColor.fromHexString(hex));
                } catch (Exception ignored) {}
            }
        }
    }

    public TextColor get(String nameOrHex) {
        if (nameOrHex == null) return fallback;
        if (nameOrHex.startsWith("#")) {
            try { return TextColor.fromHexString(nameOrHex); }
            catch (Exception e) { return fallback; }
        }
        return colors.getOrDefault(nameOrHex.toUpperCase(), fallback);
    }

    public Set<String> names() {
        return colors.keySet();
    }
}
