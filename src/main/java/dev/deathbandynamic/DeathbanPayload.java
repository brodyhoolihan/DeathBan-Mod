package dev.deathbandynamic;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: millis left on the ban (-1 = permanent). A duration, not a timestamp, so clock skew can't matter. */
public record DeathbanPayload(long remainingMillis) implements CustomPacketPayload {
    public static final Type<DeathbanPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(DynamicDeathBan.MOD_ID, "sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeathbanPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.LONG, DeathbanPayload::remainingMillis, DeathbanPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
