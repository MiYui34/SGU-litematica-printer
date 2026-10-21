package me.aleksilassila.litematica.printer.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 服务端侧配置。
 *
 * <p>服务端没有 MaLiLib，因此这里用一份独立的 JSON，和客户端的 {@code config/} 完全解耦：
 * 服主只需要编辑 {@code config/litematica-printer-server.json}，重启（或重连）即可生效。</p>
 */
public final class ServerPrinterConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ServerPrinterConfig instance;

    /** 是否允许远程开容器。关掉后客户端会退回原版行为（远处容器打不开）。 */
    public boolean remoteContainer = true;

    /** 允许远程开的距离上限（方块）。只对同维度生效；跨维度不检查距离。 */
    public int remoteContainerMaxRange = 128;

    /** 是否回发放置/破坏结果给客户端。 */
    public boolean interactionAck = true;

    /** 等待区块加载的最长 tick 数，超时就回报「暂不可用」。 */
    public int chunkLoadTimeoutTicks = 100;

    public static ServerPrinterConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    private static ServerPrinterConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                ServerPrinterConfig loaded = GSON.fromJson(reader, ServerPrinterConfig.class);
                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (IOException | RuntimeException ignored) {
                // 配置坏了就让默认值兜底，不影响服务端启动。
            }
        }
        ServerPrinterConfig defaults = new ServerPrinterConfig();
        defaults.save();
        return defaults;
    }

    private void save() {
        try {
            Files.createDirectories(configPath().getParent());
            try (Writer writer = Files.newBufferedWriter(configPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
            // 写不进去也不该拦住服务端。
        }
    }

    private void clamp() {
        this.remoteContainerMaxRange = Math.max(1, Math.min(this.remoteContainerMaxRange, 2048));
        this.chunkLoadTimeoutTicks = Math.max(1, Math.min(this.chunkLoadTimeoutTicks, 600));
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("litematica-printer-server.json");
    }
}
