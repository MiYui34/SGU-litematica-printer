package me.aleksilassila.litematica.printer.network.payload;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 客户端 → 服务端：请求服务端代玩家打开一个远处的容器。
 *
 * @param dimension 容器所在维度
 * @param pos       容器坐标
 * @param nonce     请求流水号，回执原样带回，用来把回执和请求对上
 */
public record OpenContainerRequestPayload(ResourceKey<Level> dimension, BlockPos pos, int nonce)
        implements CustomPacketPayload {

    public static final Type<OpenContainerRequestPayload> TYPE =
            new Type<>(PrinterNetworking.OPEN_CONTAINER_CHANNEL);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenContainerRequestPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeResourceKey(payload.dimension);
                        buf.writeBlockPos(payload.pos);
                        buf.writeVarInt(payload.nonce);
                    },
                    buf -> new OpenContainerRequestPayload(
                            buf.readResourceKey(Registries.DIMENSION), buf.readBlockPos(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
