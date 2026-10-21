package me.aleksilassila.litematica.printer.utils;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.Set;

/**
 * 「哪些方块必须由服务端按精确状态落块」的判定，以及状态与属性串的互转。
 *
 * <p>判定标准是「原版放置上下文产生不了这个状态」：蘑菇柄的六个连接位由世界数据决定，
 * 藤蔓的附着面也是邻居推导出来的，都无法通过点击面表达。这类方块只靠原版放置包永远
 * 只会得到默认状态，必须走服务端的精确状态通道。</p>
 *
 * <p>反过来，门、床、告示牌、箱子这类有方块实体、需要成对放置或需要后续数据的方块不走这条
 * 通道——它们要么原版就能放对，要么需要额外数据（成对的下半、告示牌文本、木牌图案）。</p>
 */
public final class ExactPlacementUtils {

    private static final Set<String> CONNECTION_PROPERTIES =
            Set.of("up", "down", "north", "east", "south", "west");

    private ExactPlacementUtils() {
    }

    public static boolean isEligible(BlockState requiredState) {
        Block block = requiredState.getBlock();
        if (block.asItem() == Items.AIR) {
            return false;
        }
        if (requiredState.hasBlockEntity()) {
            return false;
        }
        if (block instanceof HugeMushroomBlock) {
            return true;
        }
        return hasConnectionShape(requiredState.getProperties());
    }

    private static boolean hasConnectionShape(Collection<Property<?>> properties) {
        if (properties.size() != CONNECTION_PROPERTIES.size()) {
            return false;
        }
        for (Property<?> property : properties) {
            if (!(property instanceof BooleanProperty)
                    || !CONNECTION_PROPERTIES.contains(property.getName())) {
                return false;
            }
        }
        return true;
    }

    /** 生成 {@code down=true,east=false,...} 形式的属性串；无属性方块返回空串。 */
    public static String propertiesOf(BlockState state) {
        StringBuilder builder = new StringBuilder();
        for (Property<?> property : state.getProperties()) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(property.getName()).append('=').append(valueName(state, property));
        }
        return builder.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String valueName(BlockState state, Property<?> property) {
        return ((Property) property).getName(((BlockState) state).getValue((Property) property));
    }
}
