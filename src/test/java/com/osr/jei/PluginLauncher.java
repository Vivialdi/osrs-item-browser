package com.osr.jei;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        ScheduledExecutorService wait = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jei-launch");
            thread.setDaemon(true);
            return thread;
        });
        AtomicInteger attempts = new AtomicInteger();
        wait.scheduleWithFixedDelay(() -> {
            Injector injector = RuneLite.getInjector();
            if (injector == null) {
                int attempt = attempts.incrementAndGet();
                System.out.println("[JEI] Waiting for Guice injector... attempt " + attempt);
                if (attempt >= 20) {
                    System.err.println("[JEI] ERROR: RuneLite injector never became available.");
                    wait.shutdown();
                }
                return;
            }
            wait.shutdown();
            SwingUtilities.invokeLater(() -> startPlugin(injector));
        }, 0, 500, TimeUnit.MILLISECONDS);
        RuneLite.main(new String[]{"--developer-mode"});
    }

    private static void startPlugin(Injector parentInjector) {
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
    }
}
