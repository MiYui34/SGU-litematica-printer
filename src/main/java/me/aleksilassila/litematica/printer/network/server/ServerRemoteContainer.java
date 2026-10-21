package me.aleksilassila.litematica.printer.network.server;

import me.aleksilassila.litematica.printer.network.payload.OpenContainerRequestPayload;
import me.aleksilassila.litematica.printer.network.payload.OpenContainerResultPayload;
import me.aleksilassila.litematica.printer.network.payload.ServerHelloPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端侧的「远程开容器」实现。
 *
 * <p>做的事和玩家自己走到箱子前右键完全一样：加载区块 → 调用方块自己的使用逻辑
 * （{@code BlockState#useWithoutItem}）。原版随即发出打开界面的包，客户端于是拿到了
 * 一个真实的容器界面 —— 后续取物用的还是原版点击包，不需要另造一套物品同步协议。</p>
 *
 * <p>打开之后还有两件事必须由服务端兜住，否则界面下一刻就会被原版关掉：</p>
 * <ul>
 *   <li>玩家的 {@code ServerPlayer#tick} 每 tick 都用 {@code AbstractContainerMenu#stillValid}
 *       检查「容器还在不在手边」，人站在几十格外必然为 false；</li>
 *   <li>容器自己的 {@code Container#stillValidBlockEntity} 同样会拒绝远处的玩家。</li>
 * </ul>
 * <p>所以远程打开的会话会被登记在 {@link #SESSIONS} 里，上述两处检查对本模组打开的会话直接放行，
 * 会话在玩家关界面 / 掉线 / 方块消失时清除。</p>
 */
public final class ServerRemoteContainer {

    /** 票据保活时长：够把界面打开、再把候选容器扫一遍。 */
    private static final int TICKET_LIFETIME_TICKS = 200;

    private record Pending(ServerPlayer player, ServerLevel level, BlockPos pos, int nonce, long deadline) {
    }

    private record Session(ServerPlayer player, ServerLevel level, BlockPos pos, int nonce) {
    }

    private record Ticket(ServerLevel level, ChunkPos chunk, long expireTick) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Ticket> TICKETS = new ArrayList<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private ServerRemoteContainer() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(OpenContainerRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            server.execute(() -> handleRequest(server, player, payload));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendHello(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerClosed(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(ServerRemoteContainer::tick);
    }

    /** 该玩家的当前容器界面是不是本模组远程打开的（是则跳过原版的距离校验）。 */
    public static boolean hasRemoteSession(Player player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    /** 玩家关界面 / 掉线时清掉会话。 */
    public static void onPlayerClosed(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        PENDING.removeIf(pending -> pending.player == player);
        LAST_REQUEST.remove(player.getUUID());
    }

    /** 由 {@code ServerLevel#tick} 的 mixin 调用：确保界面开着时区块仍然加载，方块没了就收摊。 */
    public static void tickLevel(ServerLevel level) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.level != level) {
                continue;
            }
            if (!session.player.isAlive() || session.player.hasDisconnected()) {
                iterator.remove();
                continue;
            }
            if (level.getBlockState(session.pos).isAir()) {
                iterator.remove();
                reply(session.player, session.nonce, OpenContainerResultPayload.STATUS_UNAVAILABLE);
                continue;
            }
            keepChunkLoaded(level, session.pos);
        }
    }

    private static void sendHello(ServerPlayer player) {
        ServerPrinterConfig config = ServerPrinterConfig.get();
        if (!ServerPlayNetworking.canSend(player, ServerHelloPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, new ServerHelloPayload(
                config.remoteContainer, config.interactionAck, config.remoteContainerMaxRange));
    }

    private static void handleRequest(MinecraftServer server, ServerPlayer player,
                                      OpenContainerRequestPayload request) {
        ServerPrinterConfig config = ServerPrinterConfig.get();
        if (!config.remoteContainer) {
            reply(player, request.nonce(), OpenContainerResultPayload.STATUS_DISABLED);
            return;
        }
        ServerLevel level = server.getLevel(request.dimension());
        if (level == null || !level.isInWorldBounds(request.pos())) {
            reply(player, request.nonce(), OpenContainerResultPayload.STATUS_UNAVAILABLE);
            return;
        }
        if (level == player.level()
                && player.distanceToSqr(Vec3.atCenterOf(request.pos()))
                > (double) config.remoteContainerMaxRange * config.remoteContainerMaxRange) {
            reply(player, request.nonce(), OpenContainerResultPayload.STATUS_TOO_FAR);
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < 5L) {
            reply(player, request.nonce(), OpenContainerResultPayload.STATUS_UNAVAILABLE);
            return;
        }
        LAST_REQUEST.put(player.getUUID(), now);
        if (level.isLoaded(request.pos())) {
            open(player, level, request.pos(), request.nonce());
            return;
        }
        long deadline = now + config.chunkLoadTimeoutTicks;
        keepChunkLoaded(level, request.pos());
        PENDING.add(new Pending(player, level, request.pos().immutable(), request.nonce(), deadline));
    }

    private static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            Iterator<Pending> iterator = PENDING.iterator();
            while (iterator.hasNext()) {
                Pending pending = iterator.next();
                if (!pending.player.isAlive() || pending.player.hasDisconnected()) {
                    iterator.remove();
                    continue;
                }
                long now = pending.level.getGameTime();
                if (pending.level.isLoaded(pending.pos())) {
                    iterator.remove();
                    open(pending.player, pending.level, pending.pos(), pending.nonce());
                } else if (now > pending.deadline) {
                    iterator.remove();
                    reply(pending.player, pending.nonce(), OpenContainerResultPayload.STATUS_UNAVAILABLE);
                }
            }
        }
        if (!TICKETS.isEmpty()) {
            Iterator<Ticket> iterator = TICKETS.iterator();
            while (iterator.hasNext()) {
                Ticket ticket = iterator.next();
                if (ticket.level.getGameTime() < ticket.expireTick) {
                    continue;
                }
                ticket.level.getChunkSource().removeTicketWithRadius(
                        TicketType.UNKNOWN, ticket.chunk, 2);
                iterator.remove();
            }
        }
    }

    private static void keepChunkLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        long expire = level.getGameTime() + TICKET_LIFETIME_TICKS;
        for (int i = 0; i < TICKETS.size(); i++) {
            Ticket ticket = TICKETS.get(i);
            if (ticket.level == level && ticket.chunk.equals(chunk)) {
                if (ticket.expireTick < expire) {
                    TICKETS.set(i, new Ticket(level, chunk, expire));
                }
                return;
            }
        }
        level.getChunkSource().addTicketWithRadius(TicketType.UNKNOWN, chunk, 2);
        TICKETS.add(new Ticket(level, chunk, expire));
    }

    private static void open(ServerPlayer player, ServerLevel level, BlockPos pos, int nonce) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (state.isAir() || !(blockEntity instanceof Container)) {
            reply(player, nonce, OpenContainerResultPayload.STATUS_NOT_CONTAINER);
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        // 用 UP 面点击和玩家正对着箱子右键是同一件事；方块自己的 openMenu 会完成剩下的工作。
        InteractionResult result = state.useWithoutItem(level, player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        boolean opened = player.containerMenu != player.inventoryMenu;
        if (opened || result instanceof InteractionResult.Success) {
            SESSIONS.put(player.getUUID(), new Session(player, level, pos.immutable(), nonce));
            reply(player, nonce, OpenContainerResultPayload.STATUS_OK);
        } else {
            reply(player, nonce, OpenContainerResultPayload.STATUS_REJECTED);
        }
    }

    private static void reply(ServerPlayer player, int nonce, byte status) {
        if (player == null || player.hasDisconnected()) {
            return;
        }
        if (!ServerPlayNetworking.canSend(player, OpenContainerResultPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, new OpenContainerResultPayload(nonce, status));
    }
}
