package me.aleksilassila.litematica.printer.utils.minecraft;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.Packet;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface PredictiveAction {
    Packet<?> predict(int sequence);
}
