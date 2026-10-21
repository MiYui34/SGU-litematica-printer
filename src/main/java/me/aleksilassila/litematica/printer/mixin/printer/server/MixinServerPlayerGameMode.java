package me.aleksilassila.litematica.printer.mixin.printer.server;

import me.aleksilassila.litematica.printer.network.server.ServerInteractionAck;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端交互回执：在服务端真正处理完一次「使用物品/放置」或「破坏方块」之后，把结果发回客户端。
 *
 * <p>这条 mixin 也登记在两端（{@code mixins} 而非 {@code server} 数组）里，因为单人游戏的集成服务端
 * 在客户端进程内运行；客户端侧这个类永远不会被实例化，注入点不产生任何开销。</p>
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerGameMode {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void litematica_printer$ackPlacement(
            ServerPlayer interactingPlayer,
            Level level,
            ItemStack stack,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        ServerInteractionAck.placement(this.player, hit.getBlockPos(), cir.getReturnValue());
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void litematica_printer$ackBreaking(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ServerInteractionAck.breaking(this.player, pos, Boolean.TRUE.equals(cir.getReturnValue()));
    }
}
