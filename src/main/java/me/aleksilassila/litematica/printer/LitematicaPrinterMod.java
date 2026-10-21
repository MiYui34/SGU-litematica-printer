package me.aleksilassila.litematica.printer;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.fabricmc.api.ModInitializer;

/**
 * 模组主入口（客户端与服务端共用）。
 *
 * <p>专用服务端只加载 {@code main} 入口：这里只做两端都必须注册的事——自定义通道的类型表、
 * 服务端收包器与握手应答。客户端专属逻辑全部在 {@link LitematicaPrinterClient}。</p>
 */
public class LitematicaPrinterMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PrinterNetworking.initCommon();
    }
}
