package semmiedev.disc_jockey.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientWorldMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void makeNoteBlockSoundsOmnidirectional(double x, double y, double z, SoundEvent event, SoundSource category, float volume, float pitch, boolean useDistance, long seed, CallbackInfo ci) {
        ci.cancel();
        minecraft.getSoundManager().play(new SimpleSoundInstance(event, category, volume, pitch, RandomSource.create(seed), x, y, z));
    }
}
