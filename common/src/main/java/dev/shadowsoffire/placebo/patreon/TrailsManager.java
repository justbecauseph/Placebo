package dev.shadowsoffire.placebo.patreon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.PlaceboClient;
import dev.shadowsoffire.placebo.network.ClientPayloadSender;
import dev.shadowsoffire.placebo.patreon.PatreonUtils.PatreonParticleType;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload.CosmeticType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** Shared Patreon-trail state and client logic. The loader only supplies its tick and keybinding hooks. */
public class TrailsManager {

    static final Map<UUID, PatreonParticleType> TRAILS = new HashMap<>();
    public static final KeyMapping TOGGLE = new KeyMapping("placebo.toggleTrails", GLFW.GLFW_KEY_KP_9, PlaceboClient.KEY_CATEGORY);
    public static final Set<UUID> DISABLED = new HashSet<>();

    public static void init() {
        new Thread(() -> {
            Placebo.LOGGER.info("Loading patreon trails data...");
            try {
                URL url = new URI("https://raw.githubusercontent.com/Shadows-of-Fire/Placebo/1.16/PatreonTrails.txt").toURL();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String s;
                    while ((s = reader.readLine()) != null) {
                        String[] split = s.split(" ", 2);
                        if (split.length != 2) {
                            Placebo.LOGGER.error("Invalid patreon trail entry {} will be ignored.", s);
                            continue;
                        }
                        TRAILS.put(UUID.fromString(split[0]), PatreonParticleType.valueOf(split[1]));
                    }
                }
                catch (IOException ex) {
                    Placebo.LOGGER.error("Exception loading patreon trails data!", ex);
                }
            }
            catch (Exception ex) {
                Placebo.LOGGER.error("Exception loading patreon trails data!", ex);
            }
            Placebo.LOGGER.info("Loaded {} patreon trails.", TRAILS.size());
        }, "Placebo Patreon Trail Loader").start();
    }

    /** Called by the platform's client-post-tick hook. */
    public static void tick() {
        PatreonParticleType type = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            for (Player player : mc.level.players()) {
                if (!player.isInvisible() && player.tickCount * 3 % 2 == 0 && !DISABLED.contains(player.getUUID()) && (type = TRAILS.get(player.getUUID())) != null) {
                    ClientLevel level = (ClientLevel) player.level();
                    RandomSource random = level.getRandom();
                    ParticleOptions particle = type.type.get();
                    level.addParticle(particle, player.getX() + random.nextDouble() * 0.4 - 0.2, player.getY() + 0.1, player.getZ() + random.nextDouble() * 0.4 - 0.2, 0, 0, 0);
                }
            }
        }
    }

    /** Called by the platform's client-post-tick hook after key mappings have been registered. */
    public static void handleKeybind() {
        while (TOGGLE.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null && mc.player != null) {
                ClientPayloadSender.toServer(new PatreonDisablePayload(CosmeticType.TRAILS, mc.player.getUUID()));
            }
        }
    }
}
