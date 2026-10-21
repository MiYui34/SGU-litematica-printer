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
 * <p>被拒绝时：把这一格踢进冷却并让扫描缓存失效 —— 服务端根本没放上去（越权、保护区、反作弊拦截、
 * 距离校验失败等），继续按原节奏重试只会白刷包。</p>
 *
 * <p>被接受时：延长这一格的「等待确认」窗口。点击被接受只说明服务端跑完了 {@code useItemOn}，
 * 权威方块状态仍要等区块更新；延长窗口可以避免本地世界还没收到更新就再点一次同一格
 * （那一下会点到刚放好的方块上，把门、拉杆、箱子之类的可交互方块切换成错误状态）。</p>
 */
public final class InteractionAckClient {

    private InteractionAckClient() {
    }

    public static void receive(InteractionAckPayload payload, ClientPlayNetworking.Context context) {
        if (payload.kind() != InteractionAckPayload.KIND_PLACE) {
            return;
        }
        Minecraft client = context.client();
        if (payload.accepted()) {
            client.execute(() -> onAcceptedPlacement(client, payload.pos()));
        } else {
            client.execute(() -> onRejectedPlacement(client, payload.pos()));
        }
    }

    private static void onAcceptedPlacement(Minecraft client, BlockPos pos) {
        PrinterRuntime runtime = runtimeOrNull(client);
        if (runtime == null) {
            return;
        }
        if (!runtime.hudStats().isPrintPlacementPending(pos) || runtime.hudStats().isGuardedPrintPlacement(pos)) {
            return;
        }
        runtime.hudStats().extendPrintPlacement(pos);
    }

    private static void onRejectedPlacement(Minecraft client, BlockPos pos) {
        PrinterRuntime runtime = runtimeOrNull(client);
        if (runtime == null) {
            return;
        }
        ClientLevel level = client.level;
        if (level == null || !runtime.hudStats().isPrintPlacementPending(pos)
                || runtime.hudStats().isGuardedPrintPlacement(pos)) {
            return;
        }
        runtime.hudStats().recordFailure(HudStatsManager.Mode.PRINT, HudStatus.INTERACTION_REJECTED);
        runtime.scanEngine().invalidate(pos);
        runtime.cooldownUtils().setCooldown(
                level, PrintHandler.NAME, pos, Math.max(1, ConfigUtils.getPlaceCooldown()));
    }

    private static PrinterRuntime runtimeOrNull(Minecraft client) {
        if (client.level == null) {
            return null;
        }
        try {
            return RuntimeAccess.get();
        } catch (IllegalStateException notInstalled) {
            return null;
        }
    }
}
