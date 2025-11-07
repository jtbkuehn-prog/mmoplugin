package de.deinname.statsplugin.commands;


import de.deinname.statsplugin.items.ItemStatKeys;
import de.deinname.statsplugin.items.ItemStatLore;
import de.deinname.statsplugin.items.ItemStatUtils;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


import java.util.*;


public class ItemAdminCommand implements CommandExecutor, TabCompleter {
    private final ItemStatKeys keys;
    private final Map<String, NamespacedKey> map;


    public ItemAdminCommand(ItemStatKeys keys){
        this.keys = keys;
        map = Map.of(
                "damage", keys.DAMAGE,
                "crit_chance", keys.CRIT_CHANCE,
                "crit_damage", keys.CRIT_DAMAGE,
                "health", keys.HEALTH,
                "armor", keys.ARMOR,
                "range", keys.RANGE,
                "mana_max", keys.MANA_MAX,
                "mana_regen", keys.MANA_REGEN
        );
    }


    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a){
        if (!(s instanceof Player p)) { s.sendMessage("Nur In-Game."); return true; }
        if (a.length < 1) { s.sendMessage("Usage: /itemadmin <setstat|setability|clear>"); return true; }
        ItemStack hand = p.getInventory().getItemInMainHand(); if (hand == null) { s.sendMessage("Kein Item in der Hand."); return true; }


        switch (a[0].toLowerCase()){
            case "setstat":
                if (a.length < 3) { s.sendMessage("/itemadmin setstat <stat> <value>"); return true; }
                NamespacedKey key = map.get(a[1].toLowerCase());
                if (key == null) { s.sendMessage("Unbekannter Stat."); return true; }
                try {
                    double v = Double.parseDouble(a[2]);
                    ItemStatUtils.writeStat(hand, key, v);
                    ItemStatLore.updateLore(hand, keys);
                    s.sendMessage(ChatColor.GREEN+"OK: "+a[1]+" = "+v);
                } catch (NumberFormatException ex){ s.sendMessage("Zahl erwartet."); }
                return true;
            case "setability":
                if (a.length < 2) { s.sendMessage("/itemadmin setability <id> (z.B. damage_boost)"); return true; }
                ItemStatUtils.writeAbility(hand, keys, a[1]);
                ItemStatLore.updateLore(hand, keys);
                s.sendMessage(ChatColor.GREEN+"OK: ability="+a[1]);
                return true;
            case "clear":
                map.values().forEach(k -> ItemStatUtils.writeStat(hand, k, 0));
                ItemStatUtils.writeAbility(hand, keys, null);
                ItemStatLore.updateLore(hand, keys);
                s.sendMessage(ChatColor.YELLOW+"Item-Stats gelöscht.");
                return true;
        }
        return true;
    }


    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
        if (args.length == 1) return Arrays.asList("setstat","setability","clear");
        if (args.length == 2 && args[0].equalsIgnoreCase("setstat")) return new ArrayList<>(map.keySet());
        if (args.length == 2 && args[0].equalsIgnoreCase("setability")) return Arrays.asList("damage_boost");
        return List.of();
    }
}