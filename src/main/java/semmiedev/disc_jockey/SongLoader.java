package semmiedev.disc_jockey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import semmiedev.disc_jockey.gui.SongListWidget;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;

public class SongLoader {
    public static final ArrayList<Song> SONGS = new ArrayList<>();
    public static final ArrayList<String> SONG_SUGGESTIONS = new ArrayList<>();
    public static final LinkedHashSet<String> DIRECTORIES = new LinkedHashSet<>(); // relative paths of directories containing .nbs files
    public static volatile boolean loadingSongs;
    public static volatile boolean showToast;

    public static void loadSongs() {
        if (loadingSongs) return;
        new Thread(() -> {
            loadingSongs = true;
            try {
                SONGS.clear();
                SONG_SUGGESTIONS.clear();
                DIRECTORIES.clear();
                SONG_SUGGESTIONS.add("Songs are loading, please wait");
                Main.LOGGER.info("Looking for .nbs files in: {}", Main.songsFolder.getAbsolutePath());
                scanDirectory(Main.songsFolder, "");
                Main.LOGGER.info("Loaded {} songs, {} directories", SONGS.size(), DIRECTORIES.size());
                for (Song song : SONGS) SONG_SUGGESTIONS.add(song.displayName);
                Main.config.favorites.removeIf(favorite -> SongLoader.SONGS.stream().map(song -> song.filePath).noneMatch(favorite::equals));

                sort();
                if (showToast && Minecraft.getInstance().font != null) SystemToast.add(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, Main.NAME, Component.translatable(Main.MOD_ID+".loading_done"));
                showToast = true;
            } finally {
                loadingSongs = false;
            }
        }).start();
    }

