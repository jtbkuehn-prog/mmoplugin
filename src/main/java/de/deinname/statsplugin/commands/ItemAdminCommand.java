package de.deinname.statsplugin.commands;

import de.deinname.statsplugin.PlayerLevel;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.items.ItemRarityManager;
import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.items.ItemStatUtils;
import de.deinname.statsplugin.items.ItemStats;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class ItemAdminCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final StatsManager stats;
    private final ManaManager mana;
    private final ItemStatKeys keys;
    private final ItemRarityManager rarityMgr;

    // PDC-Schlüssel für Rarity + Custom-Lore
    private final NamespacedKey KEY_RARITY;
    private final NamespacedKey KEY_CUSTOM_LORE;

    public ItemAdminCommand(JavaPlugin plugin, StatsManager stats, ManaManager mana, ItemStatKeys keys) {
        this.plugin = plugin;
        this.stats = stats;
        this.mana = mana;
        this.keys = keys;
        this.rarityMgr = new ItemRarityManager(plugin);
        this.KEY_RARITY = new NamespacedKey(plugin, "rarity_hex_or_name");
        this.KEY_CUSTOM_LORE = new NamespacedKey(plugin, "custom_lore_lines");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cNur Ingame nutzbar."); return true; }
        if (args.length == 0) { sendHelp(p); return true; }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean done = switch (sub) {
            case "setstat"     -> handleSetStat(p, args);
            case "setability"  -> handleSetAbility(p, args);
            case "clear"       -> handleClear(p);
            case "setrarity"   -> handleSetRarity(p, args);
            case "rename"      -> handleRename(p, args);
            case "addlore"     -> handleAddLore(p, args);
            case "clearlore"   -> handleClearLore(p);
            default -> { p.sendMessage("§cUnbekannter Subcommand."); sendHelp(p); yield true; }
        };

        // Nach JEDEM erfolgreichen Edit die Stats sofort neu berechnen:
        if (done) recalcNow(p);
        return true;
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

    // ------------ Subcommands ------------

    private boolean handleSetStat(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage("§cNutzung: /itemadmin setstat <key> <value>"); return false; }
        ItemStack hand = requireHand(p); if (hand == null) return false;

        String key = args[1].toLowerCase(Locale.ROOT);
        double value;
        try { value = Double.parseDouble(args[2]); } catch (NumberFormatException e) { p.sendMessage("§cZahl ungültig."); return false; }

        // WICHTIG: über deine Utils schreiben → garantiert kompatibel zum Reader
        switch (key) {
            case "damage"      -> ItemStatUtils.writeStat(hand, keys.DAMAGE, value);
            case "critchance"  -> ItemStatUtils.writeStat(hand, keys.CRIT_CHANCE, value);
            case "critdamage"  -> ItemStatUtils.writeStat(hand, keys.CRIT_DAMAGE, value);
            case "health"      -> ItemStatUtils.writeStat(hand, keys.HEALTH, value);
            case "armor"       -> ItemStatUtils.writeStat(hand, keys.ARMOR, value);
            case "range"       -> ItemStatUtils.writeStat(hand, keys.RANGE, value);
            case "mana", "manamax" -> ItemStatUtils.writeStat(hand, keys.MANA_MAX, value);
            case "manaregen"   -> ItemStatUtils.writeStat(hand, keys.MANA_REGEN, value);
            default -> { p.sendMessage("§cUnbekannter Stat-Key."); return false; }
        }

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aStat gesetzt.");
        return true;
    }

    private TextColor loreColor(String key, String defHex) {
        String path = "lore_colors." + key;
        String hex = plugin.getConfig().getString(path, defHex);
        try { return TextColor.fromHexString(hex); } catch (Exception e) { return TextColor.fromHexString(defHex); }
    }

    private boolean handleSetAbility(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin setability <name|null>"); return false; }
        ItemStack hand = requireHand(p); if (hand == null) return false;

        String name = args[1];
        if ("null".equalsIgnoreCase(name) || "none".equalsIgnoreCase(name)) name = null;

        // ebenfalls über Utils setzen
        ItemStatUtils.writeAbility(hand, keys, name);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aAbility gesetzt: " + (name == null ? "keine" : name));
        return true;
    }

    private boolean handleClear(Player p) {
        ItemStack hand = requireHand(p); if (hand == null) return false;

        ItemStatUtils.writeStat(hand, keys.DAMAGE, 0);
        ItemStatUtils.writeStat(hand, keys.CRIT_CHANCE, 0);
        ItemStatUtils.writeStat(hand, keys.CRIT_DAMAGE, 0);
        ItemStatUtils.writeStat(hand, keys.HEALTH, 0);
        ItemStatUtils.writeStat(hand, keys.ARMOR, 0);
        ItemStatUtils.writeStat(hand, keys.RANGE, 0);
        ItemStatUtils.writeStat(hand, keys.MANA_MAX, 0);
        ItemStatUtils.writeStat(hand, keys.MANA_REGEN, 0);
        ItemStatUtils.writeAbility(hand, keys, null);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aStats gelöscht (Rarity/Custom-Lore bleibt).");
        return true;
    }

    private boolean handleSetRarity(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin setrarity <RARITY|#HEX>"); return false; }
        ItemStack hand = requireHand(p); if (hand == null) return false;

        String token = args[1];
        TextColor color = rarityMgr.get(token);

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_RARITY, PersistentDataType.STRING, token);

        String plainName = meta.hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : hand.getType().name();
        meta.displayName(Component.text(plainName, color));

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setAttributeModifiers(null);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aRarity/Hex gesetzt: §f" + token);
        return true;
    }

    private boolean handleRename(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin rename <name...>"); return false; }
        ItemStack hand = requireHand(p); if (hand == null) return false;

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
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin addlore <text...>"); return false; }
        ItemStack hand = requireHand(p); if (hand == null) return false;

        String line = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<String> cur = readCustomLore(pdc);
        cur.add(line);
        writeCustomLore(pdc, cur);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aLore-Zeile hinzugefügt.");
        return true;
    }

    private boolean handleClearLore(Player p) {
        ItemStack hand = requireHand(p); if (hand == null) return false;

        ItemMeta meta = hand.getItemMeta();
        meta.getPersistentDataContainer().remove(KEY_CUSTOM_LORE);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aCustom-Lore gelöscht.");
        return true;
    }

    // ------------ Recalc direkt nach Änderungen ------------

    private void recalcNow(Player p) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Level-Basis
            int lvl = stats.getLevel(p.getUniqueId()).getLevel();
            double baseMax   = plugin.getConfig().getDouble("mana.base-max", 50.0)
                    + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
            double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0)
                    + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);

            // Item-Boni summieren
            ItemStats total = ItemStats.zero();
            for (ItemStack armor : p.getInventory().getArmorContents()) {
                total = total.add(ItemStatUtils.read(armor, keys));
            }
            total = total.add(ItemStatUtils.read(p.getInventory().getItemInMainHand(), keys));

            // In Stats übernehmen
            stats.applyItemBonuses(p.getUniqueId(),
                    total.damage(), total.critChance(), total.critDamage(),
                    total.health(), total.armor(), total.range());

            stats.applyHealth(p); // MaxHealth sofort updaten

            // Mana als base + items
            mana.setMax(p,   baseMax   + total.manaMax());
            mana.setRegen(p, baseRegen + total.manaRegen());
        });
    }

    // ------------ Lore-Build (Stats + Custom) ------------

    private void rebuildLorePreservingCustom(ItemStack is) {
        if (is == null || !is.hasItemMeta()) return;
        ItemMeta meta = is.getItemMeta();

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setAttributeModifiers(null);

        ItemStats s = ItemStatUtils.read(is, keys);
        List<Component> lore = new ArrayList<>();
        addLineColored(lore, s.damage(),     "+%s Damage",        loreColor("damage",     "#BBBBBB"));
        addLineColored(lore, s.critChance(), "+%s%% Crit Chance", loreColor("critchance", "#BBBBBB"));
        addLineColored(lore, s.critDamage(), "+%s%% Crit Damage", loreColor("critdamage", "#BBBBBB"));
        addLineColored(lore, s.health(),     "+%s Health",        loreColor("health",     "#BBBBBB"));
        addLineColored(lore, s.armor(),      "+%s Armor",         loreColor("armor",      "#BBBBBB"));
        addLineColored(lore, s.range(),      "+%s Range",         loreColor("range",      "#BBBBBB"));
        addLineColored(lore, s.manaMax(),    "+%s Mana",          loreColor("mana",       "#BBBBBB"));
        addLineColored(lore, s.manaRegen(),  "+%s Mana Regeneration/s", loreColor("manaregen", "#BBBBBB"));


        List<String> custom = readCustomLore(meta.getPersistentDataContainer());
        if (!custom.isEmpty()) {
            lore.add(Component.text(""));
            for (String line : custom) lore.add(Component.text(line, NamedTextColor.GRAY));
        }

        // Name ggf. anhand gespeicherter Rarity/HEX einfärben
        TextColor color = resolveStoredColor(meta.getPersistentDataContainer());
        if (color != null) {
            String plainName = meta.hasDisplayName()
                    ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                    : is.getType().name();
            meta.displayName(Component.text(plainName, color));
        }

        meta.lore(lore);
        is.setItemMeta(meta);
    }

    private void addLineColored(List<Component> lore, double v, String fmt, TextColor color) {
        if (Math.abs(v) < 1e-9) return;
        lore.add(Component.text(formatNumber(v, fmt)).color(color));
    }

    private Component textColored(String s, TextColor color) {
        return Component.text(s).color(color);
    }

    private void addLine(List<Component> lore, double v, String fmt) {
        if (Math.abs(v) < 1e-9) return;
        lore.add(Component.text(formatNumber(v, fmt), NamedTextColor.GRAY));
    }

    private String formatNumber(double v, String fmt) {
        boolean isInt = Math.abs(v - Math.rint(v)) < 1e-9;
        String num = isInt ? String.valueOf((int) Math.rint(v))
                : String.valueOf(Math.round(v * 10) / 10.0);
        return String.format(fmt, num);
    }

    // ------------ PDC & Utils ------------

    private ItemStack requireHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            p.sendMessage("§cNimm ein Item in die Hand.");
            return null;
        }
        return hand;
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
        String token = pdc.get(KEY_RARITY, PersistentDataType.STRING);
        if (token == null || token.isEmpty()) return null;
        return rarityMgr.get(token);
    }

    // ------------ Tabs ------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            return Arrays.asList("setstat","setability","clear","setrarity","rename","addlore","clearlore");
        }
        if (args.length == 2 && "setstat".equalsIgnoreCase(args[0])) {
            return Arrays.asList("damage","critchance","critdamage","health","armor","range","mana","manaregen");
        }
        if (args.length == 2 && "setrarity".equalsIgnoreCase(args[0])) {
            return rarityMgr.names().stream().map(String::toLowerCase).collect(Collectors.toList());
        }
        if (args.length == 2 && "setability".equalsIgnoreCase(args[0])) {
            return Arrays.asList("damage_boost"); // ergänzbar
        }
        return List.of();
    }

}
