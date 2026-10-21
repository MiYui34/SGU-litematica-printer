package me.aleksilassila.litematica.printer.network.server;

import me.aleksilassila.litematica.printer.network.payload.GuardedPlacementAckPayload;
import me.aleksilassila.litematica.printer.network.payload.GuardedUseItemOnPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Checks stale plans, then delegates every allowed click to the original vanilla packet handler. */
public final class ServerGuardedPlacement {
    private static final Map<ServerGamePacketListenerImpl, PlacementBatchGuard> SESSIONS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> HANDLING = ThreadLocal.withInitial(() -> false);

    private ServerGuardedPlacement() {
    }

    public static void disconnect(ServerGamePacketListenerImpl connection) {
        SESSIONS.remove(connection);
    }

    public static boolean isHandling() {
        return HANDLING.get();
    }

    public static void receive(GuardedUseItemOnPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        var level = player.level();
        BlockPos target = payload.placementPos();
        BlockPos hit = payload.click().getHitResult().getBlockPos();
        int sequence = payload.click().getSequence();
        if (sequence < 0) {
            return;
        }
        // Do not let metadata load arbitrary chunks or expose out-of-range world state.
        if (!level.hasChunkAt(target) || !level.hasChunkAt(hit)
                || !player.isWithinBlockInteractionRange(target, 1.0D)
                || !player.isWithinBlockInteractionRange(hit, 1.0D)
                || !(target.equals(hit)
                    || target.equals(hit.relative(payload.click().getHitResult().getDirection())))) {
            player.connection.ackBlockChangesUpTo(sequence);
            return;
        }
        PlacementBatchGuard guard = SESSIONS.computeIfAbsent(player.connection, ignored -> new PlacementBatchGuard());
        boolean processed = guard.begin(
                payload, level.dimension(), level.getBlockState(target), level.getBlockState(hit),
                player.getMainHandItem(), player.getInventory().getSelectedSlot(), level.getGameTime());
        if (processed) {
            try {
                HANDLING.set(true);
                player.connection.handleUseItemOn(payload.click());
            } finally {
                HANDLING.remove();
            }
            guard.complete(level.getBlockState(target), level.getBlockState(hit));
        } else {
            // Rejected predictions still need vanilla reconciliation, including the held stack.
            player.connection.ackBlockChangesUpTo(sequence);
            player.containerMenu.sendAllDataToRemote();
        }
        player.connection.send(new ClientboundBlockUpdatePacket(target, level.getBlockState(target)));
        if (!target.equals(hit)) {
            player.connection.send(new ClientboundBlockUpdatePacket(hit, level.getBlockState(hit)));
        }
        ServerPlayNetworking.send(player, new GuardedPlacementAckPayload(
                sequence, target, level.getBlockState(target), processed));
    }
}
