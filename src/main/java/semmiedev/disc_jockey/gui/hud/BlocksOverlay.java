package semmiedev.disc_jockey.gui.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import semmiedev.disc_jockey.Main;
import semmiedev.disc_jockey.Note;
import semmiedev.disc_jockey.Song;

import java.util.*;

public class BlocksOverlay implements HudElement {
    public static volatile Song activeSong;
    public static boolean forceShow;

    private static final int SCAN_RADIUS = 5;
    private int lastScanTick = -1;

    private List<Row> rows = new ArrayList<>();
    private int totalNeed = 0, totalHave = 0;

    public boolean hasData() { return !rows.isEmpty() && (activeSong != null || forceShow); }
    public List<Row> getRows() { return rows; }

    private static class Row {
        ItemStack stack;
        NoteBlockInstrument instrument;
        String blockName;
        int needed, have;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ex, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        boolean show = (activeSong != null) || forceShow;
        if (!show) return;
        if (mc.player == null || mc.level == null) return;

        int currentTick = mc.player.tickCount;
        if (currentTick - lastScanTick >= 20) {
            lastScanTick = currentTick;
            scanBlocks(mc);
        }

        if (rows.isEmpty()) return;

        Font font = mc.font;
        int panelWidth = Math.max(Main.config.hud.hudWidth, 160);
        int padding = 8;
        int lineH = 22;
        int headerH = 22;
        int radius = 4;

        int totalRows = rows.size();
        int panelH = headerH + totalRows * lineH + padding * 2;
        int panelX = mc.getWindow().getGuiScaledWidth() - panelWidth - 8;
        int panelY = 8;

        // Rounded background
        ex.fill(panelX + radius, panelY, panelX + panelWidth - radius, panelY + panelH, ARGB.color(200, 15, 15, 25));
        ex.fill(panelX, panelY + radius, panelX + panelWidth, panelY + panelH - radius, ARGB.color(200, 15, 15, 25));
        ex.fill(panelX + radius, panelY, panelX + panelWidth - radius, panelY + radius, ARGB.color(200, 15, 15, 25));
        ex.fill(panelX + radius, panelY + panelH - radius, panelX + panelWidth - radius, panelY + panelH, ARGB.color(200, 15, 15, 25));

        // Title
        String title = Component.translatable("disc_jockey.screen.blocks.title").getString()
                + " (" + totalHave + "/" + totalNeed + ")";
        ex.text(font, title, panelX + padding, panelY + padding, ARGB.color(255, 255, 255, 200), false);

        // Rows
        int nameMaxW = panelWidth - padding - 70;
        for (int i = 0; i < totalRows; i++) {
            Row row = rows.get(i);
            int ry = panelY + headerH + padding + i * lineH;
            boolean ok = row.have >= row.needed;
            int bg = ok ? ARGB.color(35, 50, 180, 80) : ARGB.color(35, 200, 50, 50);

            ex.fill(panelX + 4, ry, panelX + panelWidth - 4, ry + lineH, bg);
            ex.item(row.stack, panelX + padding, ry + 2);

            // Block name (truncated if needed)
            String name = row.blockName;
            if (font.width(name) > nameMaxW) {
                while (!name.isEmpty() && font.width(name + "...") > nameMaxW)
                    name = name.substring(0, name.length() - 1);
                name += "...";
            }
            ex.text(font, name, panelX + padding + 22, ry + 2, 0xFFDDDDDD, false);

            // Count
            String cnt = row.have + "/" + row.needed;
            int cntColor = ok ? ARGB.color(255, 100, 255, 100) : ARGB.color(255, 255, 100, 100);
            int cntW = font.width(cnt);
            ex.text(font, cnt, panelX + panelWidth - padding - cntW, ry + 2, cntColor, false);
        }
    }

    private void scanBlocks(Minecraft mc) {
        rows.clear();
        totalNeed = 0;
        totalHave = 0;
        if (activeSong == null || activeSong.uniqueNotes == null || activeSong.uniqueNotes.isEmpty()) return;

        Map<NoteBlockInstrument, Integer> neededByInst = new LinkedHashMap<>();
        for (Note n : activeSong.uniqueNotes)
            neededByInst.merge(n.instrument(), 1, Integer::sum);

        BlockPos pp = mc.player.blockPosition();
        Map<NoteBlockInstrument, Integer> haveByInst = new HashMap<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++)
        for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++)
        for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
            BlockPos pos = pp.offset(dx, dy, dz);
            BlockState st = mc.level.getBlockState(pos);
            if (st.is(Blocks.NOTE_BLOCK))
                haveByInst.merge(st.getValue(BlockStateProperties.NOTEBLOCK_INSTRUMENT), 1, Integer::sum);
        }

        for (var e : neededByInst.entrySet()) {
            NoteBlockInstrument instr = e.getKey();
            int need = e.getValue();
            int have = Math.min(haveByInst.getOrDefault(instr, 0), need);
            Block block = Note.INSTRUMENT_BLOCKS.get(instr);
            Row r = new Row();
            r.stack = new ItemStack(block.asItem());
            r.instrument = instr;
            r.blockName = Component.translatable(block.getDescriptionId()).getString();
            r.needed = need; r.have = have;
            rows.add(r);
            totalNeed += need; totalHave += have;
        }
    }
}
