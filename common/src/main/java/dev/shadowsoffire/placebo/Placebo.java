package dev.shadowsoffire.placebo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

/**
 * Loader-neutral identity for Placebo: mod id, logger, and namespace helper.
 * <p>
 * The platform entrypoints ({@code PlaceboNeoForge} and, in Phase 2b, the Fabric initializer) live in their
 * own subprojects and reference these constants. Common code must never reach for a platform entrypoint.
 */
public class Placebo {

    public static final String MODID = "placebo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private Placebo() {}

    public static Identifier loc(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

}
