package com.hp.attributebridge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AttributeBridge.MOD_ID, value = Dist.CLIENT)
public final class BridgeClientEvents {
    private BridgeClientEvents() {}

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BridgeRuntime.disconnect(); }
}
