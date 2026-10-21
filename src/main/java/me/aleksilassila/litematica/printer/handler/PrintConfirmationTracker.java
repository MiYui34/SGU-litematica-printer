package me.aleksilassila.litematica.printer.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PrintConfirmationTracker {
    private final Map<BlockPos, PendingBlockState> pendingStates = new LinkedHashMap<>();

    void track(BlockPos pos, BlockState expectedState, long sentTick, int timeoutTicks) {
        this.pendingStates.put(
                pos.immutable(),
                new PendingBlockState(expectedState, sentTick + timeoutTicks)
        );
    }

    boolean isPending(BlockPos pos) {
        return this.pendingStates.containsKey(pos);
    }

    /** Re-arms the timeout of an already tracked placement without changing its expected state. */
    boolean extend(BlockPos pos, long currentTick, int timeoutTicks) {
        PendingBlockState pending = this.pendingStates.get(pos);
        if (pending == null) {
            return false;
        }
        this.pendingStates.put(
                pos.immutable(),
                new PendingBlockState(pending.expectedState(), currentTick + timeoutTicks)
        );
        return true;
    }

    @Nullable
    BlockState expectedState(BlockPos pos) {
        PendingBlockState pending = this.pendingStates.get(pos);
        return pending == null ? null : pending.expectedState();
    }

    Resolution resolve(BlockPos pos, BlockState authoritativeState) {
        PendingBlockState pending = this.pendingStates.remove(pos);
        if (pending == null) {
            return Resolution.NOT_TRACKED;
        }
        return authoritativeState.equals(pending.expectedState())
                ? Resolution.MATCHED
                : Resolution.MISMATCHED;
    }

    List<ExpiredPlacement> expire(long currentTick, int maxChecks) {
        List<ExpiredPlacement> expired = new ArrayList<>();
        for (int checked = 0; checked < maxChecks && !this.pendingStates.isEmpty(); checked++) {
            Iterator<Map.Entry<BlockPos, PendingBlockState>> iterator =
                    this.pendingStates.entrySet().iterator();
            Map.Entry<BlockPos, PendingBlockState> entry = iterator.next();
            iterator.remove();
            if (currentTick > entry.getValue().expireTick()) {
                expired.add(new ExpiredPlacement(entry.getKey(), entry.getValue().expectedState()));
            } else {
                this.pendingStates.put(entry.getKey(), entry.getValue());
            }
        }
        return expired;
    }

    void clear() {
        this.pendingStates.clear();
    }

    record ExpiredPlacement(BlockPos pos, BlockState expectedState) {
    }

    enum Resolution {
        NOT_TRACKED,
        MATCHED,
        MISMATCHED
    }

    private record PendingBlockState(BlockState expectedState, long expireTick) {
    }
}