    private static void scanDirectory(File dir, String relativePath) {
        if (relativePath != null && !relativePath.isEmpty() && !DIRECTORIES.contains(relativePath)) {
            // Only add directory if it contains .nbs files (checked after scanning)
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        boolean hasNbs = false;
        for (File file : files) {
            if (file.isDirectory()) {
                String subPath = relativePath.isEmpty() ? file.getName() : relativePath + File.separator + file.getName();
                scanDirectory(file, subPath);
            } else if (file.getName().toLowerCase().endsWith(".nbs")) {
                hasNbs = true;
                Song song = null;
                try {
                    song = loadSong(file, relativePath);
                } catch (Exception exception) {
                    Main.LOGGER.error("Unable to read or parse song {}", file.getName(), exception);
                }
                if (song != null) SONGS.add(song);
            }
        }

        if (hasNbs && !relativePath.isEmpty()) {
            DIRECTORIES.add(relativePath);
        }
    }

    public static Song loadSong(File file, String relativePath) throws IOException {
        if (file.isFile()) {
            BinaryReader reader = new BinaryReader(Files.newInputStream(file.toPath()));
            Song song = new Song();

            song.fileName = file.getName().replaceAll("[\\n\\r]", "");
            song.filePath = relativePath.isEmpty() ? song.fileName : relativePath + File.separator + song.fileName;
            song.creationTime = file.lastModified();

            song.length = reader.readShort();

            boolean newFormat = song.length == 0;
            if (newFormat) {
                song.formatVersion = reader.readByte();
                song.vanillaInstrumentCount = reader.readByte();
                if (song.formatVersion >= 3) {
                    song.length = reader.readShort();
                }
            }

            song.height = reader.readShort();
            song.name = reader.readString().replaceAll("[\\n\\r]", "");
            song.author = reader.readString().replaceAll("[\\n\\r]", "");
            song.originalAuthor = reader.readString().replaceAll("[\\n\\r]", "");
            song.description = reader.readString().replaceAll("[\\n\\r]", "");
            song.tempo = reader.readShort();
            song.autoSaving = reader.readByte();
            song.autoSavingDuration = reader.readByte();
            song.timeSignature = reader.readByte();
            song.minutesSpent = reader.readInt();
            song.leftClicks = reader.readInt();
            song.rightClicks = reader.readInt();
            song.blocksAdded = reader.readInt();
            song.blocksRemoved = reader.readInt();
            song.importFileName = reader.readString().replaceAll("[\\n\\r]", "");

            if (newFormat && song.formatVersion >= 4) {
                song.loop = reader.readByte();
                song.maxLoopCount = reader.readByte();
                song.loopStartTick = reader.readShort();
            }

            // Display name: song name + file name (if different)
            String cleanName = song.name.replaceAll("\\s", "");
            if (cleanName.isEmpty()) {
                song.displayName = song.fileName;
            } else if (!song.name.equalsIgnoreCase(song.fileName.replace(".nbs", ""))) {
                song.displayName = song.name + " (" + song.fileName + ")";
            } else {
                song.displayName = song.name;
            }
            song.songRow = new SongListWidget.SongRow(song);
            song.songRow.favorite = Main.config.favorites.contains(song.filePath);
            song.searchableFileName = song.fileName.toLowerCase().replaceAll("\\s", "");
            song.searchableName = song.name.toLowerCase().replaceAll("\\s", "");

            short tick = -1;
            short jumps;
            java.util.ArrayList<Long> noteList = new java.util.ArrayList<>();
            while ((jumps = reader.readShort()) != 0) {
                tick += jumps;
                short layer = -1;
                while ((jumps = reader.readShort()) != 0) {
                    layer += jumps;

                    byte instrumentId = reader.readByte();
                    byte noteId = (byte)(reader.readByte() - 33);

                    if (newFormat && song.formatVersion >= 4) {
                        reader.readByte(); // Velocity
                        reader.readByte(); // Panning
                        reader.readShort(); // Pitch
                    }

                    if (noteId < 0) {
                        noteId = 0;
                    } else if (noteId > 24) {
                        noteId = 24;
                    }

                    if (instrumentId < 0 || instrumentId >= Note.INSTRUMENTS.length) {
                        instrumentId = 0;
                    }
                    Note note = new Note(Note.INSTRUMENTS[instrumentId], noteId);
                    if (!song.uniqueNotes.contains(note)) song.uniqueNotes.add(note);

                    noteList.add(tick | layer << Note.LAYER_SHIFT | (long)instrumentId << Note.INSTRUMENT_SHIFT | (long)noteId << Note.NOTE_SHIFT);
                }
            }
            song.notes = new long[noteList.size()];
            for (int i = 0; i < noteList.size(); i++) {
                song.notes[i] = noteList.get(i);
            }

            return song;
        }
        return null;
    }

    /** Sort modes for the song list */
    public enum SortMode { NAME, CREATION_TIME, LAST_PLAYED }

    public static SortMode sortMode = SortMode.NAME;
    public static boolean sortAsc = true;

    private static Comparator<Song> sortComparator() {
        Comparator<Song> cmp = switch (sortMode) {
            case NAME -> Comparator.comparing(s -> s.displayName);
            case CREATION_TIME -> Comparator.comparingLong(s -> s.creationTime);
            case LAST_PLAYED -> Comparator.comparingLong(s -> -s.lastPlayedTime);
        };
        return sortAsc ? cmp : cmp.reversed();
    }

    public static void sort() {
        SONGS.sort(sortComparator());
    }

    /** Get songs that belong to a specific directory path (empty string = root) */
    public static ArrayList<Song> getSongsInDir(String dirPath) {
        ArrayList<Song> result = new ArrayList<>();
        for (Song song : SONGS) {
            if (dirPath.isEmpty()) {
                // Root: songs with no subdirectory
                if (!song.filePath.contains(File.separator)) {
                    result.add(song);
                }
            } else {
                // Specific directory
                String normalizedDir = dirPath + File.separator;
                if (song.filePath.startsWith(normalizedDir) && !song.filePath.substring(normalizedDir.length()).contains(File.separator)) {
                    result.add(song);
                }
            }
        }
        result.sort(sortComparator());
        return result;
    }

    /** Get immediate subdirectories of a given path */
    public static LinkedHashSet<String> getSubdirs(String parentPath) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String prefix = parentPath.isEmpty() ? "" : parentPath + File.separator;
        for (String dir : DIRECTORIES) {
            if (!parentPath.isEmpty() && !dir.startsWith(prefix)) continue;
            if (parentPath.isEmpty()) {
                // Root level: get immediate children
                int sep = dir.indexOf(File.separator);
                if (sep >= 0) result.add(dir.substring(0, sep));
                else result.add(dir);
            } else {
                // Sub-level: get children of parentPath
                String rest = dir.substring(prefix.length());
                int sep = rest.indexOf(File.separator);
                if (sep >= 0) result.add(prefix + rest.substring(0, sep));
                else result.add(dir);
            }
        }
        return result;
    }
}
