package me.aleksilassila.litematica.printer.network.payload;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：装在本服务器上的本模组服务端部分能做什么。
 *
 * <p>客户端只按这里声明的能力发请求，所以「服务端没装本模组」和「服务端装了但关了某个功能」
 * 两种情况都不会产生无谓流量，也不会让客户端以为功能可用。</p>
 *
 * @param exactPlacement 是否接受 {@link ExactPlacePayload} 精确状态放置
 * @param interactionAck 是否回传交互处理结果
 */
public record ServerHelloPayload(boolean exactPlacement, boolean interactionAck)
        implements CustomPacketPayload {

    public static final Type<ServerHelloPayload> TYPE =
            new Type<>(PrinterNetworking.SERVER_HELLO_CHANNEL);

    public static final StreamCodec<FriendlyByteBuf, ServerHelloPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.exactPlacement);
                buf.writeBoolean(payload.interactionAck);
            },
            buf -> new ServerHelloPayload(buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}