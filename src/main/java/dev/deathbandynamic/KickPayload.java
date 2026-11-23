package dev.deathbandynamic;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client, sent right before a deathban kick so the disconnect screen can show a live countdown. */
public record KickPayload(long remainingMillis) implements CustomPacketPayload {
    public static final Type<KickPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(DynamicDeathBan.MOD_ID, "kick"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KickPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.LONG, KickPayload::remainingMillis, KickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
