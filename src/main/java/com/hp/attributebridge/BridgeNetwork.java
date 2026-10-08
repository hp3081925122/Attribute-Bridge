package com.hp.attributebridge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

public final class BridgeNetwork {
    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(AttributeBridge.MOD_ID, "rules"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private BridgeNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, Rules.class, Rules::encode, Rules::decode, Rules::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Rules(BridgeRuntime.serverConfig().json()));
    }

    public static void broadcast() {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new Rules(BridgeRuntime.serverConfig().json()));
    }

    private record Rules(String json) {
        private void encode(FriendlyByteBuf buffer) { buffer.writeUtf(json, 65536); }
        private static Rules decode(FriendlyByteBuf buffer) { return new Rules(buffer.readUtf(65536)); }
        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                try {
                    BridgeRuntime.receive(json);
                } catch (RuntimeException exception) {
                    BridgeRuntime.LOGGER.error("Server attribute bridge rules cannot be applied on this client", exception);
                    context.getNetworkManager().disconnect(Component.translatable("attribute_bridge.rules_mismatch"));
                }
            });
            context.setPacketHandled(true);
        }
    }
}
