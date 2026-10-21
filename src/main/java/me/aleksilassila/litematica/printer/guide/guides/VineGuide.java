package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.PrinterUtils;
import me.aleksilassila.litematica.printer.utils.InteractionUtils;
import net.minecraft.core.Direction;

/**
 * 藤蔓/发光地衣
 */
public class VineGuide extends Guide {

    public VineGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN && requiredBlock instanceof net.minecraft.world.level.block.VineBlock) continue;
            Object value = PrinterUtils.getPropertyByName(requiredState, direction.name());
            if (value instanceof Boolean && (Boolean) value) {
                return Result.success(new Action().setSides(direction));
            }
        }
        return Result.SKIP;
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN && requiredBlock instanceof net.minecraft.world.level.block.VineBlock) continue;
            Object value = PrinterUtils.getPropertyByName(requiredState, direction.name());
            if (value instanceof Boolean && (Boolean) value) {
                return Result.success(new Action().setSides(direction).setLookDirection(direction));
            }
        }
        // 附着面不一致 → 根据配置决定是否破坏
        if (Configs.Print.BREAK_WRONG_STATE_BLOCK.getBooleanValue()) {
            if (InteractionUtils.canBreakBlock(blockPos) && InteractionUtils.breakRestriction(currentState)) {
                InteractionUtils.getRuntime().add(context);
            }
        }
        return Result.SKIP;
    }
}
