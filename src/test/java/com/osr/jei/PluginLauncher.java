package com.osr.jei;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Development entry point. Registers this plugin as a builtin before the
 * client starts, which is the path RuneLite uses for {@code gradle run}.
 */
public final class PluginLauncher {

    private PluginLauncher() {
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(JEIPlugin.class);
        RuneLite.main(args);
    }
}
