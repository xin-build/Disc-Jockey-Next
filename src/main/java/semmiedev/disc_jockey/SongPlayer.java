package semmiedev.disc_jockey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.function.Consumer;

public class SongPlayer implements Consumer<ClientLevel> {
    private static boolean warned;
    public boolean running;
    public Song song;

    private int index;
    private double tick;
    private HashMap<NoteBlockInstrument, HashMap<Byte, BlockPos>> noteBlocks = null;
    public boolean tuned;
    private long lastPlaybackTickAt = -1L;

    // Loop mode: 0 = off, 1 = loop playlist, 2 = loop single
    public static int loopMode = 0;

    // Playlist support
    public java.util.List<Song> playlist;
    public int currentPlaylistIndex = 0;
    public boolean shuffleMode = false;

    // Packet rate limiting
    private long last100MsSpanAt = -1L;
    private int last100MsSpanEstimatedPackets = 0;
    private final int last100MsReducePacketsAfter = 30;
    private final int last100MsStopPacketsAfter = 45;
    private long reducePacketsUntil = -1L;
    private long stopPacketsUntil = -1L;
    private long lastLookSentAt = -1L;
    private long lastSwingSentAt = -1L;

    private long pausePlaybackUntil = -1L;
    private Thread playbackThread = null;
    public long playbackLoopDelay = 5;
    public float speed = 1.0f;

    public boolean didSongReachEnd = false;
    public HashMap<Block, Integer> missingInstrumentBlocks = new HashMap<>();
    public HashMap<NoteBlockInstrument, NoteBlockInstrument> instrumentMap = new HashMap<>();

    private static final int SCAN_RADIUS = 5;

    public SongPlayer() {
        Main.TICK_LISTENERS.add(this);
    }

    public synchronized void startPlaybackThread() {
        if (Main.config.disableAsyncPlayback) {
            playbackThread = null;
            return;
        }
        this.playbackThread = new Thread(() -> {
            Thread ownThread = this.playbackThread;
            while (ownThread == this.playbackThread) {
                try { Thread.sleep(playbackLoopDelay); } catch (Exception ignored) {}
                tickPlayback();
            }
        });
        this.playbackThread.start();
    }

    public synchronized void stopPlaybackThread() {
        this.playbackThread = null;
    }

    public void startPlaylist(java.util.List<Song> list) {
        this.playlist = list;
        this.currentPlaylistIndex = 0;
    }

