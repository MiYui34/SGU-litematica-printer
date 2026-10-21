package me.aleksilassila.litematica.printer.network.client;

import me.aleksilassila.litematica.printer.network.payload.GuardedUseItemOnPayload;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** A scope covers exactly one queued action, including its intentional repeated clicks. */
public final class GuardedPlacementClient implements AutoCloseable {
    private static GuardedPlacementClient active;
    private final BlockPos pos;
    private final BlockState beforeState;
    private final BlockState hitState;
    private final BlockState expectedState;
    private final ItemStack heldStack;
    private final int selectedSlot;
    private int batchId = -1;
    private boolean sent;

    public GuardedPlacementClient(BlockPos pos, BlockState beforeState, BlockState hitState,
                                  BlockState expectedState, ItemStack heldStack, int selectedSlot) {
        this.pos = pos;
        this.beforeState = beforeState;
        this.hitState = hitState;
        this.expectedState = expectedState;
        this.heldStack = heldStack.copy();
        this.selectedSlot = selectedSlot;
        active = this;
    }

    public boolean wasSent() {
        return this.sent;
    }

    public static Packet<?> wrap(Packet<?> packet) {
        GuardedPlacementClient scope = active;
        if (scope == null || scope.pos == null
                || !(packet instanceof ServerboundUseItemOnPacket click)
                || !ClientPlayNetworking.canSend(GuardedUseItemOnPayload.TYPE)) {
            return packet;
        }
        int sequence = click.getSequence();
        if (scope.batchId < 0) {
            scope.batchId = sequence;
        }
        RuntimeAccess.get().hudStats().trackGuardedPlacement(scope.pos, scope.expectedState, sequence);
        scope.sent = true;
        return new ServerboundCustomPayloadPacket(new GuardedUseItemOnPayload(
                click, scope.batchId, scope.pos, scope.beforeState, scope.hitState,
                scope.heldStack, scope.selectedSlot));
    }

    @Override
    public void close() {
        active = null;
    }
}
