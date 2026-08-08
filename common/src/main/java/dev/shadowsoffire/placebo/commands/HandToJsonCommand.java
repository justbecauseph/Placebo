package dev.shadowsoffire.placebo.commands;

import java.io.IOException;
import java.io.StringWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.JsonOps;

import dev.shadowsoffire.placebo.json.OptionalStackCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

public class HandToJsonCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(arg -> Component.translatable("placebo.cmd.not_found", arg));

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.literal("hand").executes(ctx -> {
            try {
                String str = toJsonStr(ctx.getSource().getEntity().getWeaponItem(), RegistryOps.create(JsonOps.INSTANCE, ctx.getSource().getServer().registryAccess()));
                ctx.getSource().sendSystemMessage(Component.literal(str));
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            return 0;
        }));
    }

    public static String toJsonStr(ItemStack stack, RegistryOps<JsonElement> ops) throws IOException {
        JsonElement json = OptionalStackCodec.INSTANCE.encodeStart(ops, stack).getOrThrow();
        StringWriter str = new StringWriter();
        JsonWriter writer = GSON.newJsonWriter(str);
        writer.setIndent("    ");
        GSON.toJson(json, JsonObject.class, writer);
        return String.format("Currently Held Item:\n%s", str.toString());
    }
}
