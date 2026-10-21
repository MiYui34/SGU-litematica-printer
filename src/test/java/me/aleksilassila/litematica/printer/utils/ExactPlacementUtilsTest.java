package me.aleksilassila.litematica.printer.utils;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactPlacementUtilsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void mushroomStemConnectionsAreEligible() {
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
        assertTrue(stem.getBlock() instanceof HugeMushroomBlock);
        assertTrue(ExactPlacementUtils.isEligible(stem));
    }

    @Test
    void plainBlocksAreNotEligible() {
        assertFalse(ExactPlacementUtils.isEligible(Blocks.STONE.defaultBlockState()));
        assertFalse(ExactPlacementUtils.isEligible(Blocks.OAK_PLANKS.defaultBlockState()));
        // 有 BlockEntity 的方块由原版容器逻辑处理，不用精确通道。
        assertFalse(ExactPlacementUtils.isEligible(Blocks.CHEST.defaultBlockState()));
    }

    @Test
    void propertiesRoundTripThroughTheNameValueString() {
        BlockState state = Blocks.MUSHROOM_STEM.defaultBlockState()
                .setValue(HugeMushroomBlock.NORTH, false)
                .setValue(HugeMushroomBlock.SOUTH, true)
                .setValue(HugeMushroomBlock.WEST, false);
        String encoded = ExactPlacementUtils.propertiesOf(state);
        assertEquals("down=true,east=true,north=false,south=true,up=true,west=false", encoded);
    }

    @Test
    void statelessBlocksEncodeToEmptyString() {
        assertEquals("", ExactPlacementUtils.propertiesOf(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void waterloggedBlocksKeepTheirProperties() {
        BlockState state = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, false);
        assertEquals(
                "facing=north,half=bottom,shape=straight,waterlogged=false",
                ExactPlacementUtils.propertiesOf(state));
    }
}
