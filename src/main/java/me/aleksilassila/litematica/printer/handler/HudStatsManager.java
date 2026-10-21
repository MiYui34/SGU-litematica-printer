package me.aleksilassila.litematica.printer.handler;

import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;
import me.aleksilassila.litematica.printer.printer.RttReplayController;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

public final class HudStatsManager implements RuntimeComponent {
    private static final long RATE_WINDOW_NANOS = 1_000_000_000L;
    private static final int DEFAULT_CONFIRM_TIMEOUT_TICKS = 10;
    /**
     * Packet placement never applies a local prediction, so the client world stays stale until the
     * server echoes the block change. Wait at least a second before declaring a placement lost;
     * otherwise a laggy server turns every confirmation into a duplicate click.
     */
    private static final int PACKET_MODE_CONFIRM_FLOOR_TICKS = 20;
    private static final int RTT_CONFIRM_SAFETY_TICKS = 2;
    private static final int FALLBACK_CONFIRM_CHECKS_PER_MODE = 8;

    private final Minecraft client;
    private final LongSupplier tickClock;
    private final RttReplayController rttReplayController;
    private final EnumMap<Mode, ModeStats> stats = new EnumMap<>(Mode.class);
    private final PrintConfirmationTracker printConfirmationTracker = new PrintConfirmationTracker();

    private long lastFallbackFlushTick = Long.MIN_VALUE;
    private int diagnosticLogBudget = 200;

    public HudStatsManager(
            Minecraft client,
            LongSupplier tickClock,
            RttReplayController rttReplayController
    ) {
        this.client = client;
        this.tickClock = tickClock;
        this.rttReplayController = rttReplayController;
        for (Mode mode : Mode.values()) {
            this.stats.put(mode, new ModeStats());
        }
    }

    public static HudStatsManager getRuntime() {
        return RuntimeAccess.get().hudStats();
    }

    public void resetAll() {
        this.printConfirmationTracker.clear();
        for (Mode mode : Mode.values()) {
            this.resetMode(mode);
        }
    }

    @Override public void onEpochChanged(RuntimeEvent.EpochChanged event) { this.resetAll(); }

    public void resetMode(Mode mode) {
        if (mode == Mode.PRINT) {
            this.printConfirmationTracker.clear();
        }
        this.stats.get(mode).reset();
    }

    public void recordRateUnit(Mode mode, int count) {
        if (count <= 0) {
            return;
        }
        this.stats.get(mode).recordRateUnit(count);
    }

    public void recordFailure(Mode mode, String reason) {
        this.stats.get(mode).recordFailure(reason);
    }

    public void recordDeferred(Mode mode, String reason) {
        this.stats.get(mode).recordDeferred(reason);
    }

    public void recordStatus(Mode mode, String reason) {
        this.stats.get(mode).recordStatus(reason);
    }

    public void trackExpectedBlockState(Mode mode, BlockPos pos, BlockState expectedState) {
        if (mode != Mode.PRINT || pos == null || expectedState == null) {
            return;
        }
        long now = this.tickClock.getAsLong();
        this.printConfirmationTracker.track(pos, expectedState, now, this.getConfirmationTimeoutTicks());
    }

    public boolean isPrintPlacementPending(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        return this.printConfirmationTracker.isPending(pos);
    }

    public boolean isGuardedPrintPlacement(BlockPos pos) {
        return pos != null && this.printConfirmationTracker.isGuarded(pos);
    }

    public void trackGuardedPlacement(BlockPos pos, BlockState expectedState, int sequence) {
        this.printConfirmationTracker.trackGuarded(
                pos, expectedState, this.tickClock.getAsLong(), this.getConfirmationTimeoutTicks(), sequence);
    }

    public void confirmGuardedPlacement(BlockPos pos, int sequence, BlockState state, boolean processed) {
        BlockState expectedState = this.printConfirmationTracker.expectedState(pos);
        PrintConfirmationTracker.Resolution resolution = this.printConfirmationTracker.resolveGuarded(pos, sequence, state);
        if (resolution == PrintConfirmationTracker.Resolution.NOT_TRACKED || this.client.level == null) {
            return;
        }
        RuntimeAccess.get().scanEngine().invalidate(pos);
        if (resolution == PrintConfirmationTracker.Resolution.MATCHED) {
            this.stats.get(Mode.PRINT).recordConfirmedUnit(this.tickClock.getAsLong(), 1);
        } else {
            this.logDiagnostic("sequenced placement {} at {}: world has {}, expected {} (processed={})",
                    sequence, pos, state, expectedState, processed);
            this.recordFailure(Mode.PRINT, HudStatus.INTERACTION_REJECTED);
            RuntimeAccess.get().cooldownUtils().setCooldown(
                    this.client.level, "print", pos,
                    Math.max(1, me.aleksilassila.litematica.printer.utils.ConfigUtils.getPlaceCooldown()));
        }
    }

