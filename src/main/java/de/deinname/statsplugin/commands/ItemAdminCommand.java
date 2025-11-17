package de.deinname.statsplugin.commands;

import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.items.ItemRarityManager;
import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.items.ItemStatUtils;
import de.deinname.statsplugin.items.ItemStats;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
            case "setmodel"    -> handleSetModel(p, args);   // ⭐ NEU
            default -> { p.sendMessage("§cUnbekannter Subcommand."); sendHelp(p); yield true; }
        };

        // Nach JEDEM erfolgreichen Edit die Stats sofort neu berechnen:
        if (done) recalcNow(p);
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§e/itemadmin setstat <key> <value>");
        p.sendMessage("§e/itemadmin setability <name|null> [mana] [cooldown]");
        p.sendMessage("§e/itemadmin clear");
        p.sendMessage("§e/itemadmin setrarity <RARITY|#HEX>");
        p.sendMessage("§e/itemadmin rename <name...>");
        p.sendMessage("§e/itemadmin addlore <text...>");
        p.sendMessage("§e/itemadmin clearlore");
        p.sendMessage("§e/itemadmin setmodel <CustomModelData|null>"); // ⭐ NEU
    }

    // ------------ Subcommands ------------

    private boolean handleSetStat(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage("§cNutzung: /itemadmin setstat <key> <value>"); return false; }
        ItemStack hand = requireHand(p); if (hand == null) return false;

        String key = args[1].toLowerCase(Locale.ROOT);
        double value;
        try { value = Double.parseDouble(args[2]); } catch (NumberFormatException e) { p.sendMessage("§cZahl ungültig."); return false; }

        switch (key) {
            case "damage"      -> ItemStatUtils.writeStat(hand, keys.DAMAGE, value);
            case "critchance"  -> ItemStatUtils.writeStat(hand, keys.CRIT_CHANCE, value);
            case "critdamage"  -> ItemStatUtils.writeStat(hand, keys.CRIT_DAMAGE, value);
            case "health"      -> ItemStatUtils.writeStat(hand, keys.HEALTH, value);
            case "armor"       -> ItemStatUtils.writeStat(hand, keys.ARMOR, value);
            case "range"       -> ItemStatUtils.writeStat(hand, keys.RANGE, value);
            case "mana", "manamax" -> ItemStatUtils.writeStat(hand, keys.MANA_MAX, value);
            case "manaregen"   -> ItemStatUtils.writeStat(hand, keys.MANA_REGEN, value);
            case "healthregen" -> ItemStatUtils.writeStat(hand, keys.HEALTH_REGEN, value);
            case "attackspeed" -> ItemStatUtils.writeStat(hand, keys.ATTACKSPEED, value);
            case "speed"       -> ItemStatUtils.writeStat(hand, keys.SPEED, value);
            default -> { p.sendMessage("§cUnbekannter Stat-Key."); return false; }
        }

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aStat gesetzt.");
        return true;
    }

    private TextColor loreColor(String key, String defHex) {
        String path = "lore_colors." + key;
        String hex = plugin.getConfig().getString(path, defHex);
        try {
            return TextColor.fromHexString(hex);
        } catch (Exception e) {
            return TextColor.fromHexString(defHex);
        }
    }

    private boolean handleSetAbility(Player p, String[] args) {
        // /itemadmin setability <name|null> [mana] [cooldown]
        if (args.length < 2) {
            p.sendMessage("§cNutzung: /itemadmin setability <name|null> [mana] [cooldown]");
            return false;
        }

        ItemStack hand = requireHand(p);
        if (hand == null) return false;

        // Ability entfernen
        if ("null".equalsIgnoreCase(args[1]) || "none".equalsIgnoreCase(args[1])) {
            ItemMeta meta = hand.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.remove(keys.ABILITY);
            pdc.remove(keys.ABILITY_MANA);
            pdc.remove(keys.ABILITY_COOLDOWN);
            hand.setItemMeta(meta);

            rebuildLorePreservingCustom(hand);
            p.sendMessage("§aAbility entfernt.");
            return true;
        }

        if (args.length < 4) {
            p.sendMessage("§cNutzung: /itemadmin setability <name> <mana> <cooldownSek>");
            return false;
        }

        String abilityId = args[1];

        int manaCost;
        double cooldown;
        try {
            manaCost = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            p.sendMessage("§cMana-Kosten müssen eine ganze Zahl sein.");
            return false;
        }
        try {
            cooldown = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            p.sendMessage("§cCooldown muss eine Zahl sein (Sekunden, z.B. 3 oder 2.5).");
            return false;
        }

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.ABILITY,          PersistentDataType.STRING, abilityId.toLowerCase(Locale.ROOT));
        pdc.set(keys.ABILITY_MANA,     PersistentDataType.INTEGER, manaCost);
        pdc.set(keys.ABILITY_COOLDOWN, PersistentDataType.DOUBLE,  cooldown);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);

        p.sendMessage("§aAbility gesetzt: §e" + abilityId
                + " §7(Mana §b" + manaCost + "§7, CD §a" + cooldown + "s§7)");
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
        ItemStatUtils.writeStat(hand, keys.HEALTH_REGEN, 0);
        ItemStatUtils.writeStat(hand, keys.ATTACKSPEED, 0);
        ItemStatUtils.writeStat(hand, keys.SPEED,0);

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(keys.ABILITY);
        pdc.remove(keys.ABILITY_MANA);
        pdc.remove(keys.ABILITY_COOLDOWN);
        hand.setItemMeta(meta);

        rebuildLorePreservingCustom(hand);
        p.sendMessage("§aStats & Ability gelöscht (Rarity/Custom-Lore bleibt).");
        return true;
    }

    private boolean handleSetRarity(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cNutzung: /itemadmin setrarity <RARITY|#HEX>"); return false; }
        ItemStack hand = requireHand(p); if (hand == null) return false;

        String token = args[1];
        TextColor color = rarityMgr.get(token); // nur Farbe, ModelData bleibt unabhängig

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_RARITY, PersistentDataType.STRING, token);

        String plainName = meta.hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : hand.getType().name();
        meta.displayName(Component.text(plainName, color)
                .decoration(TextDecoration.ITALIC, false));

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
        meta.displayName(Component.text(name, color)
                .decoration(TextDecoration.ITALIC, false));

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

    // ⭐ NEU: CustomModelData setzen
    private boolean handleSetModel(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cNutzung: /itemadmin setmodel <CustomModelData|null>");
            return false;
        }

        ItemStack hand = requireHand(p);
        if (hand == null) return false;

        ItemMeta meta = hand.getItemMeta();

        if ("null".equalsIgnoreCase(args[1]) || "none".equalsIgnoreCase(args[1])) {
            meta.setCustomModelData(null);
            hand.setItemMeta(meta);
            p.sendMessage("§aCustomModelData entfernt.");
            return true;
        }

        int modelId;
        try {
            modelId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            p.sendMessage("§cUngültige Zahl: " + args[1]);
            return false;
        }

        meta.setCustomModelData(modelId);
        hand.setItemMeta(meta);

        p.sendMessage("§aCustomModelData gesetzt: §f" + modelId);
        return true;
    }

    // ------------ Recalc direkt nach Änderungen ------------

    private void recalcNow(Player p) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int lvl = stats.getLevel(p.getUniqueId()).getLevel();
            double baseMax   = plugin.getConfig().getDouble("mana.base-max", 50.0)
                    + lvl * plugin.getConfig().getDouble("mana.per-level", 5.0);
            double baseRegen = plugin.getConfig().getDouble("mana.base-regen", 1.0)
                    + lvl * plugin.getConfig().getDouble("mana.regen-per-level", 0.1);

            ItemStats total = ItemStats.zero();
            for (ItemStack armor : p.getInventory().getArmorContents()) {
                total = total.add(ItemStatUtils.read(armor, keys));
            }
            total = total.add(ItemStatUtils.read(p.getInventory().getItemInMainHand(), keys));

            stats.applyItemBonuses(p.getUniqueId(),
                    total.damage(), total.critChance(), total.critDamage(),
                    total.health(), total.armor(), total.range(), total.attackspeed(), total.speed());

            stats.applyHealth(p);
            stats.applySpeed(p);

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

        TextColor colDamage      = loreColor("damage",      "#FF5555");
        TextColor colCritChance  = loreColor("critchance",  "#55FFFF");
        TextColor colCritDamage  = loreColor("critdamage",  "#FFD700");
        TextColor colHealth      = loreColor("health",      "#FF5555");
        TextColor colArmor       = loreColor("armor",       "#AAAAAA");
        TextColor colRange       = loreColor("range",       "#55FF55");
        TextColor colMana        = loreColor("mana",        "#00AAFF");
        TextColor colManaRegen   = loreColor("manaregen",   "#00AAFF");
        TextColor colHealthRegen = loreColor("healthregen", "#FF7777");
        TextColor colAttackspeed = loreColor("attackspeed", "#FF7777");
        TextColor colSpeed       = loreColor("speed",       "#FFFFFF");
        TextColor colCustom      = loreColor("custom",      "#BFBFBF");

        addLineColored(lore, s.damage(),      "+%s Damage",              colDamage);
        addLineColored(lore, s.critChance(),  "+%s%% Crit Chance",       colCritChance);
        addLineColored(lore, s.critDamage(),  "+%s%% Crit Damage",       colCritDamage);
        addLineColored(lore, s.health(),      "+%s Health",              colHealth);
        addLineColored(lore, s.armor(),       "+%s Armor",               colArmor);
        addLineColored(lore, s.range(),       "+%s Range",               colRange);
        addLineColored(lore, s.manaMax(),     "+%s Mana",                colMana);
        addLineColored(lore, s.manaRegen(),   "+%s Mana Regeneration/s", colManaRegen);
        addLineColored(lore, s.healthRegen(), "+%s Health Regeneration/s", colHealthRegen);
        addLineColored(lore, s.attackspeed(), "+%s Attackspeed/s",       colAttackspeed);
        addLineColored(lore, s.speed(),       "+%s Speed",               colSpeed);

        List<String> customLines = readCustomLore(meta.getPersistentDataContainer());
        if (!customLines.isEmpty()) {
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            for (String line : customLines) {
                lore.add(Component.text(line)
                        .color(colCustom)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        TextColor nameColor = resolveStoredColor(meta.getPersistentDataContainer());
        if (nameColor != null) {
            String plainName = meta.hasDisplayName()
                    ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                    : is.getType().name();
            meta.displayName(Component.text(plainName, nameColor)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        is.setItemMeta(meta);

        // Ability-Block anhängen (wenn vorhanden)
        de.deinname.statsplugin.abilities.AbilityLore.appendAbilityLore(is, keys);
    }

    private void addLineColored(List<Component> lore, double v, String fmt, TextColor color) {
        if (Math.abs(v) < 1e-9) return;
        String txt = formatNumber(v, fmt);
        lore.add(Component.text(txt)
                .color(color)
                .decoration(TextDecoration.ITALIC, false));
    }

    private String formatNumber(double v, String fmt) {
        boolean isInt = Math.abs(v - Math.rint(v)) < 1e-9;
        String num = isInt ? String.valueOf((int) Math.rint(v))
                : String.valueOf(Math.round(v * 10) / 10.0);
        return String.format(fmt, num);
    }

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
            return Arrays.asList("setstat","setability","clear","setrarity","rename","addlore","clearlore","setmodel");
        }
        if (args.length == 2 && "setstat".equalsIgnoreCase(args[0])) {
            return Arrays.asList("damage","critchance","critdamage","health","armor","range","mana","manaregen","healthregen","attackspeed","speed");
        }
        if (args.length == 2 && "setrarity".equalsIgnoreCase(args[0])) {
            return rarityMgr.names().stream().map(String::toLowerCase).collect(Collectors.toList());
        }
        if (args.length == 2 && "setability".equalsIgnoreCase(args[0])) {
            return Arrays.asList("damage_boost"); // erweiterbar
        }
        return List.of();
    }
}
