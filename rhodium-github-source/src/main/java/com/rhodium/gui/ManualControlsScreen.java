package com.rhodium.gui;

import com.rhodium.RhodiumClient;
import com.rhodium.config.RhodiumConfig;
import com.rhodium.config.RhodiumConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** In-game controls for the player's manual quality preset. */
public final class ManualControlsScreen extends Screen {
    private static final int SLIDER_TURQUOISE = 0xFF27D9C8;
    private static final int SLIDER_TRACK = 0xFF193C42;
    private static final int SLIDER_HANDLE = 0xFFB6FFF6;
    private static final int PANEL_BACKGROUND = 0xE6091014;
    private static final int PANEL_HEADER = 0xF0142529;
    private static final int PANEL_BORDER = 0xFF020607;

    private int renderDistance;
    private int entityDistancePercent;
    private int simulationDistance;
    private String particles;
    private boolean renderDistanceChanged;
    private boolean entityDistanceChanged;
    private boolean simulationDistanceChanged;
    private boolean particlesChanged;
    private int dialogTop;

    public ManualControlsScreen() {
        super(Component.translatable("screen.rhodium.manual.title"));

        RhodiumConfig config = RhodiumConfigManager.getConfig();
        renderDistance = config.manualRenderDistance;
        entityDistancePercent = (int) Math.round(config.manualEntityDistanceScaling * 100.0);
        simulationDistance = config.manualSimulationDistance;
        particles = config.manualParticles;
    }

