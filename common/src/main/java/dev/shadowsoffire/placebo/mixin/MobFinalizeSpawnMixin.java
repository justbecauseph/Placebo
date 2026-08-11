package dev.shadowsoffire.placebo.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Fires {@link PlaceboEvents#FINALIZE_SPAWN}.
 * <p>
 * <b>The only mixin in this stack that applies on both loaders.</b> Every other gap event is bridged from
 * NeoForge's own equivalent, because NeoForge already fires one. This one is not, because NeoForge's
 * {@code FinalizeSpawnEvent} is posted from {@code BaseSpawner} and {@code TrialSpawner} and nowhere else —
 * {@code NaturalSpawner} calls this method directly. Verified by scanning the patched 26.2 jar: exactly two
 * classes reference the hook.
 * <p>
 * Bridging would therefore have carried that gap onto Fabric, and hooking the vanilla method on Fabric alone
 * would have given Fabric working natural-spawn replacement that NeoForge does not have. Hooking it on both is
 * what keeps the loaders agreeing, and it is why this file lives in {@code common} — both mixin config
 * generators scan this tree.
 * <p>
 * NeoForge's own event is untouched and still fires for anyone listening to it. The visible change on NeoForge
 * is that listeners on <i>this</i> event see spawns {@code FinalizeSpawnEvent} never did.
 */
@Mixin(value = Mob.class, remap = false)
public abstract class MobFinalizeSpawnMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true, remap = false)
    private void placebo$fireFinalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
        @Nullable SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> cir) {
        if (PlaceboEvents.fireFinalizeSpawn((Mob) (Object) this, level, spawnReason)) {
            cir.setReturnValue(null);
        }
    }

}
