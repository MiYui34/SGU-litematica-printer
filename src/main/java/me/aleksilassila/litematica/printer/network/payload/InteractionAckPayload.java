package me.aleksilassila.litematica.printer.network.payload;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：玩家的一次「对空气/方块使用物品」或「破坏方块」请求在服务端的处理结果。
 *
 * <p>只带服务端才知道的事实：请求是否被接受。客户端据此把「明明发了包，方块却一直没变」的格子
 * 从无限重试改成进冷却，避免对着同一个位置每秒发几十次包。</p>
 *
 * @param pos      请求指向的方块坐标（放置是点击的方块，破坏是被破坏的方块）
 * @param kind     {@link #KIND_PLACE} 或 {@link #KIND_BREAK}
 * @param accepted 服务端是否接受了这次请求
 */
public record InteractionAckPayload(BlockPos pos, byte kind, boolean accepted) implements CustomPacketPayload {

    public static final byte KIND_PLACE = 0;
    public static final byte KIND_BREAK = 1;

    public static final Type<InteractionAckPayload> TYPE =
            new Type<>(PrinterNetworking.INTERACTION_ACK_CHANNEL);

    public static final StreamCodec<FriendlyByteBuf, InteractionAckPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeByte(payload.kind);
                buf.writeBoolean(payload.accepted);
            },
            buf -> new InteractionAckPayload(buf.readBlockPos(), buf.readByte(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
