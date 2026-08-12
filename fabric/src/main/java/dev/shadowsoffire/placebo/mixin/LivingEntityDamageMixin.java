package dev.shadowsoffire.placebo.mixin;

import java.util.ArrayDeque;
import java.util.Deque;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

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

    /** Post listeners may recursively start another damage sequence, so each Pre invocation owns one stack entry. */
    private static final ThreadLocal<Deque<LivingDamagePostContext>> PLACEBO$POST_DAMAGE = ThreadLocal.withInitial(ArrayDeque::new);

    @ModifyExpressionValue(
        method = "actuallyHurt",
        at = @At(value = "INVOKE", target = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"),
        remap = false)
    private float placebo$fireLivingDamagePre(float mitigatedDamage, @Local(argsOnly = true) DamageSource source, @Local(argsOnly = true) float originalDamage) {
        LivingEntity entity = (LivingEntity) (Object) this;
        LivingDamagePreContext pre = new LivingDamagePreContext(entity, source, originalDamage, mitigatedDamage);
        PlaceboEvents.fireLivingDamagePre(pre);

        float inflictedDamage = pre.getDamage();
        float healthDamage = Math.max(inflictedDamage - entity.getAbsorptionAmount(), 0.0F);
        PLACEBO$POST_DAMAGE.get().push(new LivingDamagePostContext(entity, source, originalDamage, inflictedDamage, healthDamage));
        return inflictedDamage;
    }

    @Inject(method = "actuallyHurt", at = @At("TAIL"), remap = false)
    private void placebo$fireLivingDamagePost(ServerLevel level, DamageSource source, float damage, CallbackInfo ci) {
        Deque<LivingDamagePostContext> contexts = PLACEBO$POST_DAMAGE.get();
        if (contexts.isEmpty()) {
            return;
        }
        LivingDamagePostContext post = contexts.pop();
        if (contexts.isEmpty()) {
            PLACEBO$POST_DAMAGE.remove();
        }
        PlaceboEvents.fireLivingDamagePost(post);
    }

}
