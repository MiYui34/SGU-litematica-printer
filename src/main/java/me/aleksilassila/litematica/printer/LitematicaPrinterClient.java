package me.aleksilassila.litematica.printer;

import fi.dy.masa.malilib.event.InitializationHandler;
import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import me.aleksilassila.litematica.printer.runtime.PrinterRuntime;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * 客户端入口。
 *
 * <p>这里可以依赖 MaLiLib / Litematica 等纯客户端模组；专用服务端永远不会加载本类。</p>
 */
public class LitematicaPrinterClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PrinterNetworking.initClient();

        PrinterRuntime runtime = new PrinterRuntime();
        RuntimeAccess.install(runtime);
        ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
        ClientChunkEvents.CHUNK_LOAD.register(runtime.scanEngine()::invalidateChunk);
        ClientChunkEvents.CHUNK_UNLOAD.register(runtime.scanEngine()::invalidateChunk);
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
    }
}
