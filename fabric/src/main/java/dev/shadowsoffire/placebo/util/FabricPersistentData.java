package dev.shadowsoffire.placebo.util;

import dev.shadowsoffire.placebo.Placebo;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * {@link PersistentData.Impl} for Fabric, over {@code fabric-data-attachment-api-v1}.
 * <p>
 * One persistent attachment holding a {@code CompoundTag}, standing in for NeoForge's shared per-entity bag.
 * {@code copyOnDeath} is set because NeoForge's persistent data survives a player respawn, and several flags in
 * this stack rely on that.
 */
public class FabricPersistentData implements PersistentData.Impl {

    private static final AttachmentType<CompoundTag> DATA = AttachmentRegistry.create(
        Placebo.loc("persistent_data"),
        builder -> builder
            .persistent(CompoundTag.CODEC)
            .initializer(CompoundTag::new)
            .copyOnDeath());

    @Override
    public CompoundTag of(Entity entity) {
        return ((AttachmentTarget) entity).getAttachedOrCreate(DATA, CompoundTag::new);
    }

    /**
     * Fabric's attachment lookup is non-creating: an entity with no Placebo data keeps no empty attachment after
     * this call. This is the guarantee used by optional marker checks such as GatewayEntity.getOwner().
     */
    @Override
    @Nullable
    public CompoundTag peek(Entity entity) {
        return ((AttachmentTarget) entity).getAttached(DATA);
    }

}
