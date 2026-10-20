package me.aleksilassila.litematica.printer.handler;

import me.aleksilassila.litematica.printer.utils.ConfigUtils;

/** Resolves the global enable settings for a feature. */
final class ModuleEnablePolicy {
    private ModuleEnablePolicy() {
    }

    static boolean allows(FeatureModuleBase module) {
        if (!ConfigUtils.isEnable()) return false;
        return module.enableConfig == null || module.enableConfig.getBooleanValue();
    }
}
