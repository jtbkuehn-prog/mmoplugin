package de.deinname.statsplugin.commands;

import de.deinname.statsplugin.PlayerLevel;
import de.deinname.statsplugin.PlayerStats;
import de.deinname.statsplugin.StatsManager;
import de.deinname.statsplugin.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final StatsManager statsManager;
    private final ManaManager manaManager;

    public StatsCommand(StatsManager statsManager, ManaManager manaManager){
        this.statsManager = statsManager;
        this.manaManager = manaManager;
    }

    // ---------- Helpers (Config) ----------
    private JavaPlugin getPlugin(){
        Plugin p = Bukkit.getPluginManager().getPlugin("StatsPlugin");
        if (p instanceof JavaPlugin jp) return jp;
        return null;
    }

    private Map<String, String> labels(){
        Map<String,String> out = new HashMap<>();
        out.put("header_stats", "=== Stats ===");
        out.put("damage","Damage");
        out.put("critchance","Crit Chance");
        out.put("critdamage","Crit Damage");
        out.put("attackspeed","Attack Speed");
        out.put("range","Range");
        out.put("health","Health");
        out.put("armor","Armor");
        out.put("mana","Mana");
        out.put("manaregen","Mana Regen");
        out.put("healthregen","Health Regen");
        out.put("speed","Speed");
        out.put("header_level","=== Level & XP ===");
        out.put("level","Level");
        out.put("xp","XP");
        out.put("skillpoints","Skill Points");

        JavaPlugin pl = getPlugin();
        if (pl != null){
            ConfigurationSection sec = pl.getConfig().getConfigurationSection("stats_display.labels");
            if (sec != null){
                for (String k : sec.getKeys(false)){
                    out.put(k.toLowerCase(Locale.ROOT), sec.getString(k));
                }
            }
        }
        return out;
    }

    private Map<String, TextColor> colors(){
        Map<String,TextColor> out = new HashMap<>();
        out.put("damage",      TextColor.fromHexString("#FF5555"));
        out.put("critchance",  TextColor.fromHexString("#55FFFF"));
        out.put("critdamage",  TextColor.fromHexString("#FFD166"));
        out.put("attackspeed", TextColor.fromHexString("#FFD166"));
        out.put("range",       TextColor.fromHexString("#55FF55"));
        out.put("health",      TextColor.fromHexString("#FF4444"));
        out.put("armor",       TextColor.fromHexString("#AAAAAA"));
        out.put("mana",        TextColor.fromHexString("#55AAFF"));
        out.put("manaregen",   TextColor.fromHexString("#55AAFF"));
        out.put("healthregen", TextColor.fromHexString("#FF77AA"));
        out.put("speed", TextColor.fromHexString("#FFFFFF"));

        JavaPlugin pl = getPlugin();
        if (pl != null){
            ConfigurationSection sec = pl.getConfig().getConfigurationSection("stats_display.colors");
            if (sec != null){
                for (String k : sec.getKeys(false)){
                    try { out.put(k.toLowerCase(Locale.ROOT), TextColor.fromHexString(sec.getString(k))); }
                    catch (Exception ignored) {}
                }
            }
        }
        return out;
    }

    private List<String> order(){
        List<String> def = Arrays.asList("damage","critchance","critdamage","attackspeed","range","health","armor","mana","manaregen","healthregen","speed");
        JavaPlugin pl = getPlugin();
        if (pl != null){
            List<String> conf = pl.getConfig().getStringList("stats_display.order");
            if (conf != null && !conf.isEmpty()){
                List<String> out = new ArrayList<>();
                for (String s : conf){
                    String k = s.toLowerCase(Locale.ROOT);
                    if (def.contains(k)) out.add(k);
                }
                return out;
            }
        }
        return def;
    }

    // ---------- Formatting ----------
    private Component line(String label, String value, TextColor color){
        return Component.text(label + ": ")
                .color(TextColor.fromHexString("#E0E0E0"))
                .append(Component.text(value).color(color))
                .decoration(TextDecoration.ITALIC, false);
    }
    private Component header(String text){
        return Component.text(text).color(TextColor.fromHexString("#FFD166")).decoration(TextDecoration.ITALIC,false);
    }
    private static String n(double v){
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((int)Math.rint(v));
        return String.valueOf(Math.round(v*10.0)/10.0);
    }

    // ---------- Command ----------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player self)){ sender.sendMessage("Nur ingame nutzbar."); return true; }

        if (args.length == 0){
            show(self, self);
            return true;
        }

        // /stats <player> [sub ...]
        Player maybe = Bukkit.getPlayerExact(args[0]);
        if (maybe != null){
            if (args.length == 1){
                show(self, maybe);
                return true;
            } else {
                String[] rest = Arrays.copyOfRange(args, 1, args.length);
                return handleSub(self, maybe, rest);
            }
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub){
            case "show":
            case "get":{
                Player target = (args.length>=2) ? resolve(self, args[1]) : self;
                if (target == null){ self.sendMessage(Component.text("Spieler nicht gefunden.").color(TextColor.fromHexString("#FF5555"))); return true; }
                show(self, target);
                return true;
            }
            case "set":
            case "add":{
                if (args.length < 4){ usage(self); return true; }
                Player target = resolve(self, args[1]);
                if (target == null){ self.sendMessage(Component.text("Spieler nicht gefunden.").color(TextColor.fromHexString("#FF5555"))); return true; }
                String key = args[2].toLowerCase(Locale.ROOT);
                double val;
                try{ val = Double.parseDouble(args[3]); }catch(Exception e){ self.sendMessage(Component.text("Zahl ungültig.").color(TextColor.fromHexString("#FF5555"))); return true; }
                if (sub.equals("set")) setBase(target, key, val);
                else addBase(target, key, val);
                statsManager.applyHealth(target);
                statsManager.applySpeed(target);
                self.sendMessage(Component.text((sub.equals("set")?"Gesetzt: ":"Addiert: ") + key + " = " + n(val) + " bei " + target.getName()).color(TextColor.fromHexString("#55FF55")).decoration(TextDecoration.ITALIC,false));
                return true;
            }
            case "reset":{
                Player target = (args.length>=2) ? resolve(self, args[1]) : self;
                if (target == null){ self.sendMessage(Component.text("Spieler nicht gefunden.").color(TextColor.fromHexString("#FF5555"))); return true; }
                // <- hier: deine Signatur nutzt Player, nicht UUID
                statsManager.resetStats(target);
                statsManager.applyHealth(target);
                statsManager.applySpeed(target);
                self.sendMessage(Component.text("Basestats von " + target.getName() + " zurückgesetzt.").color(TextColor.fromHexString("#55FF55")).decoration(TextDecoration.ITALIC,false));
                return true;
            }
            case "allocate":{
                if (args.length < 3){ usage(self); return true; }
                String key = args[1].toLowerCase(Locale.ROOT);
                int pts;
                try{ pts = Integer.parseInt(args[2]); }catch(Exception e){ self.sendMessage(Component.text("Punkte müssen ganzzahlig sein.").color(TextColor.fromHexString("#FF5555"))); return true; }
                PlayerLevel lvl = statsManager.getLevel(self);
                int have = lvl.getSkillPoints();
                if (pts <= 0 || have < pts){ self.sendMessage(Component.text("Nicht genug Skill Points. Verfügbar: " + have).color(TextColor.fromHexString("#FF5555"))); return true; }
                allocate(self, key, pts);
                lvl.setSkillPoints(have - pts);
                statsManager.applyHealth(self);
                statsManager.applySpeed(self);
                show(self, self);
                return true;
            }
            default:
                usage(self);
                return true;
        }
    }

    private boolean handleSub(Player self, Player explicitTarget, String[] args){
        if (args.length == 0){ show(self, explicitTarget); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub){
            case "show":
            case "get":{
                Player t = (args.length>=2) ? resolve(self, args[1]) : explicitTarget;
                if (t == null){ self.sendMessage(Component.text("Spieler nicht gefunden.").color(TextColor.fromHexString("#FF5555"))); return true; }
                show(self, t);
                return true;
            }
            case "set":
            case "add":{
                if (args.length < 3){ usage(self); return true; }
                String key = args[1].toLowerCase(Locale.ROOT);
                double val;
                try{ val = Double.parseDouble(args[2]); }catch(Exception e){ self.sendMessage(Component.text("Zahl ungültig.").color(TextColor.fromHexString("#FF5555"))); return true; }
                if (sub.equals("set")) setBase(explicitTarget, key, val);
                else addBase(explicitTarget, key, val);
                statsManager.applyHealth(explicitTarget);
                statsManager.applySpeed(explicitTarget);
                self.sendMessage(Component.text((sub.equals("set")? "Gesetzt: " : "Addiert: ") + key + " = " + n(val) + " bei " + explicitTarget.getName()).color(TextColor.fromHexString("#55FF55")).decoration(TextDecoration.ITALIC,false));
                return true;
            }
            case "reset":{
                // <- hier: Player statt UUID
                statsManager.resetStats(explicitTarget);
                statsManager.applyHealth(explicitTarget);
                statsManager.applySpeed(explicitTarget);
                self.sendMessage(Component.text("Basestats von " + explicitTarget.getName() + " zurückgesetzt.").color(TextColor.fromHexString("#55FF55")).decoration(TextDecoration.ITALIC,false));
                return true;
            }
            default:
                usage(self);
                return true;
        }
    }

    private Player resolve(Player self, String token){
        if (token.equalsIgnoreCase("self") || token.equalsIgnoreCase("me")) return self;
        return Bukkit.getPlayerExact(token);
    }

    private void setBase(Player p, String key, double v){
        PlayerStats s = statsManager.getStats(p);
        switch (key){
            case "damage"      -> s.setBaseDamage(v);
            case "critchance"  -> s.setBaseCritChance(v);
            case "critdamage"  -> s.setBaseCritDamage(v);
            case "attackspeed" -> s.setBaseAttackSpeed(v);
            case "range"       -> s.setBaseRange(v);
            case "health"      -> s.setBaseHealth(v);
            case "armor"       -> s.setBaseArmor(v);
            case "mana"        -> s.setBaseMana(v);
            case "manaregen"   -> s.setBaseManaRegen(v);
            case "healthregen" -> s.setBaseHealthRegen(v);
            case "speed"       -> s.setBaseSpeed(v);
        }
    }

    private void addBase(Player p, String key, double dv){
        PlayerStats s = statsManager.getStats(p);
        switch (key){
            case "damage"      -> s.setBaseDamage(s.getDamage()+dv);
            case "critchance"  -> s.setBaseCritChance(s.getCritChance()+dv);
            case "critdamage"  -> s.setBaseCritDamage(s.getCritDamage()+dv);
            case "attackspeed" -> s.setBaseAttackSpeed(s.getAttackSpeed()+dv);
            case "range"       -> s.setBaseRange(s.getRange()+dv);
            case "health"      -> s.setBaseHealth(s.getHealth()+dv);
            case "armor"       -> s.setBaseArmor(s.getArmor()+dv);
            case "mana"        -> s.setBaseMana(s.getMana()+dv);
            case "manaregen"   -> s.setBaseManaRegen(s.getManaRegen()+dv);
            case "healthregen" -> s.setBaseHealthRegen(s.getHealthRegen()+dv);
            case "speed" -> s.setBaseSpeed(s.getSpeed()+dv);
        }
    }
    private void allocate(Player self, String key, int pts){
        PlayerStats s = statsManager.getStats(self);
        switch (key){
            case "damage"      -> s.setBaseDamage(s.getDamage()+pts);
            case "health"      -> s.setBaseHealth(s.getHealth()+pts);
            case "armor"       -> s.setBaseArmor(s.getArmor()+pts);
            case "range"       -> s.setBaseRange(s.getRange()+pts);
            case "attackspeed" -> s.setBaseAttackSpeed(s.getAttackSpeed()+pts);
            case "critchance"  -> s.setBaseCritChance(s.getCritChance()+pts);
            case "critdamage"  -> s.setBaseCritDamage(s.getCritDamage()+pts);
            case "mana"        -> s.setBaseMana(s.getMana()+pts);
            case "manaregen"   -> s.setBaseManaRegen(s.getManaRegen()+pts);
            case "healthregen" -> s.setBaseHealthRegen(s.getHealthRegen()+pts);
            case "speed" -> s.setBaseSpeed(s.getSpeed()+pts);
        }
    }

    private void show(Player viewer, Player target){
        Map<String,String> lbl = labels();
        Map<String,TextColor> col = colors();
        List<String> ord = order();

        PlayerStats ps = statsManager.getStats(target);
        double dmg = ps.getDamage();
        double cc  = ps.getCritChance();
        double cd  = ps.getCritDamage();
        double as_ = ps.getAttackSpeed();
        double rg  = ps.getRange();
        double hp  = ps.getHealth();
        double ar  = ps.getArmor();
        double sp  = ps.getSpeed();

        double mMax = manaManager.getMax(target);
        double mReg = manaManager.getRegen(target);
        double hpr  = statsManager.getTotalHealthRegen(target);


        PlayerLevel lvl = statsManager.getLevel(target);

        viewer.sendMessage(header(lbl.getOrDefault("header_stats","=== Stats ===")));
        for (String k : ord){
            switch (k){
                case "damage"      -> viewer.sendMessage(line(lbl.get("damage"),      n(dmg), col.get("damage")));
                case "critchance"  -> viewer.sendMessage(line(lbl.get("critchance"),  n(cc)  + "%",  col.get("critchance")));
                case "critdamage"  -> viewer.sendMessage(line(lbl.get("critdamage"),  n(cd)  + "%",  col.get("critdamage")));
                case "attackspeed" -> viewer.sendMessage(line(lbl.get("attackspeed"), n(as_),        col.get("attackspeed")));
                case "range"       -> viewer.sendMessage(line(lbl.get("range"),       n(rg),         col.get("range")));
                case "health"      -> viewer.sendMessage(line(lbl.get("health"),      n(hp),         col.get("health")));
                case "armor"       -> viewer.sendMessage(line(lbl.get("armor"),       n(ar),         col.get("armor")));
                case "mana"        -> viewer.sendMessage(line(lbl.get("mana"),        n(mMax),       col.get("mana")));
                case "manaregen"   -> viewer.sendMessage(line(lbl.get("manaregen"),   n(mReg)+"/s",  col.get("manaregen")));
                case "healthregen" -> viewer.sendMessage(line(lbl.get("healthregen"), n(hpr)+"/s",   col.get("healthregen")));
                case "speed" -> viewer.sendMessage(line(lbl.get("speed"), n(sp),   col.get("speed")));
            }
        }

        viewer.sendMessage(header(lbl.getOrDefault("header_level","=== Level & XP ===")));
        viewer.sendMessage(line(lbl.getOrDefault("level","Level"), String.valueOf(lvl.getLevel()), TextColor.fromHexString("#FFD166")));
        viewer.sendMessage(line(lbl.getOrDefault("xp","XP"), (int)lvl.getXP() + " / " + (int)lvl.getRequiredXP(), TextColor.fromHexString("#E0E0E0")));
        viewer.sendMessage(line(lbl.getOrDefault("skillpoints","Skill Points"), String.valueOf(lvl.getSkillPoints()), TextColor.fromHexString("#F7A8B8")));
    }

    // ---------- TabComplete ----------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        if (args.length == 1){
            List<String> base = new ArrayList<>();
            base.add("show"); base.add("get"); base.add("set"); base.add("add"); base.add("reset"); base.add("allocate");
            String pref = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()){
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pref)) base.add(p.getName());
            }
            return base;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("reset"))){
            List<String> out = new ArrayList<>();
            out.add("self");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return out;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))){
            return Arrays.asList("damage","critchance","critdamage","attackspeed","range","health","armor","mana","manaregen","healthregen","speed");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("allocate")){
            return Arrays.asList("damage","health","armor","range","attackspeed","critchance","critdamage","mana","manaregen","healthregen","speed");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("allocate")){
            return Collections.singletonList("<punkte>");
        }
        return Collections.emptyList();
    }

    // ---------- usage (jetzt vorhanden) ----------
    private void usage(Player p){
        p.sendMessage(Component.text("/stats [player]").color(TextColor.fromHexString("#AAAAAA")).decoration(TextDecoration.ITALIC,false));
        p.sendMessage(Component.text("/stats get [self|player]").color(TextColor.fromHexString("#AAAAAA")).decoration(TextDecoration.ITALIC,false));
        p.sendMessage(Component.text("/stats set <self|player> <key> <value>").color(TextColor.fromHexString("#AAAAAA")).decoration(TextDecoration.ITALIC,false));
        p.sendMessage(Component.text("/stats add <self|player> <key> <delta>").color(TextColor.fromHexString("#AAAAAA")).decoration(TextDecoration.ITALIC,false));
        p.sendMessage(Component.text("/stats reset [self|player]").color(TextColor.fromHexString("#AAAAAA")).decoration(TextDecoration.ITALIC,false));
        p.sendMessage(Component.text("/stats allocate <key> <points>").color(TextColor.fromHexString("#AAAAAA")).decoration(TextDecoration.ITALIC,false));
    }
}
