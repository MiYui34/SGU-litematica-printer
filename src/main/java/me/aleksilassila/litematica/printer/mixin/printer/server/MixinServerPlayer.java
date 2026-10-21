package me.aleksilassila.litematica.printer.mixin.printer.server;

import me.aleksilassila.litematica.printer.network.server.ServerRemoteContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让「服务端代开的容器」活得下来。
 *
 * <p>原版 {@code ServerPlayer#tick} 每 tick 都会检查
 * {@code containerMenu.stillValid(player)}，玩家站在几十格开外必然为 false，于是下一 tick
 * 就把界面关掉。远程取物的整个流程都要靠这个界面，所以对登记过的会话直接放行。</p>
 *
 * <p>会话在玩家关界面、掉线时清除（{@link #litematica_printer$clearSession}）。</p>
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void litematica_printer$clearOnDisconnect(CallbackInfo ci) {
        ServerRemoteContainer.onPlayerClosed((ServerPlayer) (Object) this);
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void litematica_printer$clearSession(CallbackInfo ci) {
        ServerRemoteContainer.onPlayerClosed((ServerPlayer) (Object) this);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"
            )
    )
    private boolean litematica_printer$allowRemoteMenu(AbstractContainerMenu menu, Player player) {
        return ServerRemoteContainer.hasRemoteSession(player) || menu.stillValid(player);
    }
}
