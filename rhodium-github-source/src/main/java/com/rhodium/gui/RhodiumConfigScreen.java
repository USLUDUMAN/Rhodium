package com.rhodium.gui;

import com.rhodium.RhodiumClient;
import com.rhodium.config.RhodiumConfig;
import com.rhodium.config.RhodiumConfigManager;
import com.rhodium.config.RhodiumMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

public class RhodiumConfigScreen extends OptionsSubScreen {

    public RhodiumConfigScreen(Screen lastScreen) {
        this(lastScreen, Minecraft.getInstance().options);
    }

    public RhodiumConfigScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.literal("Rhodium ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(text("title.settings").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
    }

    private static MutableComponent text(String key) {
        return Component.translatable("config.rhodium." + key);
    }

    private static MutableComponent text(String key, Object... args) {
        return Component.translatable("config.rhodium." + key, args);
    }

    private static Component header(String key) {
        return text("section." + key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    @Override
    protected void addOptions() {
        RhodiumConfig config = RhodiumConfigManager.getConfig();

        this.list.addHeader(header("general"));

        CycleButton<RhodiumMode> modeBtn = CycleButton.<RhodiumMode>builder(m -> Component.translatable(m.getTranslationKey()), config.mode)
                .withValues(RhodiumMode.AUTOMATIC, RhodiumMode.MANUAL)
                .withTooltip(val -> Tooltip.create(text(val == RhodiumMode.AUTOMATIC ? "tooltip.mode.automatic" : "tooltip.mode.manual")))
                .create(text("button.mode"), (btn, val) -> {
                    RhodiumMode oldMode = config.mode;
                    config.mode = val;
                    RhodiumConfigManager.saveConfig();
                    if (oldMode == RhodiumMode.MANUAL && val == RhodiumMode.AUTOMATIC) {
                        RhodiumClient.getController().captureBaseline(this.minecraft);
                    } else if (val == RhodiumMode.MANUAL) {
                        RhodiumClient.getController().applyManualSettings(this.minecraft);
                    }
                });

        CycleButton<Boolean> enabledBtn = CycleButton.onOffBuilder(config.enabled)
                .withTooltip(val -> Tooltip.create(text("tooltip.enabled")))
                .create(text("button.enabled"), (btn, val) -> {
                    config.enabled = val;
                    RhodiumConfigManager.saveConfig();
                    if (val) {
                        if (config.mode == RhodiumMode.AUTOMATIC) {
                            RhodiumClient.getController().captureBaseline(this.minecraft);
                        } else {
                            RhodiumClient.getController().applyManualSettings(this.minecraft);
                        }
                    } else if (config.mode == RhodiumMode.AUTOMATIC) {
                        RhodiumClient.getController().restoreBaseline(this.minecraft, false);
                        RhodiumClient.getController().clearBaseline();
                    }
                });

        this.list.addSmall(modeBtn, enabledBtn);

        this.list.addHeader(header("performance"));

        CycleButton<Integer> targetFpsBtn = CycleButton.<Integer>builder(fps -> Component.literal(fps + " FPS"), config.targetFps)
                .withValues(List.of(30, 45, 60, 75, 90, 120, 144, 165, 240))
                .withTooltip(val -> Tooltip.create(text("tooltip.target_fps", String.format(Locale.ROOT, "%.1f", 1000.0 / val))))
                .create(text("button.target_fps"), (btn, val) -> {
                    config.targetFps = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Double> dropThresholdBtn = CycleButton.<Double>builder(pct -> Component.literal("%" + pct.intValue()), config.dropThresholdPercent)
                .withValues(List.of(105.0, 110.0, 115.0, 120.0, 125.0, 130.0))
                .withTooltip(val -> Tooltip.create(text("tooltip.drop_threshold")))
                .create(text("button.drop_threshold"), (btn, val) -> {
                    config.dropThresholdPercent = val;
                    if (config.riseThresholdPercent >= val) {
                        config.riseThresholdPercent = Math.max(50.0, val - 15.0);
                    }
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(targetFpsBtn, dropThresholdBtn);

        CycleButton<Double> riseThresholdBtn = CycleButton.<Double>builder(pct -> Component.literal("%" + pct.intValue()), config.riseThresholdPercent)
                .withValues(List.of(60.0, 70.0, 75.0, 80.0, 85.0, 90.0, 95.0, 100.0))
                .withTooltip(val -> Tooltip.create(text("tooltip.rise_threshold")))
                .create(text("button.rise_threshold"), (btn, val) -> {
                    config.riseThresholdPercent = val;
                    if (val >= config.dropThresholdPercent) {
                        config.dropThresholdPercent = val + 15.0;
                    }
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Integer> upgradeMarginBtn = CycleButton.<Integer>builder(pct -> Component.literal("%" + pct), config.upgradeMarginPercent)
                .withValues(IntStream.rangeClosed(5, 40).boxed().toList())
                .withTooltip(val -> Tooltip.create(text("tooltip.upgrade_margin")))
                .create(text("button.upgrade_margin"), (btn, val) -> {
                    config.upgradeMarginPercent = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(riseThresholdBtn, upgradeMarginBtn);

        CycleButton<Integer> maxRenderDistanceBtn = CycleButton.<Integer>builder(d -> text("value.chunks", d), config.maxRenderDistance)
                .withValues(IntStream.rangeClosed(2, 32).boxed().toList())
                .withTooltip(val -> Tooltip.create(text("tooltip.max_render_distance")))
                .create(text("button.max_render_distance"), (btn, val) -> {
                    config.maxRenderDistance = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Integer> maxSimulationDistanceBtn = CycleButton.<Integer>builder(d -> text("value.chunks", d), config.maxSimulationDistance)
                .withValues(IntStream.rangeClosed(3, 32).boxed().toList())
                .withTooltip(val -> Tooltip.create(text("tooltip.max_simulation_distance")))
                .create(text("button.max_simulation_distance"), (btn, val) -> {
                    config.maxSimulationDistance = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(maxRenderDistanceBtn, maxSimulationDistanceBtn);

        this.list.addHeader(header("managed"));

        CycleButton<Boolean> manageParticlesBtn = CycleButton.onOffBuilder(config.manageParticles)
                .withTooltip(val -> Tooltip.create(text("tooltip.manage_particles")))
                .create(text("button.particles"), (btn, val) -> {
                    config.manageParticles = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Boolean> manageEntityBtn = CycleButton.onOffBuilder(config.manageEntityDistance)
                .withTooltip(val -> Tooltip.create(text("tooltip.manage_entity_distance")))
                .create(text("button.entity_distance"), (btn, val) -> {
                    config.manageEntityDistance = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(manageParticlesBtn, manageEntityBtn);

        CycleButton<Boolean> manageSimBtn = CycleButton.onOffBuilder(config.manageSimulationDistance)
                .withTooltip(val -> Tooltip.create(text("tooltip.manage_simulation_distance")))
                .create(text("button.simulation_distance"), (btn, val) -> {
                    config.manageSimulationDistance = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Boolean> manageRenderBtn = CycleButton.onOffBuilder(config.manageRenderDistance)
                .withTooltip(val -> Tooltip.create(text("tooltip.manage_render_distance")))
                .create(text("button.render_distance"), (btn, val) -> {
                    config.manageRenderDistance = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(manageSimBtn, manageRenderBtn);

        this.list.addHeader(header("minimums"));

        CycleButton<Integer> minRenderBtn = CycleButton.<Integer>builder(d -> text("value.chunks", d), config.minRenderDistance)
                .withValues(List.of(2, 4, 6, 8, 10, 12))
                .withTooltip(val -> Tooltip.create(text("tooltip.min_render_distance")))
                .create(text("button.min_render_distance"), (btn, val) -> {
                    config.minRenderDistance = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Integer> minSimBtn = CycleButton.<Integer>builder(d -> text("value.chunks", d), config.minSimulationDistance)
                .withValues(List.of(3, 4, 5, 6, 8, 10))
                .withTooltip(val -> Tooltip.create(text("tooltip.min_simulation_distance")))
                .create(text("button.min_simulation_distance"), (btn, val) -> {
                    config.minSimulationDistance = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(minRenderBtn, minSimBtn);

        CycleButton<Double> minPctBtn = CycleButton.<Double>builder(pct -> Component.literal("%" + pct.intValue()), config.minRenderDistancePercent)
                .withValues(List.of(50.0, 60.0, 65.0, 70.0, 75.0, 80.0))
                .withTooltip(val -> Tooltip.create(text("tooltip.min_start_percent")))
                .create(text("button.min_start_percent"), (btn, val) -> {
                    config.minRenderDistancePercent = val;
                    config.minSimulationDistancePercent = val;
                    config.minEntityDistancePercent = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Double> minEntityBtn = CycleButton.<Double>builder(val -> Component.literal("%" + (int) (val * 100)), config.minEntityDistanceScaling)
                .withValues(List.of(0.25, 0.50, 0.65, 0.75, 1.0))
                .withTooltip(val -> Tooltip.create(text("tooltip.min_entity_distance")))
                .create(text("button.min_entity_distance"), (btn, val) -> {
                    config.minEntityDistanceScaling = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(minPctBtn, minEntityBtn);

        this.list.addHeader(header("timing"));

        CycleButton<Double> dropGraceBtn = CycleButton.<Double>builder(sec -> text("value.seconds", sec.intValue()), config.dropGracePeriodSeconds)
                .withValues(List.of(4.0, 5.0, 6.0, 8.0, 10.0))
                .withTooltip(val -> Tooltip.create(text("tooltip.drop_grace")))
                .create(text("button.drop_grace"), (btn, val) -> {
                    config.dropGracePeriodSeconds = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Double> riseGraceBtn = CycleButton.<Double>builder(sec -> text("value.seconds", sec.intValue()), config.riseGracePeriodSeconds)
                .withValues(List.of(2.0, 3.0, 5.0, 6.0, 8.0, 10.0))
                .withTooltip(val -> Tooltip.create(text("tooltip.rise_grace")))
                .create(text("button.rise_grace"), (btn, val) -> {
                    config.riseGracePeriodSeconds = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(dropGraceBtn, riseGraceBtn);

        CycleButton<Double> riseStabilityBtn = CycleButton.<Double>builder(sec -> text("value.seconds", sec.intValue()), config.riseStabilitySeconds)
                .withValues(List.of(3.0, 5.0, 8.0, 10.0, 15.0, 20.0))
                .withTooltip(val -> Tooltip.create(text("tooltip.rise_stability")))
                .create(text("button.rise_stability"), (btn, val) -> {
                    config.riseStabilitySeconds = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addBig(riseStabilityBtn);

        this.list.addHeader(header("rate_limits"));

        CycleButton<Integer> maxDropsBtn = CycleButton.<Integer>builder(cnt -> text("value.per_minute", cnt), config.maxDropsPerMinute)
                .withValues(List.of(5, 8, 10, 12, 15, 20))
                .withTooltip(val -> Tooltip.create(text("tooltip.max_drops")))
                .create(text("button.max_drops"), (btn, val) -> {
                    config.maxDropsPerMinute = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Integer> maxRisesBtn = CycleButton.<Integer>builder(cnt -> text("value.per_minute", cnt), config.maxRisesPerMinute)
                .withValues(List.of(3, 5, 8, 12, 15, 20))
                .withTooltip(val -> Tooltip.create(text("tooltip.max_rises")))
                .create(text("button.max_rises"), (btn, val) -> {
                    config.maxRisesPerMinute = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(maxDropsBtn, maxRisesBtn);

        this.list.addHeader(header("manual"));

        CycleButton<Integer> manualRenderBtn = CycleButton.<Integer>builder(d -> text("value.chunks", d), config.manualRenderDistance)
                .withValues(List.of(2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 32))
                .withTooltip(val -> Tooltip.create(text("tooltip.manual_render_distance")))
                .create(text("button.manual_render_distance"), (btn, val) -> {
                    config.manualRenderDistance = val;
                    RhodiumConfigManager.saveConfig();
                    if (config.mode == RhodiumMode.MANUAL) {
                        RhodiumClient.getController().applyManualSettings(this.minecraft);
                    }
                });

        CycleButton<Integer> manualSimBtn = CycleButton.<Integer>builder(d -> text("value.chunks", d), config.manualSimulationDistance)
                .withValues(List.of(3, 4, 5, 6, 8, 10, 12, 16))
                .withTooltip(val -> Tooltip.create(text("tooltip.manual_simulation_distance")))
                .create(text("button.manual_simulation_distance"), (btn, val) -> {
                    config.manualSimulationDistance = val;
                    RhodiumConfigManager.saveConfig();
                    if (config.mode == RhodiumMode.MANUAL) {
                        RhodiumClient.getController().applyManualSettings(this.minecraft);
                    }
                });

        this.list.addSmall(manualRenderBtn, manualSimBtn);

        CycleButton<String> manualParticlesBtn = CycleButton.<String>builder(p -> {
                    if ("MINIMAL".equals(p)) return text("particle.minimal");
                    if ("DECREASED".equals(p)) return text("particle.decreased");
                    return text("particle.all");
                }, config.manualParticles)
                .withValues(List.of("ALL", "DECREASED", "MINIMAL"))
                .withTooltip(val -> Tooltip.create(text("tooltip.manual_particles")))
                .create(text("button.manual_particles"), (btn, val) -> {
                    config.manualParticles = val;
                    RhodiumConfigManager.saveConfig();
                    if (config.mode == RhodiumMode.MANUAL) {
                        RhodiumClient.getController().applyManualSettings(this.minecraft);
                    }
                });

        CycleButton<Double> manualEntityBtn = CycleButton.<Double>builder(val -> Component.literal("%" + (int) (val * 100)), config.manualEntityDistanceScaling)
                .withValues(List.of(0.50, 0.75, 1.0, 1.25, 1.50, 2.0))
                .withTooltip(val -> Tooltip.create(text("tooltip.manual_entity_distance")))
                .create(text("button.manual_entity_distance"), (btn, val) -> {
                    config.manualEntityDistanceScaling = val;
                    RhodiumConfigManager.saveConfig();
                    if (config.mode == RhodiumMode.MANUAL) {
                        RhodiumClient.getController().applyManualSettings(this.minecraft);
                    }
                });

        this.list.addSmall(manualParticlesBtn, manualEntityBtn);

        this.list.addHeader(header("notifications"));

        CycleButton<Boolean> overlayMsgBtn = CycleButton.onOffBuilder(config.showOverlayMessages)
                .withTooltip(val -> Tooltip.create(text("tooltip.overlay_notifications")))
                .create(text("button.overlay_notifications"), (btn, val) -> {
                    config.showOverlayMessages = val;
                    RhodiumConfigManager.saveConfig();
                });

        CycleButton<Boolean> chatMsgBtn = CycleButton.onOffBuilder(config.showChatMessages)
                .withTooltip(val -> Tooltip.create(text("tooltip.chat_notifications")))
                .create(text("button.chat_notifications"), (btn, val) -> {
                    config.showChatMessages = val;
                    RhodiumConfigManager.saveConfig();
                });

        this.list.addSmall(overlayMsgBtn, chatMsgBtn);

        this.list.addHeader(header("reset"));

        Button resetBtn = Button.builder(text("button.reset").withStyle(ChatFormatting.RED), b -> {
            RhodiumConfigManager.resetToDefaults();
            if (this.minecraft != null && this.minecraft.gui != null) {
                this.minecraft.gui.setScreen(new RhodiumConfigScreen(this.lastScreen, this.options));
            }
        }).tooltip(Tooltip.create(text("tooltip.reset"))).build();

        this.list.addBig(resetBtn);
    }
}
