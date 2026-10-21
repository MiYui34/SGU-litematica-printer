package me.aleksilassila.litematica.printer.network.client;

import me.aleksilassila.litematica.printer.network.payload.OpenContainerRequestPayload;
import me.aleksilassila.litematica.printer.network.payload.OpenContainerResultPayload;
import me.aleksilassila.litematica.printer.network.payload.ServerHelloPayload;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端一侧的「请服务端代开容器」。
 *
 * <p>箱子追踪记下来的容器大多在几格、几十格开外，客户端自己右键会被服务端的距离校验拒掉；
 * 这条路把「打开」这个动作交给服务端做，客户端只是等着原版发来打开界面的包。</p>
 *
 * <p>只有服务端主动握手（{@link ServerHelloPayload}）之后才会启用，因此连着原版服务器时
 * 行为和以前完全一样。</p>
 */
public final class RemoteContainerClient {

    /** 服务端握手后再补充的有效距离；{@code 0} 表示服务端没开这个功能。 */
    private static volatile boolean available;
    private static volatile int maxRange;

    private static final Map<Integer, PendingRequest> PENDING = new HashMap<>();
    private static final long PENDING_TIMEOUT_MILLIS = 30_000L;
    private static int nextNonce = 1;

    private record PendingRequest(BlockPos pos, long createdAt) {
    }

    private RemoteContainerClient() {
    }

    public static void receiveHello(ServerHelloPayload payload, ClientPlayNetworking.Context context) {
        available = payload.remoteContainer();
        maxRange = payload.maxContainerRange();
        PENDING.clear();
    }

    public static void receiveResult(OpenContainerResultPayload payload, ClientPlayNetworking.Context context) {
        PendingRequest request = PENDING.remove(payload.nonce());
        BlockPos pos = request == null ? null : request.pos();
        if (pos == null) {
            return;
        }
        context.client().execute(() -> {
            try {
                RuntimeAccess.get().chestTrackerAdapter().onRemoteContainerResult(pos, payload.ok());
            } catch (IllegalStateException notInstalled) {
                // 运行时还没装好：这次取物本来也没开始，忽略即可。
            }
        });
    }

    public static boolean isAvailable() {
        if (!available) {
            return false;
        }
        try {
            return ClientPlayNetworking.canSend(OpenContainerRequestPayload.TYPE);
        } catch (RuntimeException notConnected) {
            return false;
        }
    }

    public static int maxRange() {
        return maxRange;
    }

    /**
     * 这一格是不是必须走服务端代开：区块没加载，或者玩家够不到（原版交互距离 4.5 格，
     * 这里留一点余量按 5 格算）。
     */
    public static boolean needsRemoteOpen(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return false;
        }
        if (!client.level.isLoaded(pos)) {
            return true;
        }
        return client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 25.0;
    }

    public static boolean request(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !isAvailable()) {
            return false;
        }
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(pending -> now - pending.createdAt() > PENDING_TIMEOUT_MILLIS);
        int nonce = nextNonce++;
        if (nextNonce <= 0) {
            nextNonce = 1;
        }
        PENDING.put(nonce, new PendingRequest(pos.immutable(), now));
        ClientPlayNetworking.send(new OpenContainerRequestPayload(
                client.level.dimension(), pos.immutable(), nonce));
        return true;
    }
}