    /**
     * Keeps an in-flight placement tracked for one more timeout window. The server only
     * acknowledges that it accepted the click; the authoritative block change still has to arrive
     * before the position may be printed again.
     */
    public void extendPrintPlacement(BlockPos pos) {
        if (pos == null) {
            return;
        }
        this.printConfirmationTracker.extend(pos, this.tickClock.getAsLong(), this.getConfirmationTimeoutTicks());
    }

    public void tick() {
        long now = this.tickClock.getAsLong();
        if (this.lastFallbackFlushTick == now) {
            return;
        }
        this.lastFallbackFlushTick = now;
        this.flushConfirmedActions(now, FALLBACK_CONFIRM_CHECKS_PER_MODE);
    }

    public void confirmBlockUpdate(BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (this.client.level == null) {
            return;
        }
        this.confirmBlockUpdate(pos, this.client.level.getBlockState(pos));
    }

    public void confirmBlockUpdate(BlockPos pos, BlockState authoritativeState) {
        if (pos == null || authoritativeState == null) {
            return;
        }
        long now = this.tickClock.getAsLong();

        BlockState expectedState = this.printConfirmationTracker.expectedState(pos);
        PrintConfirmationTracker.Resolution printResolution =
                this.printConfirmationTracker.resolve(pos, authoritativeState);
        if (printResolution == PrintConfirmationTracker.Resolution.MATCHED) {
            this.stats.get(Mode.PRINT).recordConfirmedUnit(now, 1);
        } else if (printResolution == PrintConfirmationTracker.Resolution.MISMATCHED
                && this.client.level != null) {
            this.logDiagnostic("placement landed as {} at {} (expected {})",
                    authoritativeState, pos, expectedState);
            RuntimeAccess.get().cooldownUtils().setCooldown(
                    this.client.level, "print", pos,
                    Math.max(1, me.aleksilassila.litematica.printer.utils.ConfigUtils.getPlaceCooldown()));
        }


    }

    public Snapshot snapshot(Mode mode) {
        long now = this.tickClock.getAsLong();
        return this.stats.get(mode).snapshot(now);
    }

    static int confirmationTimeoutTicksFor(int rttTicks) {
        return Math.max(
                DEFAULT_CONFIRM_TIMEOUT_TICKS,
                Math.max(0, rttTicks) + RTT_CONFIRM_SAFETY_TICKS
        );
    }

    private int getConfirmationTimeoutTicks() {
        int base = confirmationTimeoutTicksFor(
                this.rttReplayController == null
                        ? 0
                        : this.rttReplayController.getExtraIntervalTicks(100)
        );
        return me.aleksilassila.litematica.printer.config.Configs.Placement.PRINT_USE_PACKET.getBooleanValue()
                ? Math.max(base, PACKET_MODE_CONFIRM_FLOOR_TICKS)
                : base;
    }

    private void flushConfirmedActions(long now, int maxChecksPerMode) {
        if (this.client.level == null) {
            return;
        }
        flushExpiredPrintPlacements(this.client, now, maxChecksPerMode);
    }

    private void flushExpiredPrintPlacements(Minecraft client, long now, int maxChecks) {
        List<PrintConfirmationTracker.ExpiredPlacement> expired =
                this.printConfirmationTracker.expire(now, maxChecks);
        for (PrintConfirmationTracker.ExpiredPlacement placement : expired) {
            BlockPos pos = placement.pos();
            this.logDiagnostic("placement never confirmed at {}: expected {}, world has {}",
                    pos, placement.expectedState(), client.level.getBlockState(pos));
            RuntimeAccess.get().scanEngine().invalidate(pos);
            // An expired entry means no block change ever arrived. Re-clicking immediately would
            // hit a block the server may already have placed (toggling doors, levers, chests and
            // other interactive blocks), so keep the position on cooldown instead of clearing it.
            this.recordFailure(Mode.PRINT, HudStatus.WAITING_SERVER_CONFIRM);
            RuntimeAccess.get().cooldownUtils().setCooldown(
                    client.level, "print", pos,
                    Math.max(1, me.aleksilassila.litematica.printer.utils.ConfigUtils.getPlaceCooldown()));
        }
    }

