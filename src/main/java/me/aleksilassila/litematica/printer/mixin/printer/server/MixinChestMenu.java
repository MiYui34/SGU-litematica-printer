package me.aleksilassila.litematica.printer.mixin.printer.server;

import me.aleksilassila.litematica.printer.network.server.ServerRemoteContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 远程打开的箱子不该在关闭时走「盖子合上」的流程。
 *
 * <p>{@code ChestMenu#removed} 会调用 {@code Container#stopOpen}，后者递减开启者计数并给方块
 * 发关闭动画事件。远程会话里玩家根本不在旁边，这一步只会在远处刷一次无用事件（还会让
 * 比较器输出抖动），所以只为远程会话跳过这一句，超类 {@code removed} 照常执行。</p>
 */
@Mixin(ChestMenu.class)
public abstract class MixinChestMenu {

    @Redirect(
            method = "removed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/Container;stopOpen(Lnet/minecraft/world/entity/ContainerUser;)V"
            )
    )
    private void litematica_printer$skipStopOpen(Container container, ContainerUser user) {
        if (user instanceof Player player && ServerRemoteContainer.hasRemoteSession(player)) {
            return;
        }
        container.stopOpen(user);
    }
}
