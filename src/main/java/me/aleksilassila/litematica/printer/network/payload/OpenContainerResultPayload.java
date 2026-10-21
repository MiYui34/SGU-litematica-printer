package me.aleksilassila.litematica.printer.network.payload;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：远程开容器的结果。
 *
 * <p>成功时不需要额外字段：服务端的那次「使用」本身就会让原版发出打开界面的包，
 * 客户端会像自己右键打开了箱子一样收到它。这里只回报失败原因，让客户端能立刻放弃
 * 这个候选容器，而不是干等到超时。</p>
 *
 * @param nonce  对应请求的流水号
 * @param status {@link #STATUS_OK} 等状态码
 */
public record OpenContainerResultPayload(int nonce, byte status) implements CustomPacketPayload {

    public static final byte STATUS_OK = 0;
    /** 方块不是容器（或者方块实体丢了）。 */
    public static final byte STATUS_NOT_CONTAINER = 1;
    /** 方块自己的使用逻辑拒绝了（例如潜影盒被挡住打不开）。 */
    public static final byte STATUS_REJECTED = 2;
    /** 区块加载超时 / 维度不存在 / 坐标越界。 */
    public static final byte STATUS_UNAVAILABLE = 3;
    /** 服务端关掉了远程开容器功能。 */
    public static final byte STATUS_DISABLED = 4;
    /** 超出服务端允许的远程距离。 */
    public static final byte STATUS_TOO_FAR = 5;

    public static final Type<OpenContainerResultPayload> TYPE =
            new Type<>(PrinterNetworking.OPEN_CONTAINER_RESULT_CHANNEL);

    public static final StreamCodec<FriendlyByteBuf, OpenContainerResultPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.nonce);
                buf.writeByte(payload.status);
            },
            buf -> new OpenContainerResultPayload(buf.readVarInt(), buf.readByte()));

    public boolean ok() {
        return this.status == STATUS_OK;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
