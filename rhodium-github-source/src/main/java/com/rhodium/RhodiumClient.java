package com.rhodium;

import com.mojang.blaze3d.platform.InputConstants;
import com.rhodium.config.RhodiumConfig;
import com.rhodium.config.RhodiumConfigManager;
import com.rhodium.config.RhodiumMode;
import com.rhodium.core.AdaptiveQualityController;
import com.rhodium.util.NotificationHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RhodiumClient implements ClientModInitializer {
    public static final String MOD_ID = "rhodium";
    public static final Logger LOGGER = LoggerFactory.getLogger("Rhodium");

    private static final AdaptiveQualityController CONTROLLER = new AdaptiveQualityController();
    private static KeyMapping toggleKeyMapping;

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
                KeyMapping.Category.MISC
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
