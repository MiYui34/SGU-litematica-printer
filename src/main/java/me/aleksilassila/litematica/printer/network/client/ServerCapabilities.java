package me.aleksilassila.litematica.printer.network.client;

import me.aleksilassila.litematica.printer.network.payload.ServerHelloPayload;

/**
 * 当前服务器声明支持的能力。断开连接时清空，避免换服后沿用上一台服务器的能力。
 */
public final class ServerCapabilities {

    private static volatile boolean exactPlacement;
    private static volatile boolean interactionAck;

    private ServerCapabilities() {
    }

    public static void apply(ServerHelloPayload payload) {
        exactPlacement = payload.exactPlacement();
        interactionAck = payload.interactionAck();
    }

    public static void reset() {
        exactPlacement = false;
        interactionAck = false;
    }

    public static boolean exactPlacement() {
        return exactPlacement;
    }

    public static boolean interactionAck() {
        return interactionAck;
    }
}
