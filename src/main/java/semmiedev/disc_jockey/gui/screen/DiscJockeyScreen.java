package semmiedev.disc_jockey.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import semmiedev.disc_jockey.Main;
import semmiedev.disc_jockey.Song;
import semmiedev.disc_jockey.SongLoader;
import semmiedev.disc_jockey.SongPlayer;
import semmiedev.disc_jockey.gui.SongListWidget;
import semmiedev.disc_jockey.gui.hud.BlocksOverlay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DiscJockeyScreen extends Screen {
    private static final Component
            TXT_PLAY        = Component.translatable("disc_jockey.screen.play"),
            TXT_PLAY_STOP   = Component.translatable("disc_jockey.screen.play.stop"),
            TXT_PREVIEW     = Component.translatable("disc_jockey.screen.preview"),
            TXT_PREVIEW_STOP = Component.translatable("disc_jockey.screen.preview.stop"),
            TXT_BLOCKS      = Component.translatable("disc_jockey.screen.blocks"),
            TXT_BLOCKS_OFF  = Component.translatable("disc_jockey.screen.blocks.off"),
            TXT_SHUFFLE     = Component.translatable("disc_jockey.screen.shuffle"),
            TXT_LOOP_OFF    = Component.translatable("disc_jockey.screen.loop.off"),
            TXT_LOOP_LIST   = Component.translatable("disc_jockey.screen.loop.list"),
            TXT_LOOP_ONE    = Component.translatable("disc_jockey.screen.loop.one"),
            TXT_LOOP_SHUFFLE= Component.translatable("disc_jockey.screen.loop.shuffle"),
            TXT_SEARCH      = Component.translatable("disc_jockey.screen.search"),
            TXT_DROP        = Component.translatable("disc_jockey.screen.drop_confirm"),
            TXT_ALL         = Component.translatable("disc_jockey.screen.all_songs"),
            TXT_FAV         = Component.translatable("disc_jockey.screen.favorites"),
            TXT_SONGS       = Component.translatable("disc_jockey.screen.songs"),
            TXT_RELOAD      = Component.translatable("disc_jockey.screen.reload"),
            TXT_SORT_NAME   = Component.translatable("disc_jockey.screen.sort.name"),
            TXT_SORT_CREATED = Component.translatable("disc_jockey.screen.sort.created"),
            TXT_SORT_PLAYED  = Component.translatable("disc_jockey.screen.sort.played"),
            TXT_SORT_ASC    = Component.translatable("disc_jockey.screen.sort.asc"),
            TXT_SORT_DESC   = Component.translatable("disc_jockey.screen.sort.desc"),
            TXT_SPEED       = Component.translatable("disc_jockey.screen.speed"),
            TXT_JUMP        = Component.translatable("disc_jockey.screen.progress.jump"),
            TXT_CREATIVE    = Component.translatable("disc_jockey.screen.creative_warn");

    private final SongListWidget songList = new SongListWidget();
    private boolean shouldRefresh, isLoading = true, showFavorites;
    private String query = "", currentDir = "";

    private Button btnBack, btnFavorites, btnSort, btnOrder, btnReload, btnSettings, btnOpenFolder;
    private Button btnPlay, btnPreview, btnBlocks, btnShuffle, btnLoop;
    private Button btnSpeedDown, btnSpeedUp, btnSpeedDisplay;
    private EditBox searchBar, jumpInput;

    private final Map<Button, Component> tooltips = new IdentityHashMap<>();

    private static final int PROGRESS_BAR_Y_OFFSET = 88;
    private static final int PROGRESS_BAR_H = 12;
    private static final int PROGRESS_BAR_PAD = 8;

    private String lastJumpText = "", lastAppliedText = "";
    private List<Song> cachedSongs = List.of();

    private static final int BTN_SMALL = 20;
    private static final int BTN_WIDE  = 52;
    private static final int BTN_GAP   = 4;

    public DiscJockeyScreen() { super(Main.NAME); }

    @Override
    protected void init() {
        shouldRefresh = true;
        tooltips.clear();
        int topY = 6;

        btnBack = Button.builder(Component.literal("\u2190"), b -> {
            if (showFavorites) { showFavorites = false; }
            else if (!currentDir.isEmpty()) {
                int idx = currentDir.lastIndexOf(File.separator);
                currentDir = idx >= 0 ? currentDir.substring(0, idx) : "";
            }
            query = ""; songList.selected = null; shouldRefresh = true;
        }).bounds(4, topY, BTN_SMALL, BTN_SMALL).build();
        addRenderableWidget(btnBack);
        tooltips.put(btnBack, Component.translatable("disc_jockey.tooltip.back"));

        btnFavorites = Button.builder(Component.literal("\u2605"), b -> {
            showFavorites = !showFavorites; currentDir = ""; query = "";
            songList.selected = null; shouldRefresh = true;
        }).bounds(28, topY, BTN_SMALL, BTN_SMALL).build();
        addRenderableWidget(btnFavorites);
        tooltips.put(btnFavorites, Component.translatable("disc_jockey.tooltip.favorites"));

        btnSort = Button.builder(Component.literal("AZ"), b -> {
            var modes = SongLoader.SortMode.values();
            int i = (SongLoader.sortMode.ordinal() + 1) % modes.length;
            SongLoader.sortMode = modes[i];
            SongLoader.sort(); shouldRefresh = true;
        }).bounds(54, topY, 40, BTN_SMALL).build();
        addRenderableWidget(btnSort);
        tooltips.put(btnSort, Component.translatable("disc_jockey.tooltip.sort"));

        btnOrder = Button.builder(Component.literal("\u2191"), b -> {
            SongLoader.sortAsc = !SongLoader.sortAsc;
            SongLoader.sort(); shouldRefresh = true;
        }).bounds(98, topY, BTN_SMALL, BTN_SMALL).build();
        addRenderableWidget(btnOrder);
        tooltips.put(btnOrder, Component.translatable("disc_jockey.tooltip.order"));

        btnReload = Button.builder(Component.literal("\uD83D\uDD04"), b -> {
            SongLoader.loadSongs(); isLoading = true; shouldRefresh = true;
        }).bounds(width - 80, topY, BTN_SMALL, BTN_SMALL).build();
        addRenderableWidget(btnReload);
        tooltips.put(btnReload, Component.translatable("disc_jockey.tooltip.reload"));

        btnOpenFolder = Button.builder(Component.literal("\uD83D\uDCC2"), b -> {
            try {
                String path = Main.songsFolder.getAbsolutePath();
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) Runtime.getRuntime().exec(new String[]{"explorer", path});
                else if (os.contains("mac")) Runtime.getRuntime().exec(new String[]{"open", path});
                else Runtime.getRuntime().exec(new String[]{"xdg-open", path});
            } catch (IOException ex) { Main.LOGGER.warn("Cannot open folder", ex); }
        }).bounds(width - 56, topY, BTN_SMALL, BTN_SMALL).build();
        addRenderableWidget(btnOpenFolder);
        tooltips.put(btnOpenFolder, Component.translatable("disc_jockey.tooltip.open_folder"));

        btnSettings = Button.builder(Component.literal("\uD83D\uDD27"), b ->
            minecraft.gui.setScreen(new ConfigScreen(this))
        ).bounds(width - 32, topY, BTN_SMALL, BTN_SMALL).build();
        addRenderableWidget(btnSettings);
        tooltips.put(btnSettings, Component.translatable("disc_jockey.tooltip.settings"));

        int btnY = height - 42;
        int totalW = BTN_WIDE * 5 + BTN_GAP * 4;
        int sx = (width - totalW) / 2;

        btnPlay = Button.builder(TXT_PLAY, b -> togglePlay())
                .bounds(sx, btnY, BTN_WIDE, BTN_SMALL).build();
        addRenderableWidget(btnPlay);
        tooltips.put(btnPlay, Component.translatable("disc_jockey.tooltip.play"));
        sx += BTN_WIDE + BTN_GAP;

        btnPreview = Button.builder(TXT_PREVIEW, b -> togglePreview())
                .bounds(sx, btnY, BTN_WIDE, BTN_SMALL).build();
        addRenderableWidget(btnPreview);
        tooltips.put(btnPreview, Component.translatable("disc_jockey.tooltip.preview"));
        sx += BTN_WIDE + BTN_GAP;

        btnBlocks = Button.builder(TXT_BLOCKS, b -> toggleBlocks())
                .bounds(sx, btnY, BTN_WIDE, BTN_SMALL).build();
        addRenderableWidget(btnBlocks);
        tooltips.put(btnBlocks, Component.translatable("disc_jockey.tooltip.blocks"));
        sx += BTN_WIDE + BTN_GAP;

        btnShuffle = Button.builder(TXT_SHUFFLE, b -> doShuffle())
                .bounds(sx, btnY, BTN_WIDE, BTN_SMALL).build();
        addRenderableWidget(btnShuffle);
        tooltips.put(btnShuffle, Component.translatable("disc_jockey.tooltip.shuffle"));
        sx += BTN_WIDE + BTN_GAP;

        btnLoop = Button.builder(TXT_LOOP_OFF, b ->
            SongPlayer.loopMode = (SongPlayer.loopMode + 1) % 4
        ).bounds(sx, btnY, BTN_WIDE, BTN_SMALL).build();
        addRenderableWidget(btnLoop);
        tooltips.put(btnLoop, Component.translatable("disc_jockey.tooltip.loop"));

        int ctrlY = height - 76;
        int rightX = width - PROGRESS_BAR_PAD - 160;

        btnSpeedDown = Button.builder(Component.literal("-"), b -> {
            Main.SONG_PLAYER.speed = Math.max(0.1f, Main.SONG_PLAYER.speed - 0.25f);
        }).bounds(rightX, ctrlY, 18, 18).build();
        addRenderableWidget(btnSpeedDown);

        btnSpeedDisplay = Button.builder(Component.literal(speedText()), b -> {}).bounds(rightX + 20, ctrlY, 42, 18).build();
        addRenderableWidget(btnSpeedDisplay);

        btnSpeedUp = Button.builder(Component.literal("+"), b -> {
            Main.SONG_PLAYER.speed = Math.min(10.0f, Main.SONG_PLAYER.speed + 0.25f);
        }).bounds(rightX + 64, ctrlY, 18, 18).build();
        addRenderableWidget(btnSpeedUp);

        jumpInput = new EditBox(font, rightX + 90, ctrlY, 66, 18, TXT_JUMP);
        jumpInput.setMaxLength(7);
        jumpInput.setResponder(text -> lastJumpText = text);
        addRenderableWidget(jumpInput);

        searchBar = new EditBox(font, width / 2 - 100, height - 19, 200, 16, TXT_SEARCH);
        searchBar.setResponder(q -> {
            String nq = q.toLowerCase().replaceAll("\\s", "");
            if (!this.query.equals(nq)) { this.query = nq; shouldRefresh = true; }
        });
        addRenderableWidget(searchBar);

        songList.setBounds(6, 30, width - 18, height - 135);
    }

    // === Actions ===

    private void playRow(SongListWidget.SongRow row) {
        if (row == null) return;
        // Check creative mode
        if (minecraft != null && minecraft.gameMode != null) {
            GameType mode = minecraft.gameMode.getPlayerMode();
            if (mode != null && !mode.isSurvival()) {
                minecraft.gui.setScreen(new ConfirmScreen(ok -> {
                    if (ok) minecraft.gui.setScreen(this);
                    else minecraft.gui.setScreen(this);
                }, TXT_CREATIVE, Component.literal("")));
                return;
            }
        }
        int idx = cachedSongs.indexOf(row.song);
        if (idx < 0) idx = 0;
        Main.SONG_PLAYER.startPlaylist(new ArrayList<>(cachedSongs));
        Main.SONG_PLAYER.currentPlaylistIndex = idx;
        Main.SONG_PLAYER.start(row.song);
        Main.SONG_PLAYER.speed = Main.config.playback.defaultSpeed;
        if (Main.config.hud.autoCloseBlocksOnPlay) {
            BlocksOverlay.activeSong = null;
            BlocksOverlay.forceShow = false;
        }
        minecraft.gui.setScreen(null);
    }

    private void togglePlay() {
        if (Main.SONG_PLAYER.running) { Main.SONG_PLAYER.stop(); return; }
        playRow(getSelectedRow());
    }

    private void togglePreview() {
        if (Main.PREVIEWER.running) { Main.PREVIEWER.stop(); return; }
        SongListWidget.SongRow row = getSelectedRow();
        if (row != null) Main.PREVIEWER.start(row.song);
    }

    private void toggleBlocks() {
        if (BlocksOverlay.activeSong != null || BlocksOverlay.forceShow) {
            BlocksOverlay.activeSong = null;
            BlocksOverlay.forceShow = false;
            return;
        }
        SongListWidget.SongRow row = getSelectedRow();
        if (row != null) { BlocksOverlay.activeSong = row.song; minecraft.gui.setScreen(null); }
    }

    private void doShuffle() {
        if (cachedSongs.isEmpty()) return;
        // Check creative mode
        if (minecraft != null && minecraft.gameMode != null) {
            GameType mode = minecraft.gameMode.getPlayerMode();
            if (mode != null && !mode.isSurvival()) {
                minecraft.gui.setScreen(new ConfirmScreen(ok -> {
                    if (ok) minecraft.gui.setScreen(this);
                    else minecraft.gui.setScreen(this);
                }, TXT_CREATIVE, Component.literal("")));
                return;
            }
        }
        List<Song> shuffled = new ArrayList<>(cachedSongs);
        Collections.shuffle(shuffled);
        Main.SONG_PLAYER.startPlaylist(shuffled);
        Main.SONG_PLAYER.currentPlaylistIndex = 0;
        Main.SONG_PLAYER.start(shuffled.get(0));
        minecraft.gui.setScreen(null);
    }

    private SongListWidget.SongRow getSelectedRow() {
        return songList.selected instanceof SongListWidget.SongRow ? (SongListWidget.SongRow) songList.selected : null;
    }

    // === Helpers ===

    private String speedText() { return String.format(Locale.ROOT, "%.2fx", Main.SONG_PLAYER.speed); }

    private String timeText(double totalSeconds) {
        int min = (int) (totalSeconds / 60);
        int sec = (int) (totalSeconds % 60);
        return String.format("%d:%02d", min, sec);
    }

    private void seekFromX(int mouseX) {
        if (!Main.SONG_PLAYER.running || Main.SONG_PLAYER.song == null) return;
        int barLeft = PROGRESS_BAR_PAD;
        int barRight = width - PROGRESS_BAR_PAD;
        double fraction = Mth.clamp((mouseX - barLeft) / (double)(barRight - barLeft), 0.0, 1.0);
        Main.SONG_PLAYER.seekToTick((int)(fraction * Main.SONG_PLAYER.getTotalTicks()));
    }

    private void applyJumpInput() {
        if (lastJumpText.isEmpty() || !Main.SONG_PLAYER.running || Main.SONG_PLAYER.song == null) return;
        try {
            double seconds;
            String t = lastJumpText.trim();
            if (t.contains(":")) {
                String[] parts = t.split(":");
                seconds = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            } else {
                seconds = Double.parseDouble(t);
            }
            double totalSec = Main.SONG_PLAYER.song.getLengthInSeconds();
            double fraction = Mth.clamp(seconds / totalSec, 0.0, 1.0);
            Main.SONG_PLAYER.seekToTick((int)(fraction * Main.SONG_PLAYER.getTotalTicks()));
        } catch (Exception ignored) {}
    }

    // === Tick ===

    @Override
    public void tick() {
        if (!lastJumpText.isEmpty() && !lastJumpText.equals(lastAppliedText)) {
            lastAppliedText = lastJumpText;
            applyJumpInput();
        }

        btnPlay.setMessage(Main.SONG_PLAYER.running ? TXT_PLAY_STOP : TXT_PLAY);
        btnPreview.setMessage(Main.PREVIEWER.running ? TXT_PREVIEW_STOP : TXT_PREVIEW);
        btnBlocks.setMessage(BlocksOverlay.activeSong != null ? TXT_BLOCKS_OFF : TXT_BLOCKS);
        btnBack.visible = !currentDir.isEmpty() || showFavorites;
        btnLoop.setMessage(switch (SongPlayer.loopMode) {
            case 0 -> TXT_LOOP_OFF;
            case 1 -> TXT_LOOP_LIST;
            case 2 -> TXT_LOOP_ONE;
            default -> TXT_LOOP_SHUFFLE;
        });
        btnSpeedDisplay.setMessage(Component.literal(speedText()));

        btnSort.setMessage(Component.literal(switch (SongLoader.sortMode) {
            case NAME         -> TXT_SORT_NAME.getString();
            case CREATION_TIME -> TXT_SORT_CREATED.getString();
            case LAST_PLAYED  -> TXT_SORT_PLAYED.getString();
        }));
        btnOrder.setMessage(Component.literal(SongLoader.sortAsc ? "\u2191" : "\u2193"));

        if (isLoading && !SongLoader.loadingSongs) { isLoading = false; shouldRefresh = true; }

        if (shouldRefresh) {
            shouldRefresh = false;
            songList.clear();
            List<Song> matched = new ArrayList<>();
            if (!showFavorites) {
                for (String dir : SongLoader.getSubdirs(currentDir)) {
                    String dn = currentDir.isEmpty() ? dir : dir.substring(currentDir.length() + 1);
                    if (dn.contains(File.separator)) continue;
                    songList.entries.add(new SongListWidget.FolderRow(dn, dir));
                }
                boolean emptyQ = query.isEmpty();
                for (Song s : SongLoader.getSongsInDir(currentDir)) {
                    if (emptyQ || s.searchableFileName.contains(query) || s.searchableName.contains(query)) {
                        s.songRow.favorite = Main.config.favorites.contains(s.filePath);
                        songList.entries.add(s.songRow);
                        matched.add(s);
                    }
                }
            } else {
                boolean emptyQ = query.isEmpty();
                for (Song s : SongLoader.SONGS) {
                    if (Main.config.favorites.contains(s.filePath)
                            && (emptyQ || s.searchableFileName.contains(query) || s.searchableName.contains(query))) {
                        s.songRow.favorite = true;
                        songList.entries.add(s.songRow);
                        matched.add(s);
                    }
                }
            }
            cachedSongs = matched;
        }

        if (songList.selected instanceof SongListWidget.FolderRow f && f.clicked) {
            f.clicked = false;
            if (f.isFavorites) { showFavorites = true; currentDir = ""; }
            else { currentDir = f.path; showFavorites = false; }
            query = ""; songList.selected = null; shouldRefresh = true;
        }
    }

    // === Rendering ===

    @Override
    public void extractRenderState(GuiGraphicsExtractor ex, int mx, int my, float delta) {
        ex.fill(0, 0, width, height, ARGB.color(200, 10, 10, 20));

        String title;
        if (showFavorites) title = "\u2605 " + TXT_FAV.getString();
        else if (currentDir.isEmpty()) title = TXT_ALL.getString();
        else title = "\uD83D\uDCC1 " + currentDir;
        ex.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);

        ex.text(font, cachedSongs.size() + " " + TXT_SONGS.getString(), 6, height - 48, 0xFFAAAAAA, false);

        renderProgressBar(ex);
        songList.render(ex, mx, my);
        super.extractRenderState(ex, mx, my, delta);
        renderTooltips(ex, mx, my);
    }

    private void renderProgressBar(GuiGraphicsExtractor ex) {
        boolean playing = Main.SONG_PLAYER.running && Main.SONG_PLAYER.song != null;
        int barY = height - PROGRESS_BAR_Y_OFFSET;
        int barLeft = PROGRESS_BAR_PAD;
        int barRight = width - PROGRESS_BAR_PAD;
        int barW = barRight - barLeft;

        ex.fill(barLeft, barY, barRight, barY + PROGRESS_BAR_H, ARGB.color(150, 40, 40, 40));

        if (playing) {
            double totalTicks = Main.SONG_PLAYER.getTotalTicks();
            double currentTicks = Main.SONG_PLAYER.getCurrentTick();
            double fraction = totalTicks > 0 ? Mth.clamp(currentTicks / totalTicks, 0.0, 1.0) : 0.0;
            int filledW = (int)(barW * fraction);
            ex.fill(barLeft, barY, barLeft + filledW, barY + PROGRESS_BAR_H, ARGB.color(255, 60, 140, 220));
            int knobX = barLeft + filledW - 3;
            ex.fill(knobX, barY - 2, knobX + 6, barY + PROGRESS_BAR_H + 2, ARGB.color(255, 255, 255, 255));

            double curSec = Main.SONG_PLAYER.song.ticksToMilliseconds(currentTicks) / 1000.0;
            double totalSec = Main.SONG_PLAYER.song.getLengthInSeconds();
            String timeStr = timeText(curSec) + " / " + timeText(totalSec);
            ex.text(font, timeStr, barLeft + (barW - font.width(timeStr)) / 2, barY - 12, 0xFFCCCCCC, false);
        } else {
            String idle = "--:-- / --:--";
            ex.text(font, idle, barLeft + (barW - font.width(idle)) / 2, barY - 12, 0xFF666666, false);
        }
    }

    private void renderTooltips(GuiGraphicsExtractor ex, int mx, int my) {
        for (var entry : tooltips.entrySet()) {
            Button btn = entry.getKey();
            if (!btn.visible || !btn.active) continue;
            if (mx < btn.getX() || mx > btn.getX() + btn.getWidth()) continue;
            if (my < btn.getY() || my > btn.getY() + btn.getHeight()) continue;

            String tip = entry.getValue().getString();
            if (tip.isEmpty()) continue;

            int tipW = font.width(tip) + 8;
            int tipH = font.lineHeight + 6;
            int tipX = Math.min(mx + 10, width - tipW - 4);
            int tipY = Math.max(btn.getY() - tipH - 4, 4);

            ex.fill(tipX, tipY, tipX + tipW, tipY + tipH, ARGB.color(220, 20, 20, 30));
            ex.fill(tipX, tipY, tipX + tipW, tipY + 1, ARGB.color(80, 255, 255, 255));
            ex.fill(tipX, tipY + tipH - 1, tipX + tipW, tipY + tipH, ARGB.color(80, 255, 255, 255));
            ex.text(font, tip, tipX + 4, tipY + 3, 0xFFFFFFCC, false);
        }
    }

    // === Input ===

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        int mx = (int) event.x(), my = (int) event.y();

        // Progress bar: one-shot seek on click (no drag to avoid stuck state)
        int barY = height - PROGRESS_BAR_Y_OFFSET;
        int barLeft = PROGRESS_BAR_PAD;
        int barRight = width - PROGRESS_BAR_PAD;
        if (event.button() == 0 && my >= barY - 4 && my <= barY + PROGRESS_BAR_H + 4
                && mx >= barLeft - 4 && mx <= barRight + 4) {
            seekFromX(mx);
            return true;
        }

        SongListWidget.SongListEntry entry = songList.entryAt(mx, my);
        if (entry != null && event.button() == 0) {
            songList.selected = entry;
            if (entry instanceof SongListWidget.FolderRow f) {
                f.clicked = true;
            } else if (entry instanceof SongListWidget.SongRow row) {
                if (mx >= songList.x && mx <= songList.x + 16) {
                    if (Main.config.favorites.contains(row.song.filePath))
                        Main.config.favorites.remove(row.song.filePath);
                    else Main.config.favorites.add(row.song.filePath);
                    row.favorite = Main.config.favorites.contains(row.song.filePath);
                }
                if (isDoubleClick) playRow(row);
            }
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (mx >= songList.x && mx <= songList.x + songList.width
                && my >= songList.y && my <= songList.y + songList.height) {
            songList.scroll -= vertical * 20;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        String names = paths.stream().map(Path::getFileName).map(Path::toString)
                .collect(java.util.stream.Collectors.joining(", "));
        if (names.length() > 300) names = names.substring(0, 300) + "...";
        minecraft.gui.setScreen(new ConfirmScreen(ok -> {
            if (ok) {
                for (Path p : paths) {
                    try {
                        File f = p.toFile();
                        if (SongLoader.SONGS.stream().anyMatch(s -> s.filePath.equalsIgnoreCase(f.getName())))
                            continue;
                        Song s = SongLoader.loadSong(f, currentDir);
                        if (s != null) {
                            Files.copy(p, new File(Main.songsFolder, s.filePath).toPath());
                            SongLoader.SONGS.add(s);
                        }
                    } catch (IOException e) { Main.LOGGER.warn("Drop failed", e); }
                }
                SongLoader.sort();
            }
            minecraft.gui.setScreen(this);
        }, TXT_DROP, Component.literal(names)));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        super.onClose();
        new Thread(() -> Main.configHolder.save()).start();
    }
}
