package dev.shadowsoffire.placebo;

import net.neoforged.fml.startup.DataClient;

/**
 * Finishes NeoForge data generation with a process exit.
 * <p>
 * In NeoForge 26.2, {@link DataClient#main(String[])} returns after closing FML but leaves vanilla's
 * non-daemon worker pools alive. Every Architectury data run shares this launcher so Gradle receives
 * the completed process result instead of waiting indefinitely for those pools.
 */
public final class NeoForgeDataClient {

    private NeoForgeDataClient() {}

    public static void main(String[] args) {
        DataClient.main(args);
        System.exit(0);
    }
}
