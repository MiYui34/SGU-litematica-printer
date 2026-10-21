package me.aleksilassila.litematica.printer.utils;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.NonNullList;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 数据包模式（placeUsePacket）下的物品切换。
 *
 * <p>不经过 {@code MultiPlayerGameMode#handleContainerInput} 的本地预测，而是按原服务端打印机的做法
 * 直接发送 {@link ServerboundContainerClickPacket}（SWAP），随后再做一次等价的本地
 * {@code inventoryMenu.clicked} 同步，避免本地视图与服务端不一致。</p>
 */
final class InventoryPacketSwap {
    private InventoryPacketSwap() {
    }

    /**
     * 把 {@code slot} 与当前选中的热栏槽位交换（{@link ContainerInput#SWAP}）。
     *
     * @return 是否已发送
     */
    static boolean swapWithSelectedHotbarSlot(Player player, int slot, int currentHotbarSlot) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }

        NonNullList<Slot> slots = player.inventoryMenu.slots;
        int totalSlots = slots.size();
        List<ItemStack> copies = Lists.newArrayListWithCapacity(totalSlots);
        for (Slot slotItem : slots) {
            copies.add(slotItem.getItem().copy());
        }

        Int2ObjectMap<HashedStack> snapshot = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < totalSlots; i++) {
            ItemStack original = copies.get(i);
            ItemStack current = slots.get(i).getItem();
            if (!ItemStack.isSameItem(original, current)) {
                snapshot.put(i, HashedStack.create(current, connection.decoratedHashOpsGenenerator()));
            }
        }

        HashedStack carried = HashedStack.create(
                player.inventoryMenu.getCarried(),
                connection.decoratedHashOpsGenenerator()
        );
        connection.send(new ServerboundContainerClickPacket(
                player.inventoryMenu.containerId,
                player.inventoryMenu.getStateId(),
                (short) slot,
                (byte) currentHotbarSlot,
                ContainerInput.SWAP,
                snapshot,
                carried
        ));
        player.inventoryMenu.clicked(slot, currentHotbarSlot, ContainerInput.SWAP, player);
        return true;
    }
}
