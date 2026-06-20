package semmiedev.disc_jockey;

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import semmiedev.disc_jockey.gui.SongListWidget;

import java.util.ArrayList;

public class Song {
    public String fileName;
    public String filePath; // full path relative to songs folder, e.g. "subdir/song.nbs"
    public short length;
    public byte formatVersion;
    public byte vanillaInstrumentCount;
    public short height;
    public String name;
    public String author;
    public String originalAuthor;
    public String description;
    public short tempo;
    public byte autoSaving;
    public byte autoSavingDuration;
    public byte timeSignature;
    public int minutesSpent;
    public int leftClicks;
    public int rightClicks;
    public int blocksAdded;
    public int blocksRemoved;
    public String importFileName;
    public byte loop;
    public byte maxLoopCount;
    public short loopStartTick;

    public String displayName;
    public SongListWidget.SongRow songRow;
    public String searchableFileName;
    public String searchableName;

    /** File last-modified timestamp (used for "by creation time" sort) */
    public long creationTime;
    /** System.currentTimeMillis() when last played */
    public long lastPlayedTime;

    public long[] notes = new long[0];
    public ArrayList<Note> uniqueNotes = new ArrayList<>();

    public double millisecondsToTicks(long milliseconds) {
        double songSpeed = (tempo / 100.0) / 20.0;
        double oneMsTo20TickFraction = 1.0 / 50.0;
        return milliseconds * oneMsTo20TickFraction * songSpeed;
    }

    public double ticksToMilliseconds(double ticks) {
        double songSpeed = (tempo / 100.0) / 20.0;
        double oneMsTo20TickFraction = 1.0 / 50.0;
        return ticks / oneMsTo20TickFraction / songSpeed;
    }

    public double getLengthInSeconds() {
        return ticksToMilliseconds(length) / 1000.0;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
