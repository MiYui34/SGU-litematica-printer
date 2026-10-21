package me.aleksilassila.litematica.printer.network.payload;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A vanilla click with the world and hand snapshot from which the printer planned it. */
public record GuardedUseItemOnPayload(
        ServerboundUseItemOnPacket click,
        int batchId,
        BlockPos placementPos,
        BlockState beforeState,
        BlockState hitState,
        ItemStack heldStack,
        int selectedSlot
) implements CustomPacketPayload {
    public static final Type<GuardedUseItemOnPayload> TYPE =
            new Type<>(PrinterNetworking.GUARDED_USE_ITEM_ON_CHANNEL);

    public static final StreamCodec<RegistryFriendlyByteBuf, GuardedUseItemOnPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ServerboundUseItemOnPacket.STREAM_CODEC.encode(buf, payload.click);
                buf.writeVarInt(payload.batchId);
                buf.writeBlockPos(payload.placementPos);
                buf.writeVarInt(Block.getId(payload.beforeState));
                buf.writeVarInt(Block.getId(payload.hitState));
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.heldStack);
                buf.writeVarInt(payload.selectedSlot);
            },
            buf -> new GuardedUseItemOnPayload(
                    ServerboundUseItemOnPacket.STREAM_CODEC.decode(buf),
                    buf.readVarInt(), buf.readBlockPos(),
                    Block.stateById(buf.readVarInt()), Block.stateById(buf.readVarInt()),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
