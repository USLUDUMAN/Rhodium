package com.rhodium;

import com.mojang.blaze3d.platform.InputConstants;
import com.rhodium.config.RhodiumConfig;
import com.rhodium.config.RhodiumConfigManager;
import com.rhodium.config.RhodiumMode;
import com.rhodium.core.AdaptiveQualityController;
import com.rhodium.gui.ManualControlsScreen;
import com.rhodium.util.NotificationHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RhodiumClient implements ClientModInitializer {
    public static final String MOD_ID = "rhodium";
    public static final Logger LOGGER = LoggerFactory.getLogger("Rhodium");

    private static final AdaptiveQualityController CONTROLLER = new AdaptiveQualityController();
    private static final KeyMapping.Category RHODIUM_KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
    private static KeyMapping toggleKeyMapping;
    private static KeyMapping manualControlsKeyMapping;

    public static AdaptiveQualityController getController() {
        return CONTROLLER;
    }

    @Override
    public void onInitializeClient() {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("[Rhodium] Initializing Rhodium {} - The catalyst for stable FPS.", version);

        // Load configuration
        RhodiumConfigManager.loadConfig();

        // Register keymapping (Key 'K') using InputConstants.KEY_K
        toggleKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.rhodium.toggle",
                InputConstants.KEY_K,
                RHODIUM_KEY_CATEGORY
        ));

        manualControlsKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.rhodium.open_manual_controls",
                InputConstants.KEY_F8,
                RHODIUM_KEY_CATEGORY
        ));

        // Load the saved quality ceiling without overwriting the settings persisted in options.txt.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            RhodiumConfig config = RhodiumConfigManager.getConfig();
            if (config.enabled && config.mode == RhodiumMode.AUTOMATIC) {
                CONTROLLER.loadPersistedBaseline(client);
            } else {
                CONTROLLER.clearBaseline();
            }
        });

        // World join & leave events
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CONTROLLER.onWorldJoin(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CONTROLLER.onWorldDisconnect(client);
        });

        // Clean shutdown
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CONTROLLER.flushPendingOptionsSave(client);
            RhodiumConfig config = RhodiumConfigManager.getConfig();
            if (!config.enabled || config.mode != RhodiumMode.AUTOMATIC) {
                CONTROLLER.clearBaseline();
            }
        });

        // Client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (manualControlsKeyMapping.consumeClick()) {
                RhodiumConfig config = RhodiumConfigManager.getConfig();
                if (client.gui != null && client.gui.screen() == null && client.player != null
                        && config.mode == RhodiumMode.MANUAL) {
                    client.gui.setScreen(new ManualControlsScreen());
                }
            }

            // Check toggle key
            while (toggleKeyMapping.consumeClick()) {
                RhodiumConfig config = RhodiumConfigManager.getConfig();
                config.enabled = !config.enabled;
                RhodiumConfigManager.saveConfig();

                if (config.enabled) {
                    if (config.mode == RhodiumMode.AUTOMATIC) {
                        CONTROLLER.captureBaseline(client);
                    } else {
                        CONTROLLER.applyManualSettings(client);
                    }
                    NotificationHelper.showMessage(client, Component.translatable("notification.rhodium.enabled"));
                } else {
                    if (config.mode == RhodiumMode.AUTOMATIC) {
                        CONTROLLER.restoreBaseline(client, false);
                        CONTROLLER.clearBaseline();
                    }
                    NotificationHelper.showMessage(client, Component.translatable("notification.rhodium.disabled"));
                }
            }

            // Quality controller update
            CONTROLLER.onClientTick(client);
        });
    }
}
