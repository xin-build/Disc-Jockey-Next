package semmiedev.disc_jockey.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import semmiedev.disc_jockey.Config;
import semmiedev.disc_jockey.Main;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int LEFT = 12;
    private static final int PAD = 4;
    private static final Component TITLE = Component.translatable("text.autoconfig.disc_jockey.title");

    private final Screen parent;
    private final List<EditBox> valueEdits = new ArrayList<>();
    private double scrollY;
    private static final int HEADER_H = 32;
    private static final int BUTTON_AREA = 44;

    private static final String[] LABELS = {
        "text.autoconfig.disc_jockey.option.hideWarning",
        "text.autoconfig.disc_jockey.option.disableAsyncPlayback",
        "text.autoconfig.disc_jockey.option.sound.omnidirectionalNoteBlockSounds",
        "text.autoconfig.disc_jockey.option.server.delayPlaybackStartBySecs",
        "text.autoconfig.disc_jockey.option.hud.hudX",
        "text.autoconfig.disc_jockey.option.hud.hudY",
        "text.autoconfig.disc_jockey.option.hud.hudWidth",
        "text.autoconfig.disc_jockey.option.playback.defaultSpeed",
        "disc_jockey.screen.blocks.auto_close",
    };

    // Guard to prevent setValue → responder → setValue infinite recursion
    private boolean editing;

    public ConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    private int rowCount() { return LABELS.length; }
    private int totalContentH() { return rowCount() * (ROW_H + PAD) + 16; }
    private int availableH() { return height - HEADER_H - BUTTON_AREA; }
    private boolean needsScroll() { return totalContentH() > availableH(); }
    private int maxScroll() { return Math.max(0, totalContentH() - availableH()); }

    @Override
    protected void init() {
        valueEdits.clear();
        clampScroll();
        Config cfg = Main.config;
        int rightX = width - 112;
        int yBase = HEADER_H - (int)scrollY;

        int y = yBase;
        addToggle(() -> cfg.hideWarning, v -> cfg.hideWarning = v, y, rightX); y += ROW_H + PAD;
        addToggle(() -> cfg.disableAsyncPlayback, v -> cfg.disableAsyncPlayback = v, y, rightX); y += ROW_H + PAD;
        addToggle(() -> cfg.sound.omnidirectionalNoteBlockSounds, v -> cfg.sound.omnidirectionalNoteBlockSounds = v, y, rightX); y += ROW_H + PAD;
        addFloatEdit(() -> cfg.server.delayPlaybackStartBySecs, v -> cfg.server.delayPlaybackStartBySecs = v, 0f, 10f, 0.5f, y, rightX); y += ROW_H + PAD;
        addIntEdit(() -> cfg.hud.hudX, v -> cfg.hud.hudX = v, 0, 3840, 10, y, rightX); y += ROW_H + PAD;
        addIntEdit(() -> cfg.hud.hudY, v -> cfg.hud.hudY = v, 0, 2160, 10, y, rightX); y += ROW_H + PAD;
        addIntEdit(() -> cfg.hud.hudWidth, v -> cfg.hud.hudWidth = v, 50, 400, 10, y, rightX); y += ROW_H + PAD;
        addFloatEdit(() -> cfg.playback.defaultSpeed, v -> cfg.playback.defaultSpeed = v, 0.1f, 5.0f, 0.1f, y, rightX); y += ROW_H + PAD;
        addToggle(() -> cfg.hud.autoCloseBlocksOnPlay, v -> cfg.hud.autoCloseBlocksOnPlay = v, y, rightX); y += ROW_H + PAD;

        // Bottom buttons: placed below last row or at screen bottom, whichever is lower
        int btnY = Math.max(height - BUTTON_AREA + 4, y + 8);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 110, btnY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("disc_jockey.screen.config.save"), b -> {
            new Thread(() -> Main.configHolder.save()).start();
            if (minecraft != null && minecraft.player != null)
                minecraft.player.sendSystemMessage(Component.translatable("disc_jockey.screen.config.saved"));
        }).bounds(width / 2 - 35, btnY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("disc_jockey.screen.config.reset"), b -> {
            cfg.hideWarning = false;
            cfg.disableAsyncPlayback = false;
            cfg.sound.omnidirectionalNoteBlockSounds = true;
            cfg.server.delayPlaybackStartBySecs = 0f;
            cfg.hud.hudX = 2; cfg.hud.hudY = 2; cfg.hud.hudWidth = 180;
            cfg.playback.defaultSpeed = 1.0f;
            cfg.hud.autoCloseBlocksOnPlay = true;
            cfg.hud.blockHighlight = false;
            new Thread(() -> Main.configHolder.save()).start();
            scrollY = 0;
            clearWidgets();
            init();
        }).bounds(width / 2 + 40, btnY, 70, 20).build());
    }

    private void clampScroll() {
        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));
    }

    // --- Toggle ---
    private void addToggle(Supplier<Boolean> getter, Consumer<Boolean> setter, int y, int x) {
        boolean cur = getter.get();
        Button btn = Button.builder(Component.literal(cur ? "\u2714 ON" : "\u2718 OFF"), b -> {
            setter.accept(!getter.get());
            b.setMessage(Component.literal(getter.get() ? "\u2714 ON" : "\u2718 OFF"));
        }).bounds(x, y, 100, 20).build();
        addRenderableWidget(btn);
    }

    // --- Float edit ---
    private void addFloatEdit(Supplier<Float> getter, Consumer<Float> setter, float min, float max, float step, int y, int x) {
        EditBox display = new EditBox(font, x + 22, y, 56, 20, Component.empty());
        display.setValue(fmt(getter.get()));
        display.setResponder(text -> {
            if (editing) return;
            try {
                float nv = Float.parseFloat(text);
                nv = Math.max(min, Math.min(max, nv));
                setter.accept(nv);
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(display);
        valueEdits.add(display);

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            float nv = Math.max(min, getter.get() - step);
            setter.accept(nv);
            editing = true;
            display.setValue(fmt(nv));
            editing = false;
        }).bounds(x, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            float nv = Math.min(max, getter.get() + step);
            setter.accept(nv);
            editing = true;
            display.setValue(fmt(nv));
            editing = false;
        }).bounds(x + 80, y, 20, 20).build());
    }

    // --- Int edit ---
    private void addIntEdit(Supplier<Integer> getter, Consumer<Integer> setter, int min, int max, int step, int y, int x) {
        EditBox display = new EditBox(font, x + 22, y, 56, 20, Component.empty());
        display.setValue(String.valueOf(getter.get()));
        display.setResponder(text -> {
            if (editing) return;
            try {
                int nv = Integer.parseInt(text);
                nv = Math.max(min, Math.min(max, nv));
                setter.accept(nv);
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(display);
        valueEdits.add(display);

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            int nv = Math.max(min, getter.get() - step);
            setter.accept(nv);
            editing = true;
            display.setValue(String.valueOf(nv));
            editing = false;
        }).bounds(x, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            int nv = Math.min(max, getter.get() + step);
            setter.accept(nv);
            editing = true;
            display.setValue(String.valueOf(nv));
            editing = false;
        }).bounds(x + 80, y, 20, 20).build());
    }

    private static String fmt(float f) { return String.format(Locale.ROOT, "%.1f", f); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ex, int mx, int my, float delta) {
        ex.fill(0, 0, width, height, ARGB.color(200, 10, 10, 20));
        ex.centeredText(font, TITLE, width / 2, Math.min(14, height - 4), 0xFFFFFFFF);

        // Labels: rendered with scroll offset
        int yStart = HEADER_H + 6 - (int)scrollY;
        int labelW = width / 2 - 20;
        for (int i = 0; i < LABELS.length; i++) {
            int ly = yStart + i * (ROW_H + PAD);
            // Clip to visible area
            if (ly + ROW_H < HEADER_H || ly > height) continue;
            String s = Component.translatable(LABELS[i]).getString();
            if (font.width(s) > labelW) {
                while (!s.isEmpty() && font.width(s + "...") > labelW)
                    s = s.substring(0, s.length() - 1);
                s += "...";
            }
            ex.text(font, s, LEFT, ly + 4, 0xFFCCCCCC, false);
        }

        super.extractRenderState(ex, mx, my, delta);

        // Scrollbar
        if (needsScroll()) {
            int sbW = 6;
            int sbX = width - sbW - 2;
            int trackH = availableH() - 8;
            int sbY = HEADER_H + 4;
            ex.fill(sbX, sbY, sbX + sbW, sbY + trackH, ARGB.color(60, 60, 60, 60));
            int thumbH = Math.max(20, (int)((float)availableH() / totalContentH() * trackH));
            int thumbY = sbY + (int)(scrollY / maxScroll() * (trackH - thumbH));
            ex.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, ARGB.color(150, 150, 150, 150));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (needsScroll()) {
            scrollY = Math.max(0, Math.min(scrollY - vertical * 20, maxScroll()));
            if (vertical != 0) rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void rebuild() {
        // Rebuild widgets with new scroll offset, preserving EditBox focus
        String focusedVal = null;
        int focusedIdx = -1;
        for (int i = 0; i < valueEdits.size(); i++) {
            if (valueEdits.get(i).isFocused()) {
                focusedVal = valueEdits.get(i).getValue();
                focusedIdx = i;
                break;
            }
        }
        clearWidgets();
        init();
        if (focusedIdx >= 0 && focusedIdx < valueEdits.size()) {
            valueEdits.get(focusedIdx).setFocused(true);
        }
    }

    @Override public void onClose() {
        new Thread(() -> Main.configHolder.save()).start();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
