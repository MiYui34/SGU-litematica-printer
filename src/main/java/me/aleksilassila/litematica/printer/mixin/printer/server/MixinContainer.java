package me.aleksilassila.litematica.printer.mixin.printer.server;

import me.aleksilassila.litematica.printer.network.server.ServerRemoteContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 容器自己的距离校验：{@code BaseContainerBlockEntity#stillValid} 最终调用静态方法
 * {@code Container#stillValidBlockEntity}，默认只认 4 格以内的玩家。
 * 远程打开的会话一律放行，否则界面虽然开出来了，箱子本身也会拒绝这个玩家。</p>
 */
@Mixin(Container.class)
public interface MixinContainer {

    @Inject(method = "stillValidBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void litematica_printer$allowRemoteUser(BlockEntity blockEntity, Player player,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (ServerRemoteContainer.hasRemoteSession(player)) {
            cir.setReturnValue(true);
        }
    }
}
