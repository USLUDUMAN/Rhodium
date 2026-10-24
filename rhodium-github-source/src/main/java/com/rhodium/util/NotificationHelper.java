package com.rhodium.util;

import com.rhodium.config.RhodiumConfig;
import com.rhodium.config.RhodiumConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class NotificationHelper {

    public static void showMessage(Minecraft client, Component message) {
        if (client == null) {
            return;
        }

        RhodiumConfig config = RhodiumConfigManager.getConfig();
        Component component = Component.literal("[Rhodium] ").withStyle(ChatFormatting.AQUA)
                .append(message.copy().withStyle(ChatFormatting.WHITE));

        if (config.showOverlayMessages && client.gui != null && client.gui.hud != null) {
            client.gui.hud.setOverlayMessage(component, false);
        }

        if (config.showChatMessages && client.player != null) {
            client.player.sendSystemMessage(component);
        }
    }
}
