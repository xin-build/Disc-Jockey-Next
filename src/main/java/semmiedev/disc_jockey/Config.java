package semmiedev.disc_jockey;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;

@me.shedaniel.autoconfig.annotation.Config(name = Main.MOD_ID)
@me.shedaniel.autoconfig.annotation.Config.Gui.Background("textures/block/note_block.png")
@SuppressWarnings("unused")
public class Config implements ConfigData {
    // Warning
    @ConfigEntry.Gui.Tooltip(count = 1)
    public boolean hideWarning;

    // Async playback
    @ConfigEntry.Gui.Tooltip(count = 2) public boolean disableAsyncPlayback;

    // Sound
    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public SoundSettings sound = new SoundSettings();

    public static class SoundSettings {
        @ConfigEntry.Gui.Tooltip(count = 2) public boolean omnidirectionalNoteBlockSounds = true;
    }

    // Server
    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public ServerSettings server = new ServerSettings();

    public static class ServerSettings {
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        @ConfigEntry.Gui.Tooltip(count = 4)
        public ExpectedServerVersion expectedServerVersion = ExpectedServerVersion.All;

        @ConfigEntry.Gui.Tooltip(count = 1)
        public float delayPlaybackStartBySecs = 0.0f;
    }

    // HUD (Blocks Overlay)
    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public HudSettings hud = new HudSettings();

    public static class HudSettings {
        @ConfigEntry.BoundedDiscrete(min = 0, max = 3840)
        @ConfigEntry.Gui.Tooltip(count = 1)
        public int hudX = 2;

        @ConfigEntry.BoundedDiscrete(min = 0, max = 2160)
        @ConfigEntry.Gui.Tooltip(count = 1)
        public int hudY = 2;

        @ConfigEntry.BoundedDiscrete(min = 50, max = 400)
        @ConfigEntry.Gui.Tooltip(count = 1)
        public int hudWidth = 180;

        public boolean blockHighlight = false;
        public boolean autoCloseBlocksOnPlay = true;
    }

    // Playback
    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public PlaybackSettings playback = new PlaybackSettings();

    public static class PlaybackSettings {
        @ConfigEntry.Gui.Tooltip(count = 1)
        public float defaultSpeed = 1.0f;
    }

    public enum ExpectedServerVersion {
        All,
        v1_20_4_Or_Earlier,
        v1_20_5_Or_Later;

        @Override
        public String toString() {
            if(this == All) {
                return "All (universal)";
            }else if(this == v1_20_4_Or_Earlier) {
                return "\u22641.20.4";
            }else if (this == v1_20_5_Or_Later) {
                return "\u22651.20.5";
            }else {
                return super.toString();
            }
        }
    }

    // Favorites (hidden from GUI, managed by code)
    @ConfigEntry.Gui.Excluded
    public ArrayList<String> favorites = new ArrayList<>();
}
