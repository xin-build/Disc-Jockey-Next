package semmiedev.disc_jockey.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import semmiedev.disc_jockey.Main;
import semmiedev.disc_jockey.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple song list widget. Renders entries with:
 * - Background (selected / hovered)
 * - Star icon (favorite indicator)
 * - Note block icon
 * - Song name (truncated with "..." if too long)
 * 
 * Handles click-to-select and double-click-to-play.
 */
public class SongListWidget {
    public final List<SongListEntry> entries = new ArrayList<>();
    public SongListEntry selected;
    public double scroll;
    public int x, y, width, height;
    public final int entryHeight = 22;

    public void clear() { entries.clear(); selected = null; scroll = 0; }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.width = w; this.height = h;
    }

    public int maxScroll() {
        return Math.max(0, entries.size() * entryHeight - height);
    }

    public void clampScroll() {
        int max = maxScroll();
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
    }

    /** Returns the entry at screen coordinates (mx, my), or null */
    public SongListEntry entryAt(int mx, int my) {
        if (mx < x || mx > x + width || my < y || my > y + height) return null;
        int idx = (int)((my - y + scroll) / entryHeight);
        if (idx >= 0 && idx < entries.size()) return entries.get(idx);
        return null;
    }

    public void render(GuiGraphicsExtractor ex, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        clampScroll();

        int startIdx = (int)(scroll / entryHeight);
        int endIdx = Math.min(entries.size(), startIdx + height / entryHeight + 2);
        int baseY = y - (int)(scroll % entryHeight);

        for (int i = startIdx; i < endIdx; i++) {
            SongListEntry entry = entries.get(i);
            int ey = baseY + (i - startIdx) * entryHeight;
            if (ey + entryHeight < y || ey > y + height) continue;

            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= ey && mouseY < ey + entryHeight;
            boolean selected = entry == this.selected;

            // Background
            if (selected) ex.fill(x, ey, x + width, ey + entryHeight, ARGB.color(60, 100, 180, 255));
            else if (hovered) ex.fill(x, ey, x + width, ey + entryHeight, ARGB.color(30, 255, 255, 255));

            entry.render(ex, font, x, ey, width, hovered, selected);
        }
    }

    /** Base class for list entries */
    public abstract static class SongListEntry {
        public abstract void render(GuiGraphicsExtractor ex, Font font, int rx, int ry, int rw, boolean hovered, boolean selected);
        /** Called when left-clicked. Return true if handled. */
        public boolean onClick(int mouseX, int mouseY) { return false; }
    }

    /** A song row in the list */
    public static class SongRow extends SongListEntry {
        public final Song song;
        public boolean favorite;

        public SongRow(Song song) { this.song = song; }

        @Override
        public void render(GuiGraphicsExtractor ex, Font font, int rx, int ry, int rw, boolean hovered, boolean selected) {
            // Star icon
            String star = favorite ? "\u2605" : "\u2606";
            int starColor = favorite ? 0xFFFFAA00 : 0xFF808080;
            ex.text(font, star, rx + 4, ry + 3, starColor, false);

            // Note block icon
            ex.item(new ItemStack(Blocks.NOTE_BLOCK.asItem()), rx + 18, ry + 2);

            // Song name
            String name = song.displayName != null ? song.displayName : song.fileName;
            int maxTextW = rw - 58;
            if (font.width(name) > maxTextW) {
                String trimmed = name;
                while (!trimmed.isEmpty() && font.width(trimmed + "...") > maxTextW)
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                name = trimmed + "...";
            }
            ex.text(font, name, rx + 36, ry + 4, 0xFFFFFFFF, false);
        }
    }

    /** A folder/directory row */
    public static class FolderRow extends SongListEntry {
        public final String name;
        public final String path;
        public boolean isFavorites;
        public boolean clicked;

        public FolderRow(String name, String path) { this.name = name; this.path = path; }

        @Override
        public void render(GuiGraphicsExtractor ex, Font font, int rx, int ry, int rw, boolean hovered, boolean selected) {
            // Folder icon or favorites icon
            ItemStack icon = new ItemStack(isFavorites ? Items.GOLDEN_APPLE : Items.BOOK);
            ex.item(icon, rx + 4, ry + 2);

            // Name
            String display = name;
            int maxTextW = rw - 30;
            if (font.width(display) > maxTextW) {
                String trimmed = display;
                while (!trimmed.isEmpty() && font.width(trimmed + "...") > maxTextW)
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                display = trimmed + "...";
            }
            ex.text(font, display, rx + 24, ry + 4, isFavorites ? 0xFFFFAA00 : 0xFFAAAAFF, false);
        }
    }
}
