package me.aleksilassila.litematica.printer.mixin;

import me.aleksilassila.litematica.printer.runtime.ClientBlockUpdateRouter;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import me.aleksilassila.litematica.printer.utils.mods.QuickShulkerBridge;
import me.aleksilassila.litematica.printer.utils.minecraft.NetworkUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import me.aleksilassila.litematica.printer.integration.quickshulker.QuickShulkerDiagnostics;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.MenuType;
//#if MC >= 12103
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"), method = "handleOpenScreen")
    private void printer$trackQuickShulkerContainerOpen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        QuickShulkerBridge.onContainerOpen(packet.getContainerId(), packet.getType());
    }

    @Inject(
            method = "handleOpenScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"
            ),
            cancellable = true
    )
    private void suppressTaskAnvilScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (packet.getType() != MenuType.ANVIL
                || RuntimeAccess.get().actionBroker().consumeManualAnvilScreenAllowance()) {
            return;
        }
        if (RuntimeAccess.get().actionBroker().consumeTaskAnvilScreenSuppression()) {
            NetworkUtils.sendPacket(new ServerboundContainerClosePacket(packet.getContainerId()));
            ci.cancel();
        }
    }

    @Inject(at = @At("TAIL"), method = "handleContainerContent")
    public void onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        //#if MC >= 12105
        int containerId = packet.containerId();
        //#else
        //$$ int containerId = packet.getContainerId();
        //#endif
        //#if MC >= 12105
        var items = packet.items();
        var carried = packet.carriedItem();
        //#else
        //$$ var items = packet.getItems();
        //$$ var carried = packet.getCarriedItem();
        //#endif
        for (int slot = 0; slot < items.size(); slot++) {
            QuickShulkerBridge.onSlotUpdate(containerId, slot, items.get(slot));
        }
        if (client.player != null && containerId == client.player.containerMenu.containerId) {
            QuickShulkerBridge.onSlotUpdate(-1, -1, carried);
        }
        if (client.player == null || containerId != client.player.containerMenu.containerId) {
            return;
        }
        QuickShulkerBridge.onInventoryContent(containerId, items, carried);
    }
    @Inject(at = @At("TAIL"), method = "handleContainerSetSlot")
    private void printer$confirmQuickShulkerSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        QuickShulkerBridge.onSlotUpdate(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }
    //#if MC >= 12103
    @Inject(at = @At("TAIL"), method = "handleSetCursorItem")
    private void printer$trackCursor(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
        QuickShulkerBridge.onSlotUpdate(-1, -1, packet.contents());
    }
    //#endif
    @Inject(at = @At("TAIL"), method = "handleBlockUpdate")
    private void printer$logBlockReply(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        QuickShulkerDiagnostics.blockReply(packet.getPos(), packet.getBlockState());
    }

    /**
     * 数据包放置不发本地预测，所以 {@code ClientLevel.syncBlockState} 不会被调用
     * （那是在 {@code BlockStatePredictionHandler.endPredictionsUpTo} 里走的路径）。
     * 这里直接接住服务端的权威方块更新，否则「等待确认 / 命中状态 / 扫描缓存失效」在数据包模式下全部收不到。
     */
    @Inject(at = @At("TAIL"), method = "handleBlockUpdate")
    private void printer$routeBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        ClientBlockUpdateRouter.accept(packet.getPos(), packet.getBlockState());
    }

    @Inject(at = @At("TAIL"), method = "handleChunkBlocksUpdate")
    private void printer$routeSectionBlockUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates(ClientBlockUpdateRouter::accept);
    }

    @Inject(at = @At("TAIL"), method = "handleChunkBlocksUpdate")
    private void printer$logSectionReply(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (QuickShulkerDiagnostics.enabled()) packet.runUpdates(QuickShulkerDiagnostics::blockReply);
    }
}
