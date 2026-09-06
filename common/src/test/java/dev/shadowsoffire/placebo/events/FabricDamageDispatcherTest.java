package dev.shadowsoffire.placebo.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.architectury.event.EventPriority;

final class FabricDamageDispatcherTest {

    @Test
    void fixedPostSlotsRunInOrderExactlyOnceAndFallbackUsesTheSameComposite() {
        List<String> order = new ArrayList<>();
        int[] attributesCalls = {0};
        int[] apotheosisCalls = {0};
        int[] enchantingCalls = {0};

        FabricDamageDispatcher.registerAttributesPost((target, source, original, inflicted, health, preHealth) -> {
            attributesCalls[0]++;
            order.add("attributes");
        });
        FabricDamageDispatcher.registerApotheosisPost((target, source, original, inflicted, health, preHealth) -> {
            apotheosisCalls[0]++;
            order.add("apotheosis");
        });
        FabricDamageDispatcher.registerEnchantingPost((target, source, original, inflicted, health, preHealth) -> {
            enchantingCalls[0]++;
            order.add("enchanting");
        });

        FabricDamageDispatcher.dispatchPost(null, null, 5.0F, 4.0F, 3.0F, 20.0F);
        assertEquals(List.of("attributes", "apotheosis", "enchanting"), order,
            "direct POST slots changed their gameplay order");
        assertEquals(1, attributesCalls[0], "direct Attributes POST ran more than once");
        assertEquals(1, apotheosisCalls[0], "direct Apotheosis POST ran more than once");
        assertEquals(1, enchantingCalls[0], "direct Enchanting POST ran more than once");

        FabricDamageDispatcher.installLivingDamagePostFallback();
        int[] externalCalls = {0};
        PlaceboEvents.LivingDamagePost external = context -> {
            externalCalls[0]++;
            order.add("external");
        };
        PlaceboEvents.LIVING_DAMAGE_POST.register(EventPriority.NORMAL, external);
        try {
            assertTrue(!PlaceboEvents.livingDamagePostDirectAllowed(),
                "the external POST listener did not publish fallback mode");
            order.clear();
            PlaceboEvents.fireLivingDamagePost(new PlaceboEvents.LivingDamagePostContext(
                null, null, 5.0F, 4.0F, 3.0F, 20.0F));
            assertEquals(List.of("attributes", "apotheosis", "enchanting", "external"), order,
                "fallback POST did not preserve the fixed owned order before external listeners");
            assertEquals(2, attributesCalls[0], "fallback invoked Attributes POST more than once");
            assertEquals(2, apotheosisCalls[0], "fallback invoked Apotheosis POST more than once");
            assertEquals(2, enchantingCalls[0], "fallback invoked Enchanting POST more than once");
            assertEquals(1, externalCalls[0], "the external POST listener did not run exactly once");
        }
        finally {
            PlaceboEvents.LIVING_DAMAGE_POST.unregister(external);
        }
        assertTrue(PlaceboEvents.livingDamagePostDirectAllowed(),
            "removing the last external POST listener did not restore direct mode");
    }
}
