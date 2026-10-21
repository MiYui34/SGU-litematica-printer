package me.aleksilassila.litematica.printer.mixin.printer.server;

import me.aleksilassila.litematica.printer.network.server.ServerRemoteContainer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 每 tick 维护远程打开的容器：保持区块加载，方块被拆了就通知客户端收摊。
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {

    @Inject(method = "tick", at = @At("HEAD"))
    private void litematica_printer$tickRemoteContainers(CallbackInfo ci) {
        ServerRemoteContainer.tickLevel((ServerLevel) (Object) this);
    }
}
