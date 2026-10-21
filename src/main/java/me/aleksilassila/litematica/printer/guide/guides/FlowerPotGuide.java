package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.InteractionUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.item.Items;

/**
 * 花盆
 */
public class FlowerPotGuide extends Guide {

    public FlowerPotGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        return Result.success(new Action().setItem(Items.FLOWER_POT));
    }

    @Override
    protected Result onBuildActionWrongBlock(BlockMatchResult state) {
        if (requiredBlock instanceof FlowerPotBlock potBlock) {
            Block content = potBlock.getPotted();
            if (content != Blocks.AIR) {
                // 花盆里的植物不对 → 根据配置决定是否破坏
                if (currentBlock instanceof FlowerPotBlock currentPotBlock) {
                    Block currentContent = currentPotBlock.getPotted();
                    if (currentContent != Blocks.AIR && currentContent != content
                            && Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue()
                            && InteractionUtils.canBreakBlock(blockPos)
                            && InteractionUtils.breakRestriction(currentState)) {
                        InteractionUtils.getRuntime().add(context);
                        return Result.SKIP;
                    }
                }
                return Result.success(new ClickAction().setItem(content.asItem()));
            }
        }
        return Result.SKIP;
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        return Result.SKIP;
    }
}
