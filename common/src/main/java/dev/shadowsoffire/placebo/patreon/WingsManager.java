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
import dev.shadowsoffire.placebo.patreon.PatreonUtils.WingType;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload.CosmeticType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;

/** Shared Patreon-wing state and keybinding logic. The actual renderer is loader-specific. */
public class WingsManager {

    static final Map<UUID, WingType> WINGS = new HashMap<>();
    public static final KeyMapping TOGGLE = new KeyMapping("placebo.toggleWings", GLFW.GLFW_KEY_KP_8, PlaceboClient.KEY_CATEGORY);
    public static final Set<UUID> DISABLED = new HashSet<>();
    public static final ModelLayerLocation WING_LOC = new ModelLayerLocation(Placebo.loc("wings"), "main");

    public static void init() {
        new Thread(() -> {
            Placebo.LOGGER.info("Loading patreon wing data...");
            try {
                URL url = new URI("https://raw.githubusercontent.com/Shadows-of-Fire/Placebo/1.16/PatreonWings.txt").toURL();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String s;
                    while ((s = reader.readLine()) != null) {
                        String[] split = s.split(" ", 2);
                        if (split.length != 2) {
                            Placebo.LOGGER.error("Invalid patreon wing entry {} will be ignored.", s);
                            continue;
                        }
                        WINGS.put(UUID.fromString(split[0]), WingType.valueOf(split[1]));
                    }
                }
                catch (IOException ex) {
                    Placebo.LOGGER.error("Exception loading patreon wing data!", ex);
                }
            }
            catch (Exception ex) {
                Placebo.LOGGER.error("Exception loading patreon wing data!", ex);
            }
            Placebo.LOGGER.info("Loaded {} patreon wings.", WINGS.size());
        }, "Placebo Patreon Wing Loader").start();
    }

    /** Called by the platform's client-post-tick hook after key mappings have been registered. */
    public static void handleKeybind() {
        while (TOGGLE.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null && mc.player != null) {
                ClientPayloadSender.toServer(new PatreonDisablePayload(CosmeticType.WINGS, mc.player.getUUID()));
            }
        }
    }

    public static WingType getType(UUID id) {
        return WINGS.get(id);
    }
}
