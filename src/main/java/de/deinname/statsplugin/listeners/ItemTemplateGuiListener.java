package de.deinname.statsplugin.listeners;

import de.deinname.statsplugin.commands.ItemTemplateCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ItemTemplateGuiListener implements Listener {

    private static JavaPlugin plugin;

    private static NamespacedKey KEY_TEMPLATE_NAME;
    private static NamespacedKey KEY_PAGE;
    private static NamespacedKey KEY_ACTION;

    private static final String ACTION_PREV = "prev";
    private static final String ACTION_NEXT = "next";

    private static final int PAGE_SIZE = 45;
    private static final int INV_SIZE  = 54;

    private static final int CONFIRM_SIZE = 27;
    private static final String CONFIRM_TITLE_PREFIX = "Template löschen: ";

    public ItemTemplateGuiListener(JavaPlugin pl) {
        plugin = pl;
        KEY_TEMPLATE_NAME = new NamespacedKey(pl, "itemtpl_name");
        KEY_PAGE          = new NamespacedKey(pl, "itemtpl_page");
        KEY_ACTION        = new NamespacedKey(pl, "itemtpl_action");
    }

    // ====== Haupt-GUI ======

    public static void openTemplateInventory(Player p, int page) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("saved-items");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            p.sendMessage("§7Es sind noch keine Item-Vorlagen gespeichert.");
            return;
        }

        List<String> names = new ArrayList<>(sec.getKeys(false));
        Collections.sort(names);

        int total = names.size();
        int totalPages = (total - 1) / PAGE_SIZE + 1;
        if (totalPages < 1) totalPages = 1;

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int startIndex = page * PAGE_SIZE;

        String title = ItemTemplateCommand.TEMPLATE_INV_TITLE + " §7(" + (page + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        int slot = 0;
        for (int i = startIndex; i < total && slot < PAGE_SIZE; i++, slot++) {
            String name = names.get(i);

            ItemStack stored = sec.getItemStack(name); // << WICHTIG: nur name
            if (stored == null) continue;

            ItemStack icon = stored.clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a" + name + " §7(Linksklick: erhalten, Rechtsklick: löschen)");
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(KEY_TEMPLATE_NAME, PersistentDataType.STRING, name);
                icon.setItemMeta(meta);
            }
            inv.setItem(slot, icon);
        }

        // Navigation
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta m = prev.getItemMeta();
            m.setDisplayName("§e« Vorherige Seite");
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(KEY_ACTION, PersistentDataType.STRING, ACTION_PREV);
            pdc.set(KEY_PAGE, PersistentDataType.INTEGER, page - 1);
            prev.setItemMeta(m);
            inv.setItem(45, prev);
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta mInfo = info.getItemMeta();
        mInfo.setDisplayName("§7Seite §e" + (page + 1) + "§7/§e" + totalPages);
        info.setItemMeta(mInfo);
        inv.setItem(49, info);

        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta m = next.getItemMeta();
            m.setDisplayName("§eNächste Seite »");
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(KEY_ACTION, PersistentDataType.STRING, ACTION_NEXT);
            pdc.set(KEY_PAGE, PersistentDataType.INTEGER, page + 1);
            next.setItemMeta(m);
            inv.setItem(53, next);
        }

        p.openInventory(inv);
    }

    // ====== Bestätigungs-GUI ======

    private static void openConfirmInventory(Player p, String templateName) {
        Inventory inv = Bukkit.createInventory(
                null,
                CONFIRM_SIZE,
                CONFIRM_TITLE_PREFIX + templateName
        );

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);

        for (int i = 0; i < CONFIRM_SIZE; i++) {
            inv.setItem(i, filler);
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e" + templateName);
        info.setItemMeta(im);
        inv.setItem(13, info);

        ItemStack yes = new ItemStack(Material.GREEN_WOOL);
        ItemMeta ym = yes.getItemMeta();
        ym.setDisplayName("§aJa, löschen");
        yes.setItemMeta(ym);
        inv.setItem(11, yes);

        ItemStack no = new ItemStack(Material.RED_WOOL);
        ItemMeta nm = no.getItemMeta();
        nm.setDisplayName("§cNein, abbrechen");
        no.setItemMeta(nm);
        inv.setItem(15, no);

        p.openInventory(inv);
    }

    // ====== Click-Handler ======

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;

        String title = e.getView().getTitle();

        // --- 1) Haupt-Template-GUI ---
        if (title.startsWith(ItemTemplateCommand.TEMPLATE_INV_TITLE)) {
            e.setCancelled(true); // ❗ verhindert rausnehmen/verschieben

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (!clicked.hasItemMeta()) return;

            ItemMeta meta = clicked.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            String action = pdc.get(KEY_ACTION, PersistentDataType.STRING);
            if (action != null) {
                Integer targetPage = pdc.get(KEY_PAGE, PersistentDataType.INTEGER);
                if (targetPage == null) return;
                openTemplateInventory(p, targetPage);
                return;
            }

            String tplName = pdc.get(KEY_TEMPLATE_NAME, PersistentDataType.STRING);
            if (tplName == null || tplName.isEmpty()) return;

            ClickType click = e.getClick();

            // Rechtsklick -> Bestätigungs-GUI
            if (click.isRightClick()) {
                openConfirmInventory(p, tplName);
                return;
            }

            // Linksklick -> Item geben
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("saved-items");
            if (sec == null) {
                p.sendMessage("§cKeine saved-items Sektion gefunden.");
                return;
            }

            ItemStack stored = sec.getItemStack(tplName);
            if (stored == null) {
                p.sendMessage("§cVorlage §e" + tplName + " §cexistiert nicht mehr.");
                return;
            }

            ItemStack toGive = stored.clone();
            Map<Integer, ItemStack> rest = p.getInventory().addItem(toGive);
            if (!rest.isEmpty()) {
                p.getWorld().dropItemNaturally(p.getLocation(), toGive);
                p.sendMessage("§eDein Inventar war voll – Item wurde gedroppt.");
            }

            p.sendMessage("§aTemplate §e" + tplName + " §aerhalten.");
            return;
        }

        // --- 2) Bestätigungs-GUI ---
        if (title.startsWith(CONFIRM_TITLE_PREFIX)) {
            e.setCancelled(true);

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String tplName = title.substring(CONFIRM_TITLE_PREFIX.length()).trim();
            if (tplName.isEmpty()) {
                p.closeInventory();
                return;
            }

            int slot = e.getRawSlot();

            if (slot == 11 && clicked.getType() == Material.GREEN_WOOL) {
                String path = "saved-items." + tplName;
                if (plugin.getConfig().get(path) == null) {
                    p.sendMessage("§cVorlage §e" + tplName + " §cwurde bereits gelöscht.");
                } else {
                    plugin.getConfig().set(path, null);
                    plugin.saveConfig();
                    p.sendMessage("§aVorlage §e" + tplName + " §awurde gelöscht.");
                }
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        ItemTemplateGuiListener.openTemplateInventory(p, 0));
                return;
            }

            if (slot == 15 && clicked.getType() == Material.RED_WOOL) {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        ItemTemplateGuiListener.openTemplateInventory(p, 0));
            }
        }
    }
}
