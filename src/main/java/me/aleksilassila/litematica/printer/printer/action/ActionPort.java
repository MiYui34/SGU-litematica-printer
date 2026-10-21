package me.aleksilassila.litematica.printer.printer.action;

import me.aleksilassila.litematica.printer.core.action.ResourceLease;
import me.aleksilassila.litematica.printer.printer.PlayerLook;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Feature-facing action capability, independent of the queue implementation. */
public interface ActionPort {
    enum ActionSource { GENERIC, PRINT, FILL, COVER, FLUID }

    enum SendResult {
        /** The local interaction did not return FAIL; this does not confirm a server world update. */
        SENT,
        WAITING_FOR_LOOK,
        NO_QUEUED_ACTION,
        NO_PLAYER,
        STALE_POSITION,
        HELD_ITEM_CHANGED,
        RESERVE_LIMIT,
        NO_GAME_MODE,
        INTERACTION_REJECTED;

        public boolean isSent() { return this == SENT; }

        public boolean isWaiting() { return this == WAITING_FOR_LOOK; }
    }

    boolean queueClick(
            @NotNull BlockPos target,
            @NotNull Direction side,
            @NotNull Vec3 hitModifier,
            boolean useShift,
            int clickRepeatCount,
            @Nullable Item[] expectedItems,
            @NotNull ActionSource source
    );

    void useProtocolHitModifier(@NotNull Vec3 hitModifier);

    boolean setQueueCompletionListener(@Nullable Consumer<SendResult> completionListener);

    boolean setExpectedStackPredicate(@Nullable Predicate<ItemStack> expectedStackPredicate);

    /**
     * 标记当前排队的这次点击为「按精确状态放置」。
     *
     * <p>只有服务端声明支持、且该方块状态无法由原版放置表达时才会被调用；默认实现直接返回
     * false，表示这条动作通道不支持精确放置，调用方退回原版放置。</p>
     */
    default boolean setExactPlacement(@NotNull BlockPos pos, @NotNull net.minecraft.world.level.block.state.BlockState state) {
        return false;
    }

    SendResult sendQueue(@Nullable LocalPlayer player);

    void cancelQueue();

    boolean isWaitingForLook();

    @Nullable PlayerLook getLook();

    void setLook(@Nullable PlayerLook look);

    void setWaitForHorizontalLook(boolean waitForHorizontalLook);

    void setNeedWaitModifyLookFromAction(boolean actionRequiresWaitModifyLook);

    void setShift(LocalPlayer player, boolean shift);

    boolean isPrinterInteractionActive();

    boolean isEasyPlaceProtocolActive();

    boolean consumeManualAnvilScreenAllowance();

    boolean consumeTaskAnvilScreenSuppression();

    void prioritizeManualAnvilScreen();

    void armPrintSignEdit(BlockPos blockPos);

    void confirmPrintSignEditSent(BlockPos blockPos);

    void cancelPrintSignEdit(BlockPos blockPos);

    boolean consumePrintSignEdit(BlockPos blockPos);

    boolean isResourceHeld(ResourceLease resource);

    boolean tryAcquire(String owner, EnumSet<ResourceLease> resources, long timeoutNanos);

    void releaseOwner(String owner);

    boolean isResourceHeldByOther(ResourceLease resource, String owner);
}
