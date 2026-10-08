package com.hp.attributebridge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraft.server.level.ServerPlayer;

@Mod(AttributeBridge.MOD_ID)
public final class AttributeBridge {
    public static final String MOD_ID = "attribute_bridge";

    public AttributeBridge(FMLJavaModLoadingContext context) {
        BridgeNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(this::starting);
        MinecraftForge.EVENT_BUS.addListener(this::login);
        MinecraftForge.EVENT_BUS.addListener(BridgeCommands::register);
    }

    private void starting(ServerAboutToStartEvent event) {
        try { BridgeRuntime.reload(); } catch (IllegalArgumentException ignored) {}
    }

    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BridgeNetwork.send(player);
        }
    }
}
