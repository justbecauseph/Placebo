package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;

import dev.shadowsoffire.placebo.events.FabricDamageDispatcher;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.events.PlaceboEvents.LivingDamagePostContext;
import dev.shadowsoffire.placebo.events.PlaceboEvents.LivingDamagePreContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Mirrors NeoForge's two post-mitigation damage events in both vanilla {@code actuallyHurt} overrides.
 * {@code Player} has its own implementation, so this mixin must target both classes.
 */
@Mixin(value = { LivingEntity.class, Player.class }, remap = false)
public class LivingEntityDamageMixin {

    /*
     * One primitive invocation-local frame carries all state across the two bytecode seams. The high word is
     * the raw actuallyHurt input. The low word is either NO_FRAME (the hit returned before the PRE seam),
     * NO_PRE_HEALTH (direct mode did not apply), a nonnegative pre-hit-health float, or that float with the
     * sign bit set for a fallback invocation. The direct/fallback decision is made once at PRE, after the
     * vanilla mitigation work that precedes that seam, and is retained through POST even if listeners change
     * while this hit is in progress. Health is nonnegative in vanilla, which leaves its sign bit available as
     * the fallback tag; the two NaN sentinels are never valid health values. Clearing the frame before POST
     * also prevents a nested berserking hurt from consuming it.
     */
    private static final int NO_FRAME = 0x7FC0_0000;
    private static final int NO_PRE_HEALTH = 0x7FC0_0001;
    private static final int FALLBACK_FLAG = 0x8000_0000;

    @Inject(method = "actuallyHurt", at = @At("HEAD"), remap = false)
    private void placebo$captureLivingDamageOriginal(ServerLevel level, DamageSource source, float damage, CallbackInfo ci,
        @Share("livingDamageFrame") LocalLongRef frameRef) {
        frameRef.set(packFrame(damage, NO_FRAME));
    }

    @ModifyExpressionValue(
        method = "actuallyHurt",
        at = @At(value = "INVOKE", target = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"),
        remap = false)
    private float placebo$fireLivingDamagePre(float mitigatedDamage, @Local(argsOnly = true) DamageSource source,
        @Share("livingDamageFrame") LocalLongRef frameRef) {
        LivingEntity entity = (LivingEntity) (Object) this;
        long frame = frameRef.get();
        float originalDamage = originalDamage(frame);
        boolean fallback = !PlaceboEvents.livingDamagePreDirectAllowed()
            || !PlaceboEvents.livingDamagePostDirectAllowed();
        if (fallback) {
            float preHealth = Math.max(entity.getHealth(), 0.0F);
            LivingDamagePreContext pre = new LivingDamagePreContext(entity, source, originalDamage, mitigatedDamage);
            PlaceboEvents.fireLivingDamagePre(pre);
            frameRef.set(packFrame(originalDamage, fallbackPreHealth(preHealth)));
            return pre.getDamage();
        }

        float preHealth = FabricDamageDispatcher.dispatchPre(entity, source, originalDamage, mitigatedDamage);
        frameRef.set(packFrame(originalDamage, preHealth >= 0.0F ? Float.floatToRawIntBits(preHealth) : NO_PRE_HEALTH));
        return mitigatedDamage;
    }

    @Inject(method = "actuallyHurt", at = @At("TAIL"), remap = false)
    private void placebo$fireLivingDamagePost(ServerLevel level, DamageSource source, float damage, CallbackInfo ci,
        @Local(name = "originalDamage") float inflictedDamage,
        @Local(name = "dmg") float healthDamage,
        @Share("livingDamageFrame") LocalLongRef frameRef) {
        long frame = frameRef.get();
        frameRef.set(packFrame(0.0F, NO_FRAME));
        int marker = marker(frame);
        if (marker == NO_FRAME || !(healthDamage > 0.0F)) {
            return;
        }

        LivingEntity entity = (LivingEntity) (Object) this;
        float originalDamage = originalDamage(frame);
        boolean fallback = (marker & FALLBACK_FLAG) != 0;
        float preHealth = fallback ? Float.intBitsToFloat(marker & ~FALLBACK_FLAG)
            : (marker == NO_PRE_HEALTH ? -1.0F : Float.intBitsToFloat(marker));
        if (fallback) {
            PlaceboEvents.fireLivingDamagePost(new LivingDamagePostContext(entity, source, originalDamage,
                inflictedDamage, healthDamage, preHealth));
        } else {
            FabricDamageDispatcher.dispatchPost(entity, source, originalDamage, inflictedDamage, healthDamage, preHealth);
        }
    }

    private static long packFrame(float originalDamage, int marker) {
        return ((long) Float.floatToRawIntBits(originalDamage) << 32) | (marker & 0xFFFF_FFFFL);
    }

    private static float originalDamage(long frame) {
        return Float.intBitsToFloat((int) (frame >>> 32));
    }

    private static int marker(long frame) {
        return (int) frame;
    }

    private static int fallbackPreHealth(float health) {
        return Float.floatToRawIntBits(Math.max(health, 0.0F)) | FALLBACK_FLAG;
    }

}