    @Override
    protected void init() {
        int sliderWidth = Math.min(360, Math.max(160, width - 32));
        int sliderX = (width - sliderWidth) / 2;
        dialogTop = Math.max(16, (height - 270) / 2);

        addRenderableWidget(new ManualSlider(
                sliderX, dialogTop + 42, sliderWidth, font,
                renderDistance, 2, 32,
                value -> Component.translatable("screen.rhodium.manual.render_distance",
                        Component.translatable("config.rhodium.value.chunks", value)),
                value -> {
                    renderDistance = value;
                    renderDistanceChanged = true;
                }
        ));

        addRenderableWidget(new ManualSlider(
                sliderX, dialogTop + 86, sliderWidth, font,
                entityDistancePercent, 50, 500,
                value -> Component.translatable("screen.rhodium.manual.entity_distance",
                        Component.literal(value + "%")),
                value -> {
                    entityDistancePercent = value;
                    entityDistanceChanged = true;
                }
        ));

        addRenderableWidget(new ManualSlider(
                sliderX, dialogTop + 130, sliderWidth, font,
                simulationDistance, 5, 32,
                value -> Component.translatable("screen.rhodium.manual.simulation_distance",
                        Component.translatable("config.rhodium.value.chunks", value)),
                value -> {
                    simulationDistance = value;
                    simulationDistanceChanged = true;
                }
        ));

        CycleButton<String> particleButton = CycleButton.<String>builder(
                        ManualControlsScreen::particleName, particles)
                .withValues(List.of("MINIMAL", "DECREASED", "ALL"))
                .create(width / 2 - 140, dialogTop + 193, 280, 20,
                        Component.translatable("screen.rhodium.manual.particles"), (button, value) -> {
                            particles = value;
                            particlesChanged = true;
                        });
        addRenderableWidget(particleButton);

        int buttonY = dialogTop + 235;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> applyAndClose())
                .bounds(width / 2 - 105, buttonY, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 5, buttonY, 100, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, dialogTop + 5, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.rhodium.manual.hint",
                        Component.translatable("gui.done")),
                width / 2, dialogTop + 24, 0xFFCFD8DC);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int panelLeft = width / 2 - 204;
        int panelRight = width / 2 + 204;
        int panelBottom = dialogTop + 270;
        graphics.fill(panelLeft, dialogTop, panelRight, panelBottom, PANEL_BACKGROUND);
        graphics.outline(panelLeft, dialogTop, panelRight - panelLeft, panelBottom - dialogTop, PANEL_BORDER);
        graphics.outline(panelLeft + 2, dialogTop + 2, panelRight - panelLeft - 4,
                panelBottom - dialogTop - 4, 0xFF16464A);
        graphics.fill(panelLeft + 3, dialogTop + 3, panelRight - 3, dialogTop + 39, PANEL_HEADER);
        graphics.fill(panelLeft + 3, dialogTop + 39, panelRight - 3, dialogTop + 41, SLIDER_TURQUOISE);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }

    private void applyAndClose() {
        boolean changed = renderDistanceChanged || entityDistanceChanged
                || simulationDistanceChanged || particlesChanged;
        if (changed) {
            RhodiumConfig config = RhodiumConfigManager.getConfig();
            if (renderDistanceChanged) {
                config.manualRenderDistance = renderDistance;
            }
            if (entityDistanceChanged) {
                config.manualEntityDistanceScaling = entityDistancePercent / 100.0;
            }
            if (simulationDistanceChanged) {
                config.manualSimulationDistance = simulationDistance;
            }
            if (particlesChanged) {
                config.manualParticles = particles;
            }
            RhodiumConfigManager.saveConfig();
            RhodiumClient.getController().applyManualSettings(minecraft);
        }
        minecraft.gui.setScreen(null);
    }

    private static Component particleName(String value) {
        return Component.translatable("config.rhodium.particle." + value.toLowerCase(java.util.Locale.ROOT));
    }

    private static final class ManualSlider extends AbstractSliderButton {
        private final int minimum;
        private final int maximum;
        private final int initialValue;
        private final Font font;
        private final IntFunction<Component> messageFactory;
        private final IntConsumer valueConsumer;
        private boolean interacted;

        private ManualSlider(int x, int y, int width, Font font, int initialValue, int minimum, int maximum,
                             IntFunction<Component> messageFactory, IntConsumer valueConsumer) {
            super(x, y, width, 32, Component.empty(), normalizedValue(initialValue, minimum, maximum));
            this.minimum = minimum;
            this.maximum = maximum;
            this.initialValue = initialValue;
            this.font = font;
            this.messageFactory = messageFactory;
            this.valueConsumer = valueConsumer;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int shownValue = interacted ? sliderValue() : initialValue;
            setMessage(messageFactory.apply(shownValue));
        }

        @Override
        protected void applyValue() {
            interacted = true;
            valueConsumer.accept(sliderValue());
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            interacted = true;
            super.onClick(event, doubleClick);
            applyValue();
            updateMessage();
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int left = getX() + 4;
            int right = getX() + getWidth() - 4;
            int trackY = getY() + 21;
            int handleCenter = left + (int) Math.round((right - left) * value);

            graphics.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + 2, 0xFFFFFFFF);
            graphics.fill(left - 6, trackY - 5, right + 6, trackY + 11, PANEL_BORDER);
            graphics.outline(left - 6, trackY - 5, right - left + 12, 16, 0xFF276E70);
            graphics.fill(left - 2, trackY - 1, right + 2, trackY + 7, SLIDER_TRACK);
            graphics.fill(left - 2, trackY - 1, handleCenter, trackY + 7, SLIDER_TURQUOISE);
            graphics.fill(handleCenter - 5, trackY - 4, handleCenter + 5, trackY + 10, PANEL_BORDER);
            graphics.outline(handleCenter - 5, trackY - 4, 10, 14, 0xFF46D8CC);
            graphics.fill(handleCenter - 2, trackY - 1, handleCenter + 3, trackY + 7, SLIDER_HANDLE);
        }

        private int sliderValue() {
            return minimum + (int) Math.round(value * (maximum - minimum));
        }

        private static double normalizedValue(int initialValue, int minimum, int maximum) {
            int clamped = Math.max(minimum, Math.min(maximum, initialValue));
            return (clamped - minimum) / (double) (maximum - minimum);
        }
    }
}
