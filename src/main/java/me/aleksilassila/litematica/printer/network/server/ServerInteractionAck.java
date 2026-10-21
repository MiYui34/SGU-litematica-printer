package me.aleksilassila.litematica.printer.network.server;

import me.aleksilassila.litematica.printer.network.payload.InteractionAckPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/**
 * 服务端一侧的回执发送器：把一次交互的服务端判定结果告诉装了本模组的客户端。
 *
 * <p>只对「客户端确实登记了这条通道」的玩家发（{@code canSend}），所以原版客户端、
 * 以及没装服务端部分时都不会多出任何流量。</p>
 */
public final class ServerInteractionAck {

    private ServerInteractionAck() {
    }

    /**
     * 放置/使用物品的结果。
     *
     * @param result {@code ServerPlayerGameMode#useItemOn} 的返回值；{@code FAIL} 视为服务端拒绝
     */
    public static void placement(ServerPlayer player, BlockPos pos, InteractionResult result) {
        send(player, pos, InteractionAckPayload.KIND_PLACE, result != InteractionResult.FAIL);
    }

    /**
     * 破坏方块的结果。
     *
     * @param destroyed 方块是否真的被破坏
     */
    public static void breaking(ServerPlayer player, BlockPos pos, boolean destroyed) {
        send(player, pos, InteractionAckPayload.KIND_BREAK, destroyed);
    }

    private static void send(ServerPlayer player, BlockPos pos, byte kind, boolean accepted) {
        if (player == null || pos == null) {
            return;
        }
        if (!ServerPlayNetworking.canSend(player, InteractionAckPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, new InteractionAckPayload(pos.immutable(), kind, accepted));
    }
}
