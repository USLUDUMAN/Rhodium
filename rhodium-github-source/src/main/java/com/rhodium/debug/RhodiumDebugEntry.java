package com.rhodium.debug;

import com.rhodium.RhodiumClient;
import com.rhodium.core.AdaptiveQualityController;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public final class RhodiumDebugEntry implements DebugScreenEntry {
    private static final ChatFormatting TITLE_COLOR = ChatFormatting.RED;
    private static final Identifier SODIUM_DEBUG_GROUP = Identifier.fromNamespaceAndPath("sodium", "debug_group");
    private static final Identifier RHODIUM_DEBUG_GROUP = Identifier.fromNamespaceAndPath("rhodium", "debug_group");

    private static String styleLine(String line) {
        return TITLE_COLOR.toString() + ChatFormatting.BOLD + line + ChatFormatting.RESET;
    }

    @Override
    public void display(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
        Minecraft client = Minecraft.getInstance();
        AdaptiveQualityController.DebugInfo info = RhodiumClient.getController().getDebugInfo(client);
        String version = FabricLoader.getInstance().getModContainer(RhodiumClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElseGet(() -> Component.translatable("debug.rhodium.version.unknown").getString());

        String header = Component.translatable("debug.rhodium.line.header", version,
                Component.translatable(info.modeKey())).getString();
        Identifier group = FabricLoader.getInstance().isModLoaded("sodium")
                ? SODIUM_DEBUG_GROUP : RHODIUM_DEBUG_GROUP;

        if ("debug.rhodium.mode.disabled".equals(info.modeKey())) {
            displayer.addToGroup(group, styleLine(header));
            return;
        }
        if ("debug.rhodium.mode.manual".equals(info.modeKey())) {
            displayer.addToGroup(group, List.of(styleLine(header),
                    styleLine(Component.translatable("debug.rhodium.manual").getString())));
            return;
        }

        Component status = "debug.rhodium.status.waiting".equals(info.statusKey())
                ? Component.translatable(info.statusKey(), info.waitingSeconds())
                : Component.translatable(info.statusKey());
        String fpsLine = Component.translatable("debug.rhodium.line.fps",
                info.currentFps(), info.targetFps(), status).getString();

        AdaptiveQualityController.DebugStep step = info.nextStep();
        String nextStepLine;
        if (step == null) {
            nextStepLine = Component.translatable("debug.rhodium.line.next.none").getString();
        } else {
            nextStepLine = Component.translatable("debug.rhodium.line.next",
                    Component.translatable(step.settingKey()), step.currentValue(), step.nextValue(),
                    info.preparationPercent() + "%").getString();
        }

        AdaptiveQualityController.DebugQuality quality = info.quality();
        String dash = Component.translatable("debug.rhodium.unmanaged").getString();
        String qualityLine = quality == null
                ? Component.translatable("debug.rhodium.line.quality", dash, dash, dash, dash,
                        dash, dash, dash, dash).getString()
                : Component.translatable("debug.rhodium.line.quality",
                        quality.renderCurrent(), quality.renderCeiling(),
                        quality.simulationCurrent(), quality.simulationCeiling(),
                        quality.entityCurrent(), quality.entityCeiling(),
                        quality.particlesCurrent(), quality.particlesCeiling()).getString();

        displayer.addToGroup(group, List.of(styleLine(header), styleLine(fpsLine),
                styleLine(nextStepLine), styleLine(qualityLine)));
    }
}
