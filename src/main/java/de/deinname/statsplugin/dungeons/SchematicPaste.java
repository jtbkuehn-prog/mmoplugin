package de.deinname.statsplugin.dungeons;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.WorldEditException;   // <--- WICHTIG

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SchematicPaste {

    private final Clipboard clipboard;

    public SchematicPaste(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Schematic nicht gefunden: " + file.getAbsolutePath());
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("Unbekanntes Schematic-Format: " + file.getName());
        }

        try (FileInputStream fis = new FileInputStream(file);
             ClipboardReader reader = format.getReader(fis)) {
            this.clipboard = reader.read();
        }
    }

    public void paste(org.bukkit.World bukkitWorld, int x, int y, int z) {
        World weWorld = BukkitAdapter.adapt(bukkitWorld);

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);

            Operation operation = holder
                    .createPaste(editSession)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(false)
                    .build();

            try {
                Operations.complete(operation);
            } catch (WorldEditException ex) {
                ex.printStackTrace();
                // Optional: sauber loggen
                // Bukkit.getLogger().warning("[Dungeons] Fehler beim Pasten eines Schematics: " + ex.getMessage());
            }

        }
    }
}
