package me.aleksilassila.litematica.printer.network.server;

import me.aleksilassila.litematica.printer.network.payload.ExactPlacePayload;
import me.aleksilassila.litematica.printer.network.payload.ServerHelloPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 服务端一侧的「精确状态放置」。
 *
 * <p>原版放置包只能让服务端按放置规则推导状态，无法表达 {@code mushroom_stem} 的六个连接位
 * 这类状态。这里接收客户端按投影状态发来的方块注册名 + 属性串，在服务端直接落状态，
 * 于是状态与投影逐位一致。</p>
 *
 * <p>服务端仍然做完整校验：配置开关、区块已加载、交互距离、目标格可替换、{@code canSurvive}、
 * 主手物品必须就是该方块物品。任何一条不满足都会拒绝并回执，客户端不会把方块当成已放置。</p>
 */
public final class ServerExactPlacement {

    /** 交互距离之外的容差：客户端与服务器的位置插值会让贴边放置差一点点。 */
    private static final double RANGE_TOLERANCE = 1.0D;

    private ServerExactPlacement() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ExactPlacePayload.TYPE, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPrinterConfig config = ServerPrinterConfig.get();
            if (ServerPlayNetworking.canSend(handler.player, ServerHelloPayload.TYPE)) {
                ServerPlayNetworking.send(handler.player,
                        new ServerHelloPayload(config.exactPlacement, config.interactionAck));
            }
        });
    }

    private static void handle(ServerPlayer player, ExactPlacePayload payload) {
        BlockPos pos = payload.pos();
        if (!ServerPrinterConfig.get().exactPlacement) {
            reject(player, pos);
            return;
        }
        ServerLevel level = player.level();
        if (!level.isLoaded(pos)) {
            reject(player, pos);
            return;
        }
        double range = Math.max(1.0D, player.blockInteractionRange()) + RANGE_TOLERANCE;
        if (!player.isWithinBlockInteractionRange(pos, range)) {
            reject(player, pos);
            return;
        }
        BlockState state = parseState(payload);
        if (state == null || !state.canSurvive(level, pos)) {
            reject(player, pos);
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !current.canBeReplaced()) {
            reject(player, pos);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() != state.getBlock()) {
            reject(player, pos);
            return;
        }
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            reject(player, pos);
            return;
        }

        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!player.isCreative()) {
            held.shrink(1);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        ServerInteractionAck.exact(player, pos, true);
    }

    private static void reject(ServerPlayer player, BlockPos pos) {
        ServerInteractionAck.exact(player, pos, false);
    }

    /**
     * 把注册名 + 属性串还原成方块状态。
     *
     * <p>属性多一个、少一个、值不合法都直接拒绝：宁可让客户端退回原版放置，也不要落一个
     * 和投影不一致的状态。</p>
     */
    private static BlockState parseState(ExactPlacePayload payload) {
        Identifier identifier = Identifier.tryParse(payload.block());
        if (identifier == null) {
            return null;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(identifier);
        if (block.isEmpty()) {
            return null;
        }
        BlockState state = block.get().defaultBlockState();
        Map<String, String> values = parseProperties(payload.properties());
        if (values == null) {
            return null;
        }
        for (Property<?> property : state.getProperties()) {
            String raw = values.remove(property.getName());
            if (raw == null) {
                return null;
            }
            state = applyProperty(state, property, raw);
            if (state == null) {
                return null;
            }
        }
        return values.isEmpty() ? state : null;
    }

    private static Map<String, String> parseProperties(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw.isEmpty()) {
            return values;
        }
        for (String entry : raw.split(",")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                return null;
            }
            values.put(entry.substring(0, separator), entry.substring(separator + 1));
        }
        return values;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property<?> property, String raw) {
        Optional<?> value = property.getValue(raw);
        if (value.isEmpty()) {
            return null;
        }
        return state.setValue((Property) property, (Comparable) value.get());
    }
}
