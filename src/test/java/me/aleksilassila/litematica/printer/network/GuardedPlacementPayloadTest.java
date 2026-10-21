package me.aleksilassila.litematica.printer.network;

import io.netty.buffer.Unpooled;
import me.aleksilassila.litematica.printer.network.payload.GuardedPlacementAckPayload;
import me.aleksilassila.litematica.printer.network.payload.GuardedUseItemOnPayload;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class GuardedPlacementPayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void wireFormatKeepsSupportAndPlacementCoordinatesSeparateAndPreservesStackComponents() {
        BlockPos support = new BlockPos(4, 9, 6);
        BlockPos target = support.above();
        ItemStack namedStack = new ItemStack(Items.OAK_SLAB, 17);
        namedStack.set(DataComponents.CUSTOM_NAME, Component.literal("printer material"));
        for (ItemStack stack : new ItemStack[]{namedStack, ItemStack.EMPTY}) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            try {
                var original = new GuardedUseItemOnPayload(
                        new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                                new BlockHitResult(new Vec3(4.5, 10.0, 6.5), Direction.UP, support, false), 42),
                        41, target, Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), stack, 7);
                GuardedUseItemOnPayload.CODEC.encode(buffer, original);
                var decoded = GuardedUseItemOnPayload.CODEC.decode(buffer);

                assertEquals(support, decoded.click().getHitResult().getBlockPos());
                assertEquals(target, decoded.placementPos());
                assertEquals(42, decoded.click().getSequence());
                assertEquals(41, decoded.batchId());
                assertEquals(7, decoded.selectedSlot());
                assertEquals(original.beforeState(), decoded.beforeState());
                assertEquals(original.hitState(), decoded.hitState());
                assertEquals(stack.getCount(), decoded.heldStack().getCount());
                assertTrue(ItemStack.isSameItemSameComponents(stack, decoded.heldStack()));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void replyCarriesTheSequenceAndCompleteDirectionalState() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var state = Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
            var original = new GuardedPlacementAckPayload(42, new BlockPos(4, 10, 6), state, false);
            GuardedPlacementAckPayload.CODEC.encode(buffer, original);
            assertEquals(original, GuardedPlacementAckPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void vanillaPredictionStillContainsTheRequiredPacketDispatchHook() throws Exception {
        // Check the external Minecraft integration point for each supported version, without launching a GUI.
        try (InputStream stream = getClass().getResourceAsStream("/net/minecraft/client/multiplayer/MultiPlayerGameMode.class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            long hooks = node.methods.stream().filter(method -> method.name.equals("startPrediction"))
                    .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                    .filter(instruction -> instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/minecraft/client/multiplayer/ClientPacketListener")
                            && call.name.equals("send")
                            && call.desc.equals("(Lnet/minecraft/network/protocol/Packet;)V"))
                    .count();
            assertEquals(1L, hooks);
        }
    }
}