    public synchronized void start(Song song) {
        Minecraft client = Minecraft.getInstance();
        if (!Main.config.hideWarning && !warned) {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.translatable("disc_jockey.warning")
                    .withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.RED));
            }
            warned = true;
            return;
        }
        if (running) stop();
        stopPlaybackThread();
        this.song = song;
        this.speed = Main.config.playback.defaultSpeed;
        index = 0;
        tick = 0;
        missingInstrumentBlocks.clear();
        didSongReachEnd = false;
        tuned = false;
        noteBlocks = null;
        lastPlaybackTickAt = System.currentTimeMillis();
        last100MsSpanAt = System.currentTimeMillis();
        last100MsSpanEstimatedPackets = 0;
        reducePacketsUntil = -1L;
        stopPacketsUntil = -1L;
        lastLookSentAt = -1L;
        lastSwingSentAt = -1L;
        pausePlaybackUntil = -1L;
        song.lastPlayedTime = System.currentTimeMillis();
        startPlaybackThread();
        running = true;
    }

    public synchronized void stop() {
        stopPlaybackThread();
        running = false;
        index = 0;
        tick = 0;
        noteBlocks = null;
        tuned = false;
        didSongReachEnd = false;
        lastPlaybackTickAt = -1L;
        last100MsSpanAt = -1L;
        last100MsSpanEstimatedPackets = 0;
        reducePacketsUntil = -1L;
        stopPacketsUntil = -1L;
        lastLookSentAt = -1L;
        lastSwingSentAt = -1L;
    }

    private void playNext() {
        if (playlist == null || playlist.isEmpty()) {
            stop();
            return;
        }

        if (loopMode == 2) {
            // Loop single: replay same song
            start(song);
            return;
        }

        if (loopMode == 3) {
            // Shuffle loop: pick a random song from playlist (excluding current if possible)
            if (playlist.size() <= 1) {
                start(song);
                return;
            }
            Song next;
            int tries = 0;
            do {
                next = playlist.get(new java.util.Random().nextInt(playlist.size()));
                tries++;
            } while (next == song && tries < 10);
            currentPlaylistIndex = playlist.indexOf(next);
            start(next);
            return;
        }

        currentPlaylistIndex++;
        if (currentPlaylistIndex >= playlist.size()) {
            if (loopMode == 1) {
                // Loop playlist: go back to start
                currentPlaylistIndex = 0;
            } else {
                stop();
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.translatable(Main.MOD_ID + ".song_done")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                }
                return;
            }
        }

        Song nextSong = playlist.get(currentPlaylistIndex);
        start(nextSong);
    }

    private boolean canInteractWith(LocalPlayer player, BlockPos pos) {
        if (player == null) return false;
        Vec3 eyePos = player.getEyePosition();
        double dist = eyePos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return dist <= (SCAN_RADIUS + 1) * (SCAN_RADIUS + 1);
    }

    public synchronized void tickPlayback() {
        Minecraft client = Minecraft.getInstance();

        if (!running || song == null) {
            lastPlaybackTickAt = -1L;
            return;
        }

        long previousPlaybackTickAt = lastPlaybackTickAt;
        long now = System.currentTimeMillis();
        lastPlaybackTickAt = now;

        // Detect pause/unpause: if more than 500ms has passed since last tick,
        // treat it as a resume from pause and reset time to avoid note explosion.
        long elapsedMs = previousPlaybackTickAt != -1L ? now - previousPlaybackTickAt : 16;
        if (elapsedMs > 500) elapsedMs = 16;

        if (last100MsSpanAt != -1L && now - last100MsSpanAt >= 100) {
            last100MsSpanEstimatedPackets = 0;
            last100MsSpanAt = now;
        } else if (last100MsSpanAt == -1L) {
            last100MsSpanAt = now;
        }

        if (noteBlocks != null && tuned) {
            if (pausePlaybackUntil != -1L && now <= pausePlaybackUntil) return;

            while (running && index < song.notes.length) {
                GameType gameMode = client.gameMode == null ? null : client.gameMode.getPlayerMode();
                if (gameMode == null || !gameMode.isSurvival()) {
                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.translatable(Main.MOD_ID + ".player.invalid_game_mode",
                            gameMode == null ? "unknown" : gameMode.getName()).withStyle(net.minecraft.ChatFormatting.RED));
                    }
                    stop();
                    return;
                }

                long note = song.notes[index];
                if ((short) note <= Math.round(tick)) {
                    byte instrIdx = (byte) (note >> Note.INSTRUMENT_SHIFT);
                    byte noteVal = (byte) (note >> Note.NOTE_SHIFT);

                    if (instrIdx < 0 || instrIdx >= Note.INSTRUMENTS.length) {
                        index++;
                        continue;
                    }

                    NoteBlockInstrument instrument = Note.INSTRUMENTS[instrIdx];
                    HashMap<Byte, BlockPos> pitchMap = noteBlocks.get(instrument);
                    BlockPos blockPos = pitchMap != null ? pitchMap.get(noteVal) : null;

                    if (blockPos == null) {
                        index++;
                        continue;
                    }

                    if (!canInteractWith(client.player, blockPos)) {
                        stop();
                        if (client.player != null) {
                            client.player.sendSystemMessage(Component.translatable(Main.MOD_ID + ".player.to_far")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        }
                        return;
                    }

                    final long now2 = System.currentTimeMillis();

                    // Look at block
                    if ((lastLookSentAt == -1L || now2 - lastLookSentAt >= 50)
                        && last100MsSpanEstimatedPackets < last100MsReducePacketsAfter
                        && (reducePacketsUntil == -1L || reducePacketsUntil < now2)) {

                        if (client.player != null && client.player.connection != null) {
                            Vec3 unit = Vec3.atCenterOf(blockPos).add(0, 0.5, 0)
                                .subtract(client.player.getEyePosition()).normalize();
                            float yaw = Mth.wrapDegrees((float) (Math.atan2(unit.z, unit.x) * 57.2957763671875) - 90.0f);
                            float pitch = Mth.wrapDegrees((float) (-(Math.atan2(unit.y,
                                Math.sqrt(unit.x * unit.x + unit.z * unit.z)) * 57.2957763671875)));
                            client.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, true, false));
                            last100MsSpanEstimatedPackets++;
                            lastLookSentAt = now2;
                        }
                    } else if (last100MsSpanEstimatedPackets >= last100MsReducePacketsAfter) {
                        reducePacketsUntil = Math.max(reducePacketsUntil, now2 + 500);
                    }

                    // Play: START_DESTROY_BLOCK
                    if (last100MsSpanEstimatedPackets < last100MsStopPacketsAfter
                        && (stopPacketsUntil == -1L || stopPacketsUntil < now2)) {

                        if (client.player != null && client.player.connection != null) {
                            client.player.connection.send(new ServerboundPlayerActionPacket(
                                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, Direction.UP, 0));
                            last100MsSpanEstimatedPackets++;
                        }
                    } else if (last100MsSpanEstimatedPackets >= last100MsStopPacketsAfter) {
                        stopPacketsUntil = Math.max(stopPacketsUntil, now2 + 250);
                        reducePacketsUntil = Math.max(reducePacketsUntil, now2 + 10000);
                    }

                    // ABORT_DESTROY_BLOCK
                    if (last100MsSpanEstimatedPackets < last100MsReducePacketsAfter
                        && (reducePacketsUntil == -1L || reducePacketsUntil < now2)) {
                        if (client.player != null && client.player.connection != null) {
                            client.player.connection.send(new ServerboundPlayerActionPacket(
                                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, blockPos, Direction.UP, 0));
                            last100MsSpanEstimatedPackets++;
                        }
                    } else if (last100MsSpanEstimatedPackets >= last100MsReducePacketsAfter) {
                        reducePacketsUntil = Math.max(reducePacketsUntil, now2 + 500);
                    }

                    // Swing hand
                    if ((lastSwingSentAt == -1L || now2 - lastSwingSentAt >= 50)
                        && last100MsSpanEstimatedPackets < last100MsReducePacketsAfter
                        && (reducePacketsUntil == -1L || reducePacketsUntil < now2)) {
                        Minecraft.getInstance().execute(() -> {
                            if (client.player != null) client.player.swing(InteractionHand.MAIN_HAND);
                        });
                        lastSwingSentAt = now2;
                        last100MsSpanEstimatedPackets++;
                    } else if (last100MsSpanEstimatedPackets >= last100MsReducePacketsAfter) {
                        reducePacketsUntil = Math.max(reducePacketsUntil, now2 + 500);
                    }

                    index++;
                    if (index >= song.notes.length) {
                        didSongReachEnd = true;
                        playNext();
                        return;
                    }
                } else {
                    break;
                }
            }

            if (running) {
                tick += song.millisecondsToTicks(elapsedMs) * speed;
            }
        }
    }

    @Override
    public void accept(ClientLevel level) {
        Minecraft client = Minecraft.getInstance();
        if (level == null || client.player == null || song == null || !running) return;
        if (playbackThread == null) tickPlayback();

        if (noteBlocks == null) {
            ClientLevel world = level;

            HashMap<NoteBlockInstrument, List<BlockPos>> blocksByInstrument = new HashMap<>();
            for (NoteBlockInstrument instr : Note.INSTRUMENTS) {
                blocksByInstrument.put(instr, new ArrayList<>());
            }

            Set<NoteBlockInstrument> requiredInstruments = new HashSet<>();
            Set<Long> requiredNoteKeys = new HashSet<>();
            for (long n : song.notes) {
                byte instrIdx = (byte) (n >> Note.INSTRUMENT_SHIFT);
                byte noteVal = (byte) (n >> Note.NOTE_SHIFT);
                if (instrIdx >= 0 && instrIdx < Note.INSTRUMENTS.length) {
                    requiredInstruments.add(Note.INSTRUMENTS[instrIdx]);
                    requiredNoteKeys.add(((long) instrIdx << 8) | (noteVal & 0xFF));
                }
            }

            BlockPos playerPos = client.player.blockPosition();
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                    for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                        BlockPos pos = playerPos.offset(dx, dy, dz);
                        BlockState state = world.getBlockState(pos);
                        if (state.is(Blocks.NOTE_BLOCK) && canInteractWith(client.player, pos)) {
                            NoteBlockInstrument blockInstr = state.getValue(BlockStateProperties.NOTEBLOCK_INSTRUMENT);
                            if (requiredInstruments.contains(blockInstr)) {
                                blocksByInstrument.get(blockInstr).add(pos.immutable());
                            }
                        }
                    }
                }
            }

            HashMap<NoteBlockInstrument, HashMap<Byte, BlockPos>> newNoteBlocks = new HashMap<>();
            boolean allFound = true;
            missingInstrumentBlocks.clear();

            for (NoteBlockInstrument instrument : requiredInstruments) {
                HashMap<Byte, BlockPos> pitchAssignments = new HashMap<>();
                List<BlockPos> available = blocksByInstrument.get(instrument);
                int blockIdx = 0;

                int neededCount = 0;
                for (long key : requiredNoteKeys) {
                    byte keyInstr = (byte) (key >> 8);
                    if (Note.INSTRUMENTS[keyInstr] == instrument) {
                        neededCount++;
                        byte pitch = (byte) (key & 0xFF);
                        if (available != null && blockIdx < available.size()) {
                            pitchAssignments.put(pitch, available.get(blockIdx));
                            blockIdx++;
                        } else {
                            allFound = false;
                        }
                    }
                }
                newNoteBlocks.put(instrument, pitchAssignments);

                int have = available != null ? available.size() : 0;
                if (have < neededCount) {
                    int missing = neededCount - have;
                    Block block = Note.INSTRUMENT_BLOCKS.get(instrument);
                    missingInstrumentBlocks.merge(block, missing, Integer::sum);
                }
            }

            if (allFound) {
                noteBlocks = newNoteBlocks;

                for (Map.Entry<NoteBlockInstrument, HashMap<Byte, BlockPos>> instEntry : noteBlocks.entrySet()) {
                    for (Map.Entry<Byte, BlockPos> pitchEntry : instEntry.getValue().entrySet()) {
                        byte targetPitch = pitchEntry.getKey();
                        BlockPos pos = pitchEntry.getValue();
                        BlockState state = level.getBlockState(pos);
                        if (state.is(Blocks.NOTE_BLOCK)) {
                            int currentPitch = state.getValue(BlockStateProperties.NOTE);
                            if (currentPitch != targetPitch) {
                                int clicks = (targetPitch - currentPitch + 25) % 25;
                                for (int c = 0; c < clicks; c++) {
                                    if (client.player != null && client.player.connection != null) {
                                        BlockHitResult hitResult = new BlockHitResult(
                                            Vec3.atCenterOf(pos), Direction.UP, pos, false);
                                        client.player.connection.send(new ServerboundUseItemOnPacket(
                                            InteractionHand.MAIN_HAND, hitResult, 0));
                                    }
                                }
                            }
                        }
                    }
                }

                tuned = true;
                if (Main.config.server.delayPlaybackStartBySecs > 0) {
                    pausePlaybackUntil = System.currentTimeMillis()
                        + (long) (Main.config.server.delayPlaybackStartBySecs * 1000);
                }
            }
        }
    }

    /** Seek to a specific tick position using binary search. Resets tuning so blocks are re-scanned. */
    public synchronized void seekToTick(int targetTick) {
        if (song == null || !running) return;
        tick = Math.max(0, Math.min(targetTick, song.length));
        // Binary search for the first note index where tick > target
        long[] notes = song.notes;
        int lo = 0, hi = notes.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if ((short) notes[mid] <= tick) lo = mid + 1;
            else hi = mid;
        }
        index = lo;
        tuned = false;
        noteBlocks = null;
        lastPlaybackTickAt = -1L;
    }

    public double getCurrentTick() { return tick; }
    public int getTotalTicks() { return song != null ? song.length : 0; }
}
