package me.aleksilassila.litematica.printer.network.client;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.network.payload.ExactPlacePayload;
import me.aleksilassila.litematica.printer.utils.ExactPlacementUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 客户端把「这一格应该是什么状态」发给服务端。
 *
 * <p>只有服务器声明支持、且客户端配置允许时才发；发送失败就返回 false，调用方退回原版放置，
 * 不会出现「以为放了其实没放」。</p>
 */
public final class ExactPlacementSender {

    private ExactPlacementSender() {
    }

    public static boolean isAvailable() {
        return ServerCapabilities.exactPlacement()
                && Configs.Placement.EXACT_PLACEMENT.getBooleanValue();
    }

    public static boolean send(BlockPos pos, BlockState state) {
        if (!isAvailable()) {
            return false;
        }
        try {
            if (!ClientPlayNetworking.canSend(ExactPlacePayload.TYPE)) {
                return false;
            }
            ClientPlayNetworking.send(new ExactPlacePayload(
                    pos.immutable(),
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    ExactPlacementUtils.propertiesOf(state)));
            return true;
        } catch (RuntimeException notConnected) {
            return false;
        }
    }
}
