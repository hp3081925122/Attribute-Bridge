package com.hp.attributebridge;

import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import java.util.*;

public final class BridgeCommands {
    private BridgeCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("attributebridge")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload").executes(context -> {
                    try {
                        int count = BridgeRuntime.reload();
                        BridgeNetwork.broadcast();
                        context.getSource().sendSuccess(() -> Component.translatable("attribute_bridge.reloaded", count), true);
                        return count + 1;
                    } catch (IllegalArgumentException exception) {
                        context.getSource().sendFailure(Component.translatable("attribute_bridge.reload_failed", exception.getMessage()));
                        return 0;
                    }
                }))
                .then(Commands.literal("list").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("attribute_bridge.group_count", BridgeRuntime.serverConfig().groups().size()), false);
                    for (BridgeConfig.Group group : BridgeRuntime.serverConfig().groups()) {
                        context.getSource().sendSuccess(() -> Component.translatable("attribute_bridge.group", group.name(), group.replace() ? "replace" : "combine", group.ids().toString()), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("inspect").then(Commands.argument("entity", EntityArgument.entity())
                        .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(BridgeRuntime.serverConfig().groups().stream().flatMap(group -> group.ids().stream()).map(ResourceLocation::toString), builder))
                                .executes(context -> {
                                    if (!(EntityArgument.getEntity(context, "entity") instanceof LivingEntity entity)) {
                                        context.getSource().sendFailure(Component.translatable("attribute_bridge.not_living"));
                                        return 0;
                                    }
                                    ResourceLocation id = ResourceLocationArgument.getId(context, "attribute");
                                    Attribute attribute = BuiltInRegistries.ATTRIBUTE.getOptional(id).orElse(null);
                                    AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
                                    if (instance == null) {
                                        context.getSource().sendFailure(Component.translatable("attribute_bridge.no_attribute", id.toString()));
                                        return 0;
                                    }
                                    Collection<AttributeModifier> effective = BridgeRuntime.effective(entity.getAttributes(), attribute, instance.getModifiers());
                                    context.getSource().sendSuccess(() -> Component.translatable("attribute_bridge.inspect", entity.getDisplayName(), id.toString(), instance.getBaseValue(), instance.getValue(), instance.getModifiers().size(), effective.size()), false);
                                    for (AttributeModifier modifier : effective) {
                                        context.getSource().sendSuccess(() -> Component.translatable("attribute_bridge.modifier", modifier.getId().toString(), modifier.getOperation().name(), modifier.getAmount()), false);
                                    }
                                    return 1;
                                })))));
    }
}