    /**
     * Placement mismatches are logged so a report can be traced back to the exact block and the
     * exact state the server ended up with. The budget keeps a broken print session from flooding
     * the game log.
     */
    private void logDiagnostic(String message, Object... args) {
        if (this.diagnosticLogBudget <= 0) {
            return;
        }
        this.diagnosticLogBudget--;
        me.aleksilassila.litematica.printer.Reference.LOGGER.warn("[print] " + message, args);
        if (this.diagnosticLogBudget == 0) {
            me.aleksilassila.litematica.printer.Reference.LOGGER.warn(
                    "[print] further placement diagnostics suppressed for this session");
        }
    }

    public enum Mode {
        TOTAL,
        PRINT
    }

    public record Snapshot(
            long finished,
            long total,
            double progress,
            double ratePerSecond,
            double completedRatePerSecond,
            double failuresPerSecond,
            double deferredPerSecond,
            long lifetimeUnits,
            long lifetimeFailures,
            long lifetimeDeferred,
            String lastReason
    ) {
    }

    private static final class ModeStats {
        private final RollingCounter rateCounter = new RollingCounter();
        private final RollingCounter completedCounter = new RollingCounter();
        private final RollingCounter failureCounter = new RollingCounter();
        private final RollingCounter deferredCounter = new RollingCounter();

        private long finished;
        private long total;
        private double progress;
        private long lifetimeUnits;
        private long lifetimeFailures;
        private long lifetimeDeferred;
        private String lastReason = HudStatus.IDLE;

        private void reset() {
            this.finished = 0;
            this.total = 0;
            this.progress = 0.0D;
            this.lifetimeUnits = 0;
            this.lifetimeFailures = 0;
            this.lifetimeDeferred = 0;
            this.lastReason = HudStatus.IDLE;
            this.rateCounter.reset();
            this.completedCounter.reset();
            this.failureCounter.reset();
            this.deferredCounter.reset();
        }

        private void recordRateUnit(int count) {
            this.total += count;
            this.rateCounter.add(count);
            this.lifetimeUnits += count;
            this.updateProgress();
            this.lastReason = HudStatus.RUNNING;
        }

        private void recordConfirmedUnit(long tick, int count) {
            this.finished += count;
            this.completedCounter.add(count);
            this.updateProgress();
            this.lastReason = HudStatus.RUNNING;
        }

        private void recordFailure(String reason) {
            this.failureCounter.add(1);
            this.lifetimeFailures++;
            this.lastReason = normalizeReason(reason);
        }

        private void recordDeferred(String reason) {
            this.deferredCounter.add(1);
            this.lifetimeDeferred++;
            this.lastReason = normalizeReason(reason);
        }

        private void recordStatus(String reason) {
            this.lastReason = normalizeReason(reason);
        }

        private Snapshot snapshot(long now) {
            return new Snapshot(
                    this.finished,
                    this.total,
                    this.progress,
                    this.rateCounter.sumRecent(),
                    this.completedCounter.sumRecent(),
                    this.failureCounter.sumRecent(),
                    this.deferredCounter.sumRecent(),
                    this.lifetimeUnits,
                    this.lifetimeFailures,
                    this.lifetimeDeferred,
                    this.lastReason
            );
        }

        private void updateProgress() {
            this.progress = this.total > 0 ? (double) this.finished / (double) this.total : 0.0D;
        }

        private static String normalizeReason(String reason) {
            return reason == null || reason.isBlank() ? HudStatus.RUNNING : reason;
        }
    }


    private static final class RollingCounter {
        private static final int MAX_EVENTS = 32768;
        private final long[] timestamps = new long[MAX_EVENTS];
        private final int[] values = new int[MAX_EVENTS];
        private int head;
        private int size;
        private int total;

        private RollingCounter() {
            this.reset();
        }

        private void reset() {
            this.head = 0;
            this.size = 0;
            this.total = 0;
        }

        private void add(int delta) {
            long now = System.nanoTime();
            this.discardExpired(now);
            if (this.size >= MAX_EVENTS) {
                this.discardOldest();
            }
            int index = (this.head + this.size) % MAX_EVENTS;
            this.timestamps[index] = now;
            this.values[index] = delta;
            this.size++;
            this.total += delta;
        }

        private double sumRecent() {
            this.discardExpired(System.nanoTime());
            return this.total;
        }

        private void discardExpired(long now) {
            while (this.size > 0) {
                long timestamp = this.timestamps[this.head];
                if (now - timestamp < RATE_WINDOW_NANOS) {
                    break;
                }
                this.discardOldest();
            }
        }

        private void discardOldest() {
            if (this.size <= 0) {
                return;
            }
            this.total -= this.values[this.head];
            this.timestamps[this.head] = 0L;
            this.values[this.head] = 0;
            this.head = (this.head + 1) % MAX_EVENTS;
            this.size--;
        }
    }
}
