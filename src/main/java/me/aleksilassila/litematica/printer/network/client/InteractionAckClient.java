package me.aleksilassila.litematica.printer.network.client;

import me.aleksilassila.litematica.printer.handler.HudStatus;
import me.aleksilassila.litematica.printer.handler.HudStatsManager;
import me.aleksilassila.litematica.printer.handler.handlers.PrintHandler;
import me.aleksilassila.litematica.printer.network.payload.InteractionAckPayload;
import me.aleksilassila.litematica.printer.runtime.PrinterRuntime;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * 服务端回执的客户端处理。
 *
 * <p>只有「服务端明确拒绝一次我们正在等待确认的放置」才需要动作：那一格在本地看是空的、
 * 在服务端根本没放上去（越权、保护区、反作弊拦截等），继续按原节奏重试只会白刷包。
 * 处理办法是把这一格踢进冷却并让扫描缓存失效，等冷却结束再试一次。</p>
 *
 * <p>成功回执不做任何事：方块的权威状态最终仍以区块更新为准，提前「确认」反而会掩盖
 * 真正的不同步。</p>
 */
public final class InteractionAckClient {

    private InteractionAckClient() {
    }

    public static void receive(InteractionAckPayload payload, ClientPlayNetworking.Context context) {
        if (payload.accepted() || payload.kind() != InteractionAckPayload.KIND_PLACE) {
            return;
        }
        Minecraft client = context.client();
        client.execute(() -> onRejectedPlacement(client, payload.pos()));
    }

    private static void onRejectedPlacement(Minecraft client, BlockPos pos) {
        PrinterRuntime runtime;
        try {
            runtime = RuntimeAccess.get();
        } catch (IllegalStateException notInstalled) {
            return;
        }
        ClientLevel level = client.level;
        if (level == null || !runtime.hudStats().isPrintPlacementPending(pos)) {
            return;
        }
        runtime.hudStats().recordFailure(HudStatsManager.Mode.PRINT, HudStatus.INTERACTION_REJECTED);
        runtime.scanEngine().invalidate(pos);
        runtime.cooldownUtils().setCooldown(
                level, PrintHandler.NAME, pos, Math.max(1, ConfigUtils.getPlaceCooldown()));
    }
}
