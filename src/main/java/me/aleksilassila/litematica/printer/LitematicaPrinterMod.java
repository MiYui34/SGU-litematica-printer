package me.aleksilassila.litematica.printer;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import me.aleksilassila.litematica.printer.network.server.ServerExactPlacement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 模组主入口（客户端与服务端共用）。
 *
 * <p>专用服务端只会加载 {@code main} 入口：这里只做两端都必须注册的事（自定义通道的类型表）
 * 以及服务端专属的收包器与握手。客户端专属逻辑全部在 {@link LitematicaPrinterClient}。</p>
 */
public class LitematicaPrinterMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PrinterNetworking.initCommon();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            ServerExactPlacement.init();
        }
    }
}
