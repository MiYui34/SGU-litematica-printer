package me.aleksilassila.litematica.printer.network.payload;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：握手。玩家连上装了我们服务端部分的服务器时发一次。
 *
 * <p>客户端据此决定要不要走「请服务端代开容器」这条路；没收到握手就完全退回原版行为。</p>
 *
 * @param remoteContainer   服务端是否允许远程开容器
 * @param interactionAck    服务端是否会回发交互回执
 * @param maxContainerRange 服务端允许的远程开容器最大距离（方块）
 */
public record ServerHelloPayload(boolean remoteContainer, boolean interactionAck, int maxContainerRange)
        implements CustomPacketPayload {

    public static final Type<ServerHelloPayload> TYPE =
            new Type<>(PrinterNetworking.SERVER_HELLO_CHANNEL);

    public static final StreamCodec<FriendlyByteBuf, ServerHelloPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.remoteContainer);
                buf.writeBoolean(payload.interactionAck);
                buf.writeVarInt(payload.maxContainerRange);
            },
            buf -> new ServerHelloPayload(buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
