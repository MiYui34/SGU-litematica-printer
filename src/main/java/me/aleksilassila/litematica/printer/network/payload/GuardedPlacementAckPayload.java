package me.aleksilassila.litematica.printer.network.payload;

import me.aleksilassila.litematica.printer.network.PrinterNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The target state after a particular vanilla click was processed (or rejected as stale). */
public record GuardedPlacementAckPayload(
        int sequence, BlockPos pos, BlockState state, boolean processed
) implements CustomPacketPayload {
    public static final Type<GuardedPlacementAckPayload> TYPE =
            new Type<>(PrinterNetworking.GUARDED_PLACEMENT_ACK_CHANNEL);

    public static final StreamCodec<FriendlyByteBuf, GuardedPlacementAckPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.sequence);
                buf.writeBlockPos(payload.pos);
                buf.writeVarInt(Block.getId(payload.state));
                buf.writeBoolean(payload.processed);
            },
            buf -> new GuardedPlacementAckPayload(
                    buf.readVarInt(), buf.readBlockPos(),
                    Block.stateById(buf.readVarInt()), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
