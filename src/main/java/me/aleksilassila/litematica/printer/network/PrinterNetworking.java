package me.aleksilassila.litematica.printer.network;

import me.aleksilassila.litematica.printer.network.client.InteractionAckClient;
import me.aleksilassila.litematica.printer.network.payload.InteractionAckPayload;
import me.aleksilassila.litematica.printer.utils.minecraft.IdentifierUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
    }

    /** 客户端入口调用一次。 */
    public static void initClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        ClientPlayNetworking.registerGlobalReceiver(
                InteractionAckPayload.TYPE, InteractionAckClient::receive);
    }

    /** 当前服务端是否装了本模组的服务端部分（能接收回执）。 */
    public static boolean serverSupportsAcks() {
        if (!clientInitialized) {
            return false;
        }
        try {
            return ClientPlayNetworking.canSend(InteractionAckPayload.TYPE);
        } catch (RuntimeException notConnected) {
            return false;
        }
    }
}
