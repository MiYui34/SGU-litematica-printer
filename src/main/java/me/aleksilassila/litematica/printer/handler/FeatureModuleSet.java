package me.aleksilassila.litematica.printer.handler;

import com.google.common.collect.ImmutableList;
import me.aleksilassila.litematica.printer.handler.handlers.GuiHandler;
import me.aleksilassila.litematica.printer.handler.handlers.PrintHandler;
import me.aleksilassila.litematica.printer.runtime.PrinterRuntime;

/** Runtime-owned feature graph and its stable execution order. */
public final class FeatureModuleSet {
    private final GuiHandler gui;
    private final PrintHandler print;
    private final ImmutableList<FeatureModuleBase> values;
    private final TickScheduler scheduler;

    public FeatureModuleSet(PrinterRuntime runtime) {
        this.gui = new GuiHandler(runtime);
        this.print = new PrintHandler(runtime);
        this.values = ImmutableList.of(this.gui, this.print);
        for (FeatureModuleBase module : this.values) {
            runtime.register(module);
        }
        this.scheduler = new TickScheduler(this.values, runtime);
        runtime.register(this.scheduler);
    }

    public GuiHandler gui() { return this.gui; }
    public PrintHandler print() { return this.print; }
    public ImmutableList<FeatureModuleBase> values() { return this.values; }

    public void tick() { this.scheduler.tick(); }
    public int packetTick() { return this.scheduler.getPacketTick(); }
    public void setPacketTick(int packetTick) { this.scheduler.setPacketTick(packetTick); }
    public void recordInboundPacket() { this.scheduler.recordInboundPacket(); }
    public String lastPauseReason() { return this.scheduler.getLastPauseReason(); }
}
