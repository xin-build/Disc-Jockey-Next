package semmiedev.disc_jockey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public class Previewer implements Consumer<ClientLevel> {
    public boolean running;

    private int i;
    private float tick;
    private Song song;

    public Previewer() {
        Main.TICK_LISTENERS.add(this);
    }

    public void start(Song song) {
        this.song = song;
        i = 0;
        tick = 0;
        running = true;
    }

    public void stop() {
        running = false;
        i = 0;
        tick = 0;
    }

    @Override
    public void accept(ClientLevel world) {
        if (!running) return;
        if (song == null || song.notes.length == 0) return;

        while (running) {
            long note = song.notes[i];
            if ((short)note <= tick) {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return;
                Vec3 pos = client.player.getEyePosition();
                world.playSound(client.player, pos.x, pos.y, pos.z,
                    Note.INSTRUMENTS[(byte)(note >> Note.INSTRUMENT_SHIFT)].getSoundEvent(),
                    SoundSource.RECORDS, 3,
                    (float)Math.pow(2.0, ((byte)(note >> Note.NOTE_SHIFT) - 12) / 12.0));
                i++;
                if (i >= song.notes.length) {
                    stop();
                    break;
                }
            } else {
                break;
            }
        }

        if (song != null) {
            tick += song.tempo / 100f / 20f;
        }
    }
}