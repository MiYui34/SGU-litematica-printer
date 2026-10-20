package me.aleksilassila.litematica.printer.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** No-op debug sink kept so break-pipeline diagnostics stay switchable without a logger dependency. */
public final class PrinterDebugLog {
    private PrinterDebugLog() {
    }

    public static void write(String message) {
    }

    public static void reset() {
    }

    public static String pos(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static String describeState(BlockState state) {
        return state.getBlock() + " " + state;
    }
}
