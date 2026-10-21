package me.aleksilassila.litematica.printer.handler;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintConfirmationTrackerTest {
    private static final BlockPos POS = new BlockPos(1, 2, 3);

    private final PrintConfirmationTracker tracker = new PrintConfirmationTracker();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void localObservationCannotResolvePendingPlacement() {
        this.tracker.track(POS, Blocks.STONE.defaultBlockState(), 10L, 80);

        assertTrue(this.tracker.expire(20L, 8).isEmpty());
        assertTrue(this.tracker.isPending(POS));
    }

    @Test
    void authoritativeMismatchResolvesAsMismatch() {
        this.tracker.track(POS, Blocks.STONE.defaultBlockState(), 10L, 80);

        assertEquals(
                PrintConfirmationTracker.Resolution.MISMATCHED,
                this.tracker.resolve(POS, Blocks.DIRT.defaultBlockState())
        );
        assertFalse(this.tracker.isPending(POS));
    }

    @Test
    void timeoutIsReportedOnceAfterDeadline() {
        this.tracker.track(POS, Blocks.STONE.defaultBlockState(), 10L, 80);

        assertTrue(this.tracker.expire(90L, 8).isEmpty());
        assertEquals(
                List.of(new PrintConfirmationTracker.ExpiredPlacement(POS, Blocks.STONE.defaultBlockState())),
                this.tracker.expire(91L, 8)
        );
        assertTrue(this.tracker.expire(92L, 8).isEmpty());
    }

    @Test
    void duplicateAuthoritativeDeliveryDoesNotResolveTwice() {
        this.tracker.track(POS, Blocks.STONE.defaultBlockState(), 10L, 80);

        assertEquals(
                PrintConfirmationTracker.Resolution.MATCHED,
                this.tracker.resolve(POS, Blocks.STONE.defaultBlockState())
        );
        assertEquals(
                PrintConfirmationTracker.Resolution.NOT_TRACKED,
                this.tracker.resolve(POS, Blocks.STONE.defaultBlockState())
        );
    }

    @Test
    void staleNeighbourUpdateCannotReleaseGuardedPlacement() {
        this.tracker.trackGuarded(POS, Blocks.STONE.defaultBlockState(), 10L, 80, 42);

        assertEquals(PrintConfirmationTracker.Resolution.NOT_TRACKED,
                this.tracker.resolve(POS, Blocks.AIR.defaultBlockState()));
        assertTrue(this.tracker.isPending(POS));
        assertEquals(PrintConfirmationTracker.Resolution.MATCHED,
                this.tracker.resolveGuarded(POS, 42, Blocks.STONE.defaultBlockState()));
        assertFalse(this.tracker.isPending(POS));
    }

    @Test
    void onlyLastClickInRepeatedActionCanResolveTheTarget() {
        this.tracker.trackGuarded(POS, Blocks.STONE.defaultBlockState(), 10L, 80, 41);
        this.tracker.trackGuarded(POS, Blocks.STONE.defaultBlockState(), 10L, 80, 42);
        // The executor records its sent action after the packet interception registered the sequence.
        this.tracker.track(POS, Blocks.STONE.defaultBlockState(), 10L, 80);

        assertEquals(PrintConfirmationTracker.Resolution.NOT_TRACKED,
                this.tracker.resolveGuarded(POS, 41, Blocks.DIRT.defaultBlockState()));
        assertTrue(this.tracker.isGuarded(POS));
        assertEquals(PrintConfirmationTracker.Resolution.MATCHED,
                this.tracker.resolveGuarded(POS, 42, Blocks.STONE.defaultBlockState()));
        assertEquals(PrintConfirmationTracker.Resolution.NOT_TRACKED,
                this.tracker.resolveGuarded(POS, 42, Blocks.STONE.defaultBlockState()));
    }

    @Test
    void guardedTimeoutAllowsRecoveryAndDoesNotAcceptAnOldReceiptForARetry() {
        this.tracker.trackGuarded(POS, Blocks.STONE.defaultBlockState(), 10L, 20, 41);
        assertEquals(1, this.tracker.expire(31L, 8).size());
        this.tracker.trackGuarded(POS, Blocks.STONE.defaultBlockState(), 32L, 20, 42);

        assertEquals(PrintConfirmationTracker.Resolution.NOT_TRACKED,
                this.tracker.resolveGuarded(POS, 41, Blocks.AIR.defaultBlockState()));
        assertTrue(this.tracker.isPending(POS));
    }

    @Test
    void receiptExtensionNeverShortensExistingDeadline() {
        this.tracker.track(POS, Blocks.STONE.defaultBlockState(), 10L, 80);
        assertTrue(this.tracker.extend(POS, 20L, 10));
        assertTrue(this.tracker.expire(31L, 8).isEmpty());
        assertTrue(this.tracker.isPending(POS));
    }
}
