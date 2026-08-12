package dev.shadowsoffire.placebo.mixin;

import java.util.ArrayDeque;
import java.util.Deque;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.events.PlaceboEvents.IncomingDamageContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Recreates NeoForge's mutable incoming-damage event after vanilla's immunity checks but before item
 * blocking, armor/magic reductions, absorption and health mutation.
 */
@Mixin(value = LivingEntity.class, remap = false)
public class LivingEntityIncomingDamageMixin {

    /** Damage handlers may recursively damage another entity, so a stack is required rather than one slot. */
    private static final ThreadLocal<Deque<IncomingDamageContext>> PLACEBO$INCOMING_DAMAGE = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
        method = "hurtServer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"),
        cancellable = true,
        remap = false)
    private void placebo$fireIncomingDamage(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        IncomingDamageContext ctx = new IncomingDamageContext((LivingEntity) (Object) this, source, damage);
        if (PlaceboEvents.fireIncomingDamage(ctx)) {
            cir.setReturnValue(false);
            return;
        }
        PLACEBO$INCOMING_DAMAGE.get().push(ctx);
    }

    /**
     * Vanilla immediately stores the incoming amount after its non-negative clamp. Replacing that store lets
     * the shared event adjust the original method argument before every downstream vanilla calculation.
     */
    @ModifyVariable(method = "hurtServer", at = @At(value = "STORE"), argsOnly = true, ordinal = 0, remap = false)
    private float placebo$applyIncomingDamage(float vanillaDamage) {
        Deque<IncomingDamageContext> contexts = PLACEBO$INCOMING_DAMAGE.get();
        if (contexts.isEmpty()) {
            return vanillaDamage;
        }
        IncomingDamageContext ctx = contexts.pop();
        if (contexts.isEmpty()) {
            PLACEBO$INCOMING_DAMAGE.remove();
        }
        return ctx.getDamage();
    }

}
