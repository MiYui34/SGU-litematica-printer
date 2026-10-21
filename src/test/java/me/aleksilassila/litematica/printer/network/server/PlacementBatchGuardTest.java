package me.aleksilassila.litematica.printer.network.server;

import me.aleksilassila.litematica.printer.network.payload.GuardedUseItemOnPayload;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementBatchGuardTest {
    private static final BlockPos TARGET = new BlockPos(1, 2, 3);
    private final PlacementBatchGuard guard = new PlacementBatchGuard();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private GuardedUseItemOnPayload request(int sequence, int batch, BlockState before) {
        return new GuardedUseItemOnPayload(
                new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(TARGET), Direction.UP, TARGET, false), sequence),
                batch, TARGET, before, before, new ItemStack(Items.STONE), 0);
    }

    private boolean begin(GuardedUseItemOnPayload request, BlockState current) {
        return this.guard.begin(request, Level.OVERWORLD, current, current,
                new ItemStack(Items.STONE), 0, 10L);
    }

    @Test
    void secondAirClickIsRejectedAfterFirstPlacementSoItCannotPlaceBesideTarget() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertTrue(this.begin(this.request(1, 1, air), air));
        this.guard.complete(stone, stone);

        assertFalse(this.begin(this.request(2, 2, air), stone));
    }

    @Test
    void changedSupportBlockRejectsAPlannedPlacement() {
        BlockState air = Blocks.AIR.defaultBlockState();
        var request = new GuardedUseItemOnPayload(
                new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(TARGET), Direction.UP, TARGET.below(), false), 1),
                1, TARGET, air, Blocks.STONE.defaultBlockState(), new ItemStack(Items.STONE), 0);
        assertFalse(this.guard.begin(request, Level.OVERWORLD,
                air, Blocks.DIRT.defaultBlockState(), new ItemStack(Items.STONE), 0, 10L));
    }

    @Test
    void serverHandAndSlotMustMatchClientSnapshot() {
        BlockState air = Blocks.AIR.defaultBlockState();
        assertFalse(this.guard.begin(this.request(1, 1, air), Level.OVERWORLD,
                air, air, new ItemStack(Items.DIRT), 0, 10L));
        assertFalse(this.guard.begin(this.request(2, 2, air), Level.OVERWORLD,
                air, air, new ItemStack(Items.STONE), 1, 10L));
    }

    @Test
    void intentionalRepeatedClicksContinueFromThePreviousServerState() {
        BlockState before = Blocks.REPEATER.defaultBlockState();
        BlockState after = before.setValue(RepeaterBlock.DELAY, 2);
        assertTrue(this.begin(this.request(1, 1, before), before));
        this.guard.complete(after, after);
        assertTrue(this.begin(this.request(2, 1, before), after));
        BlockState next = before.setValue(RepeaterBlock.DELAY, 3);
        this.guard.complete(next, next);
        assertTrue(this.begin(this.request(3, 1, before), next));
    }

    @Test
    void rejectedFirstClickAlsoRejectsTheRemainingRepeatedClicks() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertFalse(this.begin(this.request(1, 1, air), stone));
        assertFalse(this.begin(this.request(2, 1, air), stone));
    }

    @Test
    void duplicateSequenceAndWorldChangesBetweenRepeatsAreRejected() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertTrue(this.begin(this.request(1, 1, air), air));
        this.guard.complete(stone, stone);
        assertFalse(this.begin(this.request(1, 1, air), stone));
        assertTrue(this.begin(this.request(2, 2, stone), stone));
        this.guard.complete(stone, stone);
        assertFalse(this.begin(this.request(3, 2, stone), Blocks.DIRT.defaultBlockState()));
    }

    @Test
    void changingDimensionAllowsTheNewClientLevelToRestartItsSequence() {
        BlockState air = Blocks.AIR.defaultBlockState();
        assertTrue(this.begin(this.request(40, 40, air), air));
        this.guard.complete(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState());

        assertTrue(this.guard.begin(this.request(1, 1, air), Level.NETHER,
                air, air, new ItemStack(Items.STONE), 0, 10L));
        this.guard.complete(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState());
        assertFalse(this.guard.begin(this.request(2, 40, air), Level.NETHER,
                Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), new ItemStack(Items.STONE), 0, 10L));
    }
}
