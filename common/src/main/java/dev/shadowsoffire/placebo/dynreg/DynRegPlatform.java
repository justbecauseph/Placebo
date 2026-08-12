package dev.shadowsoffire.placebo.dynreg;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The four things {@link DynamicRegistry} needs from the loader, installed once by the platform entrypoint.
 * <p>
 * This is a deliberately small surface. Everything else in {@code dynreg} -- codecs, holder resolution,
 * weighted selection, staging, tag binding -- is pure vanilla and lives in common unchanged.
 * <p>
 * Phase 2a installs the NeoForge implementation from {@code PlaceboNeoForge}. Phase 2b adds the Fabric one:
 * {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS} for sync, {@code ResourceManagerHelper} for reload
 * registration, {@code ServerPlayNetworking} for sends, and {@code ResourceConditions} for conditions.
 */
public final class DynRegPlatform {

    private static Function<DynamicRegistry<?>, ReloadContext> contextFactory;
    private static ReloadRegistrar reloadRegistrar;
    private static SyncHandler payloadSender;

    private DynRegPlatform() {}

    /**
     * Installs the platform implementations. Must be called before any registry reloads -- i.e. from the
     * platform entrypoint's construction or setup, not lazily.
     */
    public static void install(Function<DynamicRegistry<?>, ReloadContext> contexts, ReloadRegistrar registrar, SyncHandler sender) {
        contextFactory = contexts;
        reloadRegistrar = registrar;
        payloadSender = sender;
    }

    /**
     * Builds the {@link ReloadContext} for a registry's current reload.
     */
    public static ReloadContext contextFor(DynamicRegistry<?> registry) {
        return require(contextFactory, "context factory").apply(registry);
    }

    /**
     * Registers a registry as a server reload listener. The platform is responsible for ordering it before
     * its own tag manager -- tag loading must run after registry content has been deserialized.
     */
    public static void registerReloadListener(Identifier id, DynamicRegistry<?> registry, List<Identifier> dependencies) {
        require(reloadRegistrar, "reload registrar").register(id, registry, dependencies);
    }

    /**
     * The sync payload handler. {@link DynamicRegistry#sync} owns the ordering; the platform owns the
     * payload types and how they are dispatched.
     */
    public static SyncHandler sync() {
        return require(payloadSender, "sync handler");
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalStateException("Placebo's dynamic registry " + what + " was never installed. "
                + "The platform entrypoint must call DynRegPlatform.install(...) during setup.");
        }
        return value;
    }

    @FunctionalInterface
    public interface ReloadRegistrar {
        void register(Identifier id, DynamicRegistry<?> registry, List<Identifier> dependencies);
    }

    /**
     * Emits the sync packet sequence for one registry. A null player means "every player".
     * <p>
     * This is an interface rather than a raw payload sender because the payload types themselves are
     * platform-specific: NeoForge uses its own {@code CustomPacketPayload} providers, and Fabric registers
     * payloads through {@code PayloadTypeRegistry}.
     */
    public interface SyncHandler {
        void start(@Nullable ServerPlayer player, Identifier registryId);

        <R> void content(@Nullable ServerPlayer player, Identifier registryId, Identifier key, R value);

        void tags(@Nullable ServerPlayer player, Identifier registryId, java.util.Map<Identifier, java.util.List<Identifier>> tags);

        void end(@Nullable ServerPlayer player, Identifier registryId);
    }

}
