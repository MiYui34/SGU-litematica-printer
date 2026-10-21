package me.aleksilassila.litematica.printer.network;

import me.aleksilassila.litematica.printer.network.client.InteractionAckClient;
import me.aleksilassila.litematica.printer.network.payload.InteractionAckPayload;
import me.aleksilassila.litematica.printer.network.payload.GuardedPlacementAckPayload;
import me.aleksilassila.litematica.printer.network.payload.GuardedUseItemOnPayload;
import me.aleksilassila.litematica.printer.network.server.ServerGuardedPlacement;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import me.aleksilassila.litematica.printer.utils.minecraft.IdentifierUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;

/**
 * 本模组自定义通道的注册中心。
 *
 * <p>拆成 {@link #initCommon()} 与 {@link #initClient()} 两部分：专用服务端只会执行 {@code main}
 * 入口，编码器（{@code PayloadTypeRegistry}）两端都要登记，而收包器是分端的。</p>
 */
public final class PrinterNetworking {

    public static final String NAMESPACE = "litematica-printer";

    /** 服务端 → 客户端：一次放置/破坏在服务端的处理结果。 */
    public static final Identifier INTERACTION_ACK_CHANNEL =
            IdentifierUtils.of(NAMESPACE, "interaction_ack");
    public static final Identifier GUARDED_USE_ITEM_ON_CHANNEL =
            IdentifierUtils.of(NAMESPACE, "guarded_use_item_on_v1");
    public static final Identifier GUARDED_PLACEMENT_ACK_CHANNEL =
            IdentifierUtils.of(NAMESPACE, "guarded_placement_ack_v1");

    private static boolean commonInitialized;
    private static boolean clientInitialized;

    private PrinterNetworking() {
    }

    /** 主入口（客户端与服务端共用）调用一次。 */
    public static void initCommon() {
        if (commonInitialized) {
            return;
        }
        commonInitialized = true;
        PayloadTypeRegistry.clientboundPlay().register(
                InteractionAckPayload.TYPE, InteractionAckPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                GuardedUseItemOnPayload.TYPE, GuardedUseItemOnPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                GuardedPlacementAckPayload.TYPE, GuardedPlacementAckPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GuardedUseItemOnPayload.TYPE, ServerGuardedPlacement::receive);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (connection, server) -> ServerGuardedPlacement.disconnect(connection));
    }

    /** 客户端入口调用一次。 */
    public static void initClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        ClientPlayNetworking.registerGlobalReceiver(
                InteractionAckPayload.TYPE, InteractionAckClient::receive);
        ClientPlayNetworking.registerGlobalReceiver(GuardedPlacementAckPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level != null) {
                        RuntimeAccess.get().hudStats().confirmGuardedPlacement(
                                payload.pos(), payload.sequence(), payload.state(), payload.processed());
                    }
                }));
    }

    /** 当前服务端是否支持带序号的校验放置与回执。 */
    public static boolean serverSupportsAcks() {
        if (!clientInitialized) {
            return false;
        }
        try {
            return ClientPlayNetworking.canSend(GuardedUseItemOnPayload.TYPE);
        } catch (RuntimeException notConnected) {
            return false;
        }
    }
}
