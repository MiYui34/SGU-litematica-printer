package me.aleksilassila.litematica.printer.network.payload;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：请求按「精确方块状态」放置一个方块。
 *
 * <p>原版放置只能产生 {@code getStateForPlacement} 根据点击面、邻居、玩家朝向推导出来的状态。
 * 蘑菇柄（{@code mushroom_stem}）的 {@code up/down/north/east/south/west} 六个连接位、
 * 藤蔓的附着面这类状态无法由原版放置上下文产生，只能由服务端直接落状态。
 * 数据包放置模式（{@code placeUsePacket}）下客户端又不做本地预测，于是这些方块永远只会被
 * 放成默认状态——这就是「投影里蘑菇柄全是错状态」的根因。</p>
 *
 * <p>方块用注册名 + 属性字符串传输，而不是方块状态数字 id：状态 id 由各端自己按方块属性顺序
 * 推导，模组方块在两端不一致时会整体错位，注册名则不会。服务端解析不出来就拒绝，客户端会退回
 * 原版放置。</p>
 *
 * @param pos        要放置到的方块坐标（投影里那一格）
 * @param block      方块注册名，例如 {@code minecraft:mushroom_stem}
 * @param properties 属性串，形如 {@code down=true,east=true,north=false,...}，无属性时为空串
 */
public record ExactPlacePayload(BlockPos pos, String block, String properties)
        implements CustomPacketPayload {

    public static final Type<ExactPlacePayload> TYPE =
            new Type<>(PrinterNetworking.EXACT_PLACE_CHANNEL);

    public static final StreamCodec<FriendlyByteBuf, ExactPlacePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeUtf(payload.block);
                buf.writeUtf(payload.properties);
            },
            buf -> new ExactPlacePayload(buf.readBlockPos(), buf.readUtf(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
