package me.aleksilassila.litematica.printer.network.server;

import me.aleksilassila.litematica.printer.network.payload.GuardedUseItemOnPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Per-connection state; intentional repeated clicks must continue the last accepted batch. */
final class PlacementBatchGuard {
    private static final int MAX_BATCH_CLICKS = 64;
    private static final int MAX_BATCH_AGE_TICKS = 40;
    private int lastSequence = -1;
    private int batchId = -1;
    private int clicks;
    private long lastTick;
    private ResourceKey<Level> sequenceDimension;
    private ResourceKey<Level> dimension;
    private BlockPos placementPos;
    private BlockPos hitPos;
    private BlockState targetState;
    private BlockState hitState;

    boolean begin(GuardedUseItemOnPayload request, ResourceKey<Level> dimension,
                  BlockState targetState, BlockState hitState, ItemStack heldStack,
                  int selectedSlot, long tick) {
        // A new ClientLevel (for example after changing dimensions) starts its sequence at zero.
        if (!dimension.equals(this.sequenceDimension)) {
            this.lastSequence = -1;
            this.batchId = -1;
            this.sequenceDimension = dimension;
        }
        int sequence = request.click().getSequence();
        BlockPos clicked = request.click().getHitResult().getBlockPos();
        boolean freshSequence = sequence > this.lastSequence;
        this.lastSequence = Math.max(this.lastSequence, sequence);
        boolean sameBatch = request.batchId() == this.batchId
                && dimension.equals(this.dimension)
                && request.placementPos().equals(this.placementPos)
                && clicked.equals(this.hitPos)
                && tick >= this.lastTick && tick - this.lastTick <= MAX_BATCH_AGE_TICKS
                && this.clicks < MAX_BATCH_CLICKS;
        boolean first = sequence == request.batchId();
        boolean valid = freshSequence && request.batchId() >= 0
                && request.click().getHand() == InteractionHand.MAIN_HAND
                && selectedSlot == request.selectedSlot()
                && ItemStack.isSameItemSameComponents(heldStack, request.heldStack())
                && (first
                    ? targetState.equals(request.beforeState()) && hitState.equals(request.hitState())
                    : sameBatch && targetState.equals(this.targetState) && hitState.equals(this.hitState));
        if (!valid) {
            this.batchId = -1;
            return false;
        }
        this.batchId = request.batchId();
        this.dimension = dimension;
        this.placementPos = request.placementPos();
        this.hitPos = clicked;
        this.clicks = first ? 1 : this.clicks + 1;
        this.lastTick = tick;
        return true;
    }

    void complete(BlockState targetState, BlockState hitState) {
        this.targetState = targetState;
        this.hitState = hitState;
    }
}
