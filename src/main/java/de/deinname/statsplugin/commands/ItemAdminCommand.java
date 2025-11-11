package de.deinname.statsplugin.commands;

import de.deinname.statsplugin.items.ItemRarity;
import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.items.ItemStatUtils;
import de.deinname.statsplugin.items.ItemStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /itemadmin:
 *   setstat <key> <value>
 *   setability <name|null>
 *   clear
 *   setrarity <RARITY|#HEX>
 *   rename <name...>
 *   addlore <text...>
 *   clearlore
 *
 * - Rarity/Custom-Lore werden im PDC gespeichert und beim Lore-Rebuild erhalten.
 * - Keine Abhängigkeit zu ItemStatKeys.map() – direkte Switch-Zuordnung.
 */
public class ItemAdminCommand implements CommandExecutor, TabCompleter {

    private final ItemStatKeys keys;

    // PDC keys
    private final NamespacedKey KEY_RARITY;      // z.B. "LEGENDARY" oder "#FFAA00"
    private final NamespacedKey KEY_CUSTOM_LORE; // \n-separiert

    public ItemAdminCommand(ItemStatKeys keys) {
        this.keys = keys;
        var plugin = JavaPlugin.getProvidingPlugin(getClass());
        KEY_RARITY = new NamespacedKey(plugin, "rarity_hex_or_name");
        KEY_CUSTOM_LORE = new NamespacedKey(plugin, "custom_lore_lines");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur Ingame nutzbar.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "setstat"     -> handleSetStat(p, args);
            case "setability"  -> handleSetAbility(p, args);
            case "clear"       -> handleClear(p);
            case "setrarity"   -> handleSetRarity(p, args);
            case "rename"      -> handleRename(p, args);
            case "addlore"     -> handleAddLore(p, args);
            case "clearlore"   -> handleClearLore(p);
            default -> {
                p.sendMessage("§cUnbekannter Subcommand."); sendHelp(p); yield true;
            }
        };
    }

    private void sendHelp(Player p) {
        p.sendMessage("§e/itemadmin setstat <key> <value>");
        p.sendMessage("§e/itemadmin setability <name|null>");
        p.sendMessage("§e/itemadmin clear");
        p.sendMessage("§e/itemadmin setrarity <RARITY|#HEX>");
        p.sendMessage("§e/itemadmin rename <name...>");
        p.sendMessage("§e/itemadmin addlore <text...>");
        p.sendMessage("§e/itemadmin clearlore");
    }

    // ---------- Subcommands ----------

    private boolean handleSetStat(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage("§cNutzung: /itemadmin setstat <key> <value>"); return true; }
        ItemStack hand = requireHand(p); if (hand == null) return true;

        String keyName = args[1].toLowerCase(Locale.ROOT);
        double value;
        try { value = Double.parseDouble(args[2]); }
        catch (NumberFormatException e) { p.sendMessage("§cUngültige Zahl."); return true; }

        if (!writeStatByName(hand, keyName, value)) {
            p.sendMessage("§cUnbekannter Stat-Key: " + keyName +
                    " §7(erlaubt: damage, critchance, critdamage, health, armor, range, mana, manaregen)");
            return true;
        }

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aStat §f" + keyName + "§a = " + value);
        return true;
    }

    private boolean handleSetAbility(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin setability <name|null>"); return true; }
        ItemStack hand = requireHand(p); if (hand == null) return true;

        String name = args[1];
        if ("null".equalsIgnoreCase(name) || "none".equalsIgnoreCase(name)) name = null;

        // direkt im PDC setzen (unabhängig von Utils)
        ItemMeta meta = hand.getItemMeta();
        meta.getPersistentDataContainer().set(keys.ABILITY, PersistentDataType.STRING, name);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aAbility gesetzt: §f" + (name == null ? "keine" : name));
        return true;
    }

    private boolean handleClear(Player p) {
        ItemStack hand = requireHand(p); if (hand == null) return true;

        // Setze alle bekannten Stats auf 0
        setDoublePDC(hand, keys.DAMAGE, 0);
        setDoublePDC(hand, keys.CRIT_CHANCE, 0);
        setDoublePDC(hand, keys.CRIT_DAMAGE, 0);
        setDoublePDC(hand, keys.HEALTH, 0);
        setDoublePDC(hand, keys.ARMOR, 0);
        setDoublePDC(hand, keys.RANGE, 0);
        setDoublePDC(hand, keys.MANA_MAX, 0);
        setDoublePDC(hand, keys.MANA_REGEN, 0);

        // Ability löschen
        ItemMeta meta = hand.getItemMeta();
        meta.getPersistentDataContainer().remove(keys.ABILITY);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aAlle Stats entfernt (Rarity/Custom-Lore bleibt).");
        return true;
    }

    private boolean handleSetRarity(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin setrarity <RARITY|#HEX>"); return true; }
        ItemStack hand = requireHand(p); if (hand == null) return true;

        String token = args[1];
        TextColor color;
        String store;
        if (token.startsWith("#")) {
            try { color = TextColor.fromHexString(token); }
            catch (Exception e) { p.sendMessage("§cUngültige HEX-Farbe."); return true; }
            store = token;
        } else {
            ItemRarity r = ItemRarity.from(token);
            color = r.color();
            store = r.name();
        }

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_RARITY, PersistentDataType.STRING, store);

        // vorhandenen Namen (als Plaintext) übernehmen, nur Farbe ändern
        String currentName = meta.hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : hand.getType().name();
        meta.displayName(Component.text(currentName, color));

        // Vanilla-Attribute verstecken & Modifier entfernen
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setAttributeModifiers(null);

        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aRarity/Hex gesetzt: §f" + store);
        return true;
    }

    private boolean handleRename(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin rename <name...>"); return true; }
        ItemStack hand = requireHand(p); if (hand == null) return true;

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        ItemMeta meta = hand.getItemMeta();
        TextColor color = resolveStoredColor(meta.getPersistentDataContainer());
        if (color == null) color = TextColor.fromHexString("#FFFFFF");
        meta.displayName(Component.text(name, color));

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aItem umbenannt.");
        return true;
    }

    private boolean handleAddLore(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin addlore <text...>"); return true; }
        ItemStack hand = requireHand(p); if (hand == null) return true;

        String line = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        List<String> current = readCustomLore(pdc);
        current.add(line);
        writeCustomLore(pdc, current);

        hand.setItemMeta(meta);
        rebuildLorePreservingCustom(hand);

        p.sendMessage("§aLore-Zeile hinzugefügt.");
        return true;
    }

    private boolean handleClearLore(Player p) {
        ItemStack hand = requireHand(p); if (hand == null) return true;

        ItemMeta meta = hand.getItemMeta();
        meta.getPersistentDataContainer().remove(KEY_CUSTOM_LORE);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aCustom-Lore gelöscht.");
        return true;
    }

    // ---------- Helpers ----------

    private ItemStack requireHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            p.sendMessage("§cNimm ein Item in die Hand.");
            return null;
        }
        return hand;
    }

    /** setzt einen Stats-Key per Name (ohne ItemStatKeys.map()) */
    private boolean writeStatByName(ItemStack is, String key, double value) {
        switch (key) {
            case "damage"      -> setDoublePDC(is, keys.DAMAGE, value);
            case "critchance"  -> setDoublePDC(is, keys.CRIT_CHANCE, value);
            case "critdamage"  -> setDoublePDC(is, keys.CRIT_DAMAGE, value);
            case "health"      -> setDoublePDC(is, keys.HEALTH, value);
            case "armor"       -> setDoublePDC(is, keys.ARMOR, value);
            case "range"       -> setDoublePDC(is, keys.RANGE, value);
            case "mana", "manamax" -> setDoublePDC(is, keys.MANA_MAX, value);
            case "manaregen"   -> setDoublePDC(is, keys.MANA_REGEN, value);
            default -> { return false; }
        }
        return true;
    }

    private void setDoublePDC(ItemStack is, NamespacedKey k, double v) {
        ItemMeta meta = is.getItemMeta();
        meta.getPersistentDataContainer().set(k, PersistentDataType.DOUBLE, v);
        is.setItemMeta(meta);
    }

    private void rebuildLorePreservingCustom(ItemStack is) {
        if (is == null || !is.hasItemMeta()) return;
        ItemMeta meta = is.getItemMeta();

        // 1) Vanilla-Attribute verstecken & Modifier entfernen
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setAttributeModifiers(null);

        // 2) Stats lesen
        ItemStats s = ItemStatUtils.read(is, keys);
        List<Component> lore = new ArrayList<>();
        addLine(lore, s.damage(),     "+%s Damage");
        addLine(lore, s.critChance(), "+%s%% Crit Chance");
        addLine(lore, s.critDamage(), "+%s%% Crit Damage");
        addLine(lore, s.health(),     "+%s Health");
        addLine(lore, s.armor(),      "+%s Armor");
        addLine(lore, s.range(),      "+%s Range");
        addLine(lore, s.manaMax(),    "+%s Mana");
        addLine(lore, s.manaRegen(),  "+%s Mana Regeneration/s");

        // 3) Custom-Lore aus PDC anhängen
        List<String> custom = readCustomLore(meta.getPersistentDataContainer());
        if (!custom.isEmpty()) {
            lore.add(Component.text("")); // Separator
            for (String line : custom) {
                lore.add(Component.text(line, NamedTextColor.GRAY));
            }
        }

        // 4) Name ggf. mit gespeicherter Rarity/HEX einfärben
        TextColor color = resolveStoredColor(meta.getPersistentDataContainer());
        if (color != null) {
            String currentName = meta.hasDisplayName()
                    ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                    : is.getType().name();
            meta.displayName(Component.text(currentName, color));
        }

        meta.lore(lore);
        is.setItemMeta(meta);
    }

    private void addLine(List<Component> lore, double v, String fmt) {
        if (Math.abs(v) < 1e-9) return;
        lore.add(Component.text(formatNumber(v, fmt), NamedTextColor.GRAY));
    }

    private String formatNumber(double v, String fmt) {
        boolean isInt = Math.abs(v - Math.rint(v)) < 1e-9;
        String num = isInt ? String.valueOf((int) Math.rint(v))
                : String.valueOf(((double) Math.round(v * 10)) / 10.0);
        return String.format(fmt, num);
    }

    private List<String> readCustomLore(PersistentDataContainer pdc) {
        String raw = pdc.get(KEY_CUSTOM_LORE, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\n", -1)));
    }

    private void writeCustomLore(PersistentDataContainer pdc, List<String> lines) {
        String raw = String.join("\n", lines);
        pdc.set(KEY_CUSTOM_LORE, PersistentDataType.STRING, raw);
    }

    private TextColor resolveStoredColor(PersistentDataContainer pdc) {
        String s = pdc.get(KEY_RARITY, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) return null;
        if (s.startsWith("#")) {
            try { return TextColor.fromHexString(s); }
            catch (Exception e) { return null; }
        } else {
            return ItemRarity.from(s).color();
        }
    }

    // ---------- Tab-Complete ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            return Arrays.asList("setstat", "setability", "clear", "setrarity", "rename", "addlore", "clearlore");
        }
        if (args.length == 2 && "setstat".equalsIgnoreCase(args[0])) {
            return Arrays.asList("damage","critchance","critdamage","health","armor","range","mana","manaregen");
        }
        if (args.length == 2 && "setrarity".equalsIgnoreCase(args[0])) {
            return Arrays.stream(ItemRarity.values()).map(r -> r.name().toLowerCase()).collect(Collectors.toList());
        }
        return List.of();
    }
}
