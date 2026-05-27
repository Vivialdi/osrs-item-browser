package com.osr.jei;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;

import javax.swing.*;

/**
 * Launches RuneLite and manually loads the OSRS JEI plugin.
 *
 * When RuneLite loads a plugin normally it:
 *   1. Scans the plugin class for @Provides methods
 *   2. Creates a child Guice injector that includes those bindings
 *   3. Builds the plugin from that child injector
 *
 * We replicate that here. Our only @Provides is JEIConfig → ConfigManager.getConfig(),
 * so we add that one binding to a child injector before asking Guice to build the plugin.
 */
public class PluginLauncher {

    public static void main(String[] args) throws Exception {
        System.out.println("[JEI] Starting RuneLite...");
        RuneLite.main(new String[]{"--developer-mode"});

        // Poll until RuneLite's Guice injector is ready
        Injector injector = null;
        for (int i = 0; i < 20; i++) {
            injector = RuneLite.getInjector();
            if (injector != null) break;
            System.out.println("[JEI] Waiting for Guice injector... attempt " + (i + 1));
            Thread.sleep(500);
        }

        if (injector == null) {
            System.err.println("[JEI] ERROR: RuneLite injector never became available.");
            return;
        }

        System.out.println("[JEI] Got injector. Registering plugin...");

        final Injector parentInjector = injector;
        SwingUtilities.invokeLater(() -> {
            try {
                // Ask RuneLite's ConfigManager to create our config proxy
                // (this is what @Provides JEIConfig in JEIPlugin.java does normally)
                ConfigManager configManager = parentInjector.getInstance(ConfigManager.class);
                JEIConfig jeiConfig = configManager.getConfig(JEIConfig.class);

                // Build a child injector that adds the one missing binding: JEIConfig
                Injector pluginInjector = parentInjector.createChildInjector(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(JEIConfig.class).toInstance(jeiConfig);
                    }
                });

                // Now Guice can build JEIPlugin — all @Inject fields (panel, itemManager, etc.) resolve
                JEIPlugin plugin = pluginInjector.getInstance(JEIPlugin.class);

                // Hand it to RuneLite's PluginManager: calls startUp() and wires event subscriptions
                PluginManager pluginManager = parentInjector.getInstance(PluginManager.class);
                pluginManager.startPlugin(plugin);

                System.out.println("[JEI] Plugin started! Look for the JEI button in the sidebar.");
            } catch (Exception e) {
                System.err.println("[JEI] ERROR starting plugin:");
                e.printStackTrace();
            }
        });
    }
}
