package com.rhodium.core;

import com.rhodium.config.RhodiumConfig;
import com.rhodium.config.RhodiumConfigManager;
import com.rhodium.config.RhodiumMode;
import com.rhodium.config.RhodiumOriginalSettings;
import com.rhodium.util.NotificationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AdaptiveQualityController {
    private static final Logger LOGGER = LoggerFactory.getLogger("Rhodium");

    public record DebugStep(String settingKey, String currentValue, String nextValue) {
    }

    public record DebugQuality(String renderCurrent, String renderCeiling,
                               String simulationCurrent, String simulationCeiling,
                               String entityCurrent, String entityCeiling,
                               String particlesCurrent, String particlesCeiling) {
    }

    public record DebugInfo(String modeKey, int currentFps, int targetFps,
                            String statusKey, int waitingSeconds, int preparationPercent,
                            DebugStep nextStep, DebugQuality quality) {
    }

    private record DebugReadinessResult(long readyAt, int preparationPercent) {
    }

    private final PerformanceSampler sampler = new PerformanceSampler();
    private RhodiumOriginalSettings baselineSettings = null;

    // Track the last values applied by Rhodium to detect user manual changes in the options menu
    private int lastAppliedRenderDistance = -1;
    private int lastAppliedSimulationDistance = -1;
    private double lastAppliedEntityDistanceScaling = -1.0;
    private ParticleStatus lastAppliedParticles = null;

    // Debounced options.save() (waits for 10s of silence before writing options.txt)
    private long pendingOptionsSaveTime = 0;

    // Stale sample avoidance
    private boolean wasSamplingPaused = true;

    // Timestamps for rate limits and hysteresis
    private long lastAdjustmentTime = 0;
    private long settingChangeGracePeriodUntil = 0; // Cooldown during which samples aren't used for decisions
    private long preventDropUntil = 0;              // Configured lockout after an upgrade
    private long nextAllowedRiseTime = 0;           // Configured cooldown between upgrades
    private long riseConditionStartTime = 0;        // Continuous eligible time for a rise
    private long worldJoinGracePeriodUntil = 0;     // 15s grace after joining a world

    // Rolling per-minute rate limiter lists
    private final List<Long> recentDropTimestamps = new ArrayList<>();
    private final List<Long> recentRiseTimestamps = new ArrayList<>();

    public void onWorldJoin(Minecraft client) {
        sampler.clear();
        recentDropTimestamps.clear();
        recentRiseTimestamps.clear();
        riseConditionStartTime = 0;
        wasSamplingPaused = true;

        long now = System.currentTimeMillis();
        RhodiumConfig config = RhodiumConfigManager.getConfig();
        worldJoinGracePeriodUntil = now + (long) (config.worldJoinGracePeriodSeconds * 1000);
        lastAdjustmentTime = now;
        settingChangeGracePeriodUntil = now + (long) (config.dropGracePeriodSeconds * 1000);

        if (baselineSettings == null) {
            captureBaseline(client);
        }

        // If manual mode, apply manual values immediately upon joining
        if (config.enabled && config.mode == RhodiumMode.MANUAL) {
            applyManualSettings(client);
        }
    }

    public void onWorldDisconnect(Minecraft client) {
        // Keep the quality level reached during play in Minecraft's options file.
        flushPendingOptionsSave(client);
        if (RhodiumConfigManager.getConfig().mode != RhodiumMode.AUTOMATIC) {
            clearBaseline();
        }
        sampler.clear();
        wasSamplingPaused = true;
    }

    public void loadPersistedBaseline(Minecraft client) {
        if (client == null || client.options == null) {
            return;
        }

        RhodiumOriginalSettings persisted = RhodiumConfigManager.loadOriginalSettings();
        if (persisted == null) {
            return;
        }

        baselineSettings = persisted;
        Options options = client.options;
        lastAppliedRenderDistance = options.renderDistance().get();
        lastAppliedSimulationDistance = options.simulationDistance().get();
        lastAppliedEntityDistanceScaling = options.entityDistanceScaling().get();
        lastAppliedParticles = options.particles().get();
        LOGGER.info("[Rhodium] Loaded persistent quality baseline: Render={}, Sim={}, Entity={}, Particles={}",
                baselineSettings.renderDistance, baselineSettings.simulationDistance,
                baselineSettings.entityDistanceScaling, baselineSettings.particles);
    }

    public void captureBaseline(Minecraft client) {
        if (client == null || client.options == null) {
            return;
        }

        Options options = client.options;
        baselineSettings = new RhodiumOriginalSettings(
                options.renderDistance().get(),
                options.simulationDistance().get(),
                options.entityDistanceScaling().get(),
                options.particles().get().name()
        );

        lastAppliedRenderDistance = baselineSettings.renderDistance;
        lastAppliedSimulationDistance = baselineSettings.simulationDistance;
        lastAppliedEntityDistanceScaling = baselineSettings.entityDistanceScaling;
        lastAppliedParticles = options.particles().get();

        RhodiumConfigManager.saveOriginalSettings(baselineSettings);
        LOGGER.info("[Rhodium] Baseline captured: Render={}, Sim={}, Entity={}, Particles={}",
                baselineSettings.renderDistance, baselineSettings.simulationDistance,
                baselineSettings.entityDistanceScaling, baselineSettings.particles);
    }

    public void restoreBaseline(Minecraft client, boolean notify) {
        if (baselineSettings == null || client == null || client.options == null) {
            return;
        }

        Options options = client.options;
        boolean changed = false;

        if (options.renderDistance().get() != baselineSettings.renderDistance) {
            options.renderDistance().set(baselineSettings.renderDistance);
            lastAppliedRenderDistance = baselineSettings.renderDistance;
            changed = true;
        }

        if (client.hasSingleplayerServer() && options.simulationDistance().get() != baselineSettings.simulationDistance) {
            options.simulationDistance().set(baselineSettings.simulationDistance);
            lastAppliedSimulationDistance = baselineSettings.simulationDistance;
            changed = true;
        }

        if (Math.abs(options.entityDistanceScaling().get() - baselineSettings.entityDistanceScaling) > 0.001) {
            options.entityDistanceScaling().set(baselineSettings.entityDistanceScaling);
            lastAppliedEntityDistanceScaling = baselineSettings.entityDistanceScaling;
            changed = true;
        }

        ParticleStatus targetParticles = parseParticleStatus(baselineSettings.particles);
        if (options.particles().get() != targetParticles) {
            options.particles().set(targetParticles);
            lastAppliedParticles = targetParticles;
            changed = true;
        }

        if (changed) {
            flushPendingOptionsSave(client);
            if (notify) {
                NotificationHelper.showMessage(client, Component.translatable("notification.rhodium.original_settings_restored"));
            }
        }
    }

    public void clearBaseline() {
        baselineSettings = null;
        lastAppliedRenderDistance = -1;
        lastAppliedSimulationDistance = -1;
        lastAppliedEntityDistanceScaling = -1.0;
        lastAppliedParticles = null;
        RhodiumConfigManager.deleteOriginalSettings();
    }

    public void applyManualSettings(Minecraft client) {
        if (client == null || client.options == null) {
            return;
        }
        RhodiumConfig config = RhodiumConfigManager.getConfig();
        Options options = client.options;
        boolean changed = false;

        if (options.renderDistance().get() != config.manualRenderDistance) {
            options.renderDistance().set(config.manualRenderDistance);
            changed = true;
        }

        if (client.hasSingleplayerServer() && options.simulationDistance().get() != config.manualSimulationDistance) {
            options.simulationDistance().set(config.manualSimulationDistance);
            changed = true;
        }

        if (Math.abs(options.entityDistanceScaling().get() - config.manualEntityDistanceScaling) > 0.001) {
            options.entityDistanceScaling().set(config.manualEntityDistanceScaling);
            changed = true;
        }

        ParticleStatus targetParticles = parseParticleStatus(config.manualParticles);
        if (options.particles().get() != targetParticles) {
            options.particles().set(targetParticles);
            changed = true;
        }

        if (changed) {
            flushPendingOptionsSave(client);
        }
    }

    public void flushPendingOptionsSave(Minecraft client) {
        if (client != null && client.options != null) {
            client.options.save();
        }
        pendingOptionsSaveTime = 0;
    }

    public void onClientTick(Minecraft client) {
        RhodiumConfig config = RhodiumConfigManager.getConfig();
        if (!config.enabled) {
            resetSamplingState();
            return;
        }

        // Process debounced options.save()
        long now = System.currentTimeMillis();
        if (pendingOptionsSaveTime > 0 && now >= pendingOptionsSaveTime) {
            if (client.options != null) {
                client.options.save();
                LOGGER.debug("[Rhodium] Debounced options.save() executed after silence.");
            }
            pendingOptionsSaveTime = 0;
        }

        // Check user manual override in options menu (Section 1b)
        checkUserManualOverride(client);

        // In MANUAL mode, automatic measurements and adjustments are halted (Section 2)
        if (config.mode == RhodiumMode.MANUAL) {
            resetSamplingState();
            return;
        }

        // Safety rules: pause, unfocused, in-menu, loading (Section 1d)
        boolean canSampleNow = sampler.canSample(client);
        if (!canSampleNow) {
            wasSamplingPaused = true;
            riseConditionStartTime = 0;
            return;
        }

        // Returning from pause/menu: discard stale samples!
        if (wasSamplingPaused) {
            wasSamplingPaused = false;
            sampler.clear();
            settingChangeGracePeriodUntil = now + (long) (config.dropGracePeriodSeconds * 1000);
            return;
        }

        // Record frame time from Minecraft client (in milliseconds)
        long frameTimeNs = client.getFrameTimeNs();
        if (frameTimeNs > 0) {
            sampler.recordSample(frameTimeNs / 1_000_000.0);
        }

        // Setting change grace period (samples ignored during setting change cooldown)
        if (now < settingChangeGracePeriodUntil) {
            riseConditionStartTime = 0;
            return;
        }

        // Require at least 20 samples (1 second) before making decisions
        if (sampler.getSampleCount() < 20) {
            return;
        }

        double avgFrameTimeMs = sampler.getAverageFrameTimeMs();
        double frameBudgetMs = 1000.0 / config.targetFps;
        double configuredDropThresholdMs = frameBudgetMs * (config.dropThresholdPercent / 100.0);
        double dropThresholdMs = configuredDropThresholdMs;
        double riseThresholdMs = frameBudgetMs * (config.riseThresholdPercent / 100.0);
        double bufferedRiseFrameBudgetMs = frameBudgetMs / (1.0 + config.upgradeMarginPercent / 100.0);

        // Clean up per-minute counters
        pruneTimestamps(recentDropTimestamps, now);
        pruneTimestamps(recentRiseTimestamps, now);

        boolean pinnedToCap = sampler.isPinnedToAnyCap(client, avgFrameTimeMs);
        boolean pinnedAtOrAboveTarget = pinnedToCap && client.getFps() >= config.targetFps;
        boolean comfortablePerformance = hasComfortablePerformance(avgFrameTimeMs, frameBudgetMs,
                riseThresholdMs, bufferedRiseFrameBudgetMs, pinnedAtOrAboveTarget);

        // Gather a stable sample window during join grace without changing settings.
        if (now < worldJoinGracePeriodUntil) {
            if (comfortablePerformance) {
                if (riseConditionStartTime == 0) {
                    riseConditionStartTime = now;
                }
            } else {
                riseConditionStartTime = 0;
            }
            return;
        }

        // 1. EVALUATE LOWERING (Düşürme)
        // Ignore a cap only when it already meets the user's target; a lower cap must not hide under-target FPS.
        if (avgFrameTimeMs > dropThresholdMs && !pinnedAtOrAboveTarget) {
            // Cannot drop while the post-rise stability lockout is active.
            if (now >= preventDropUntil) {
                // Use the shorter grace period when lag is more than twice the target budget.
                double neededGrace = (avgFrameTimeMs > (2.0 * frameBudgetMs))
                        ? config.urgentDropGracePeriodSeconds
                        : config.dropGracePeriodSeconds;

                if (now >= lastAdjustmentTime + (long) (neededGrace * 1000)) {
                    if (recentDropTimestamps.size() < config.maxDropsPerMinute) {
                        boolean lowered = tryLowerQuality(client, config);
                        if (lowered) {
                            lastAdjustmentTime = now;
                            settingChangeGracePeriodUntil = now + (long) (neededGrace * 1000);
                            pendingOptionsSaveTime = now + 10_000; // 10 seconds debounced save
                            recentDropTimestamps.add(now);
                            riseConditionStartTime = 0;
                            return;
                        }
                    }
                }
            }
        }

        // 2. EVALUATE RAISING (Yükseltme)
        if (comfortablePerformance) {
            if (riseConditionStartTime == 0) {
                riseConditionStartTime = now;
            }

            long stableDurationMs = now - riseConditionStartTime;
            long requiredStabilityMs = (long) (config.riseStabilitySeconds * 1000);

            // Require stable performance, then allow quality to recover in small steps.
            if (stableDurationMs >= requiredStabilityMs && now >= nextAllowedRiseTime) {
                if (recentRiseTimestamps.size() < config.maxRisesPerMinute) {
                    boolean raised = tryRaiseQuality(client, config);
                    if (raised) {
                        lastAdjustmentTime = now;
                        settingChangeGracePeriodUntil = now + 1_000;
                        nextAllowedRiseTime = now + (long) (config.riseGracePeriodSeconds * 1000);
                        preventDropUntil = now + (long) (config.postRiseDropLockoutSeconds * 1000);
                        pendingOptionsSaveTime = now + 10_000; // 10 seconds debounced save
                        recentRiseTimestamps.add(now);
                        riseConditionStartTime = 0;
                    }
                }
            }
        } else {
            // Interrupted stability
            riseConditionStartTime = 0;
        }
    }

    private void resetSamplingState() {
        if (!wasSamplingPaused) {
            sampler.clear();
            wasSamplingPaused = true;
        }
        riseConditionStartTime = 0;
    }

    private boolean hasComfortablePerformance(double averageFrameTimeMs, double frameBudgetMs,
                                               double riseThresholdMs, double bufferedRiseFrameBudgetMs,
                                               boolean pinnedAtOrAboveTarget) {
        if (pinnedAtOrAboveTarget) {
            return true;
        }

        return averageFrameTimeMs < riseThresholdMs
                && averageFrameTimeMs < bufferedRiseFrameBudgetMs
                && sampler.getNinetiethPercentileFrameTimeMs() < frameBudgetMs;
    }

    /**
     * Builds a read-only snapshot for the F3 overlay. This method is called by
     * the debug entry only while the vanilla debug screen is being displayed.
     */
    public DebugInfo getDebugInfo(Minecraft client) {
        RhodiumConfig config = RhodiumConfigManager.getConfig();
        int currentFps = client == null ? 0 : client.getFps();

        if (!config.enabled) {
            return new DebugInfo("debug.rhodium.mode.disabled", currentFps, config.targetFps,
                    "debug.rhodium.status.ok", 0, 0, null, null);
        }
        if (config.mode == RhodiumMode.MANUAL) {
            return new DebugInfo("debug.rhodium.mode.manual", currentFps, config.targetFps,
                    "debug.rhodium.status.ok", 0, 0, null, null);
        }
        if (client == null || client.options == null) {
            return new DebugInfo("debug.rhodium.mode.automatic", currentFps, config.targetFps,
                    "debug.rhodium.status.paused", 0, 0, null, null);
        }

        Options options = client.options;
        DebugQuality quality = getDebugQuality(options, config);

        if (!sampler.canSample(client)) {
            return new DebugInfo("debug.rhodium.mode.automatic", currentFps, config.targetFps,
                    "debug.rhodium.status.paused", 0, 0, null, quality);
        }

        long now = System.currentTimeMillis();
        int sampleCount = sampler.getSampleCount();
        if (sampleCount < 20) {
            DebugReadinessResult warmup = getDebugWarmupReadiness(config, now, sampleCount);
            return new DebugInfo("debug.rhodium.mode.automatic", currentFps, config.targetFps,
                    "debug.rhodium.status.waiting", secondsUntil(now, warmup.readyAt()),
                    warmup.preparationPercent(), null, quality);
        }

        double averageFrameTimeMs = sampler.getAverageFrameTimeMs();
        double frameBudgetMs = 1000.0 / config.targetFps;
        double dropThresholdMs = frameBudgetMs * (config.dropThresholdPercent / 100.0);
        double riseThresholdMs = frameBudgetMs * (config.riseThresholdPercent / 100.0);
        double bufferedRiseFrameBudgetMs = frameBudgetMs / (1.0 + config.upgradeMarginPercent / 100.0);
        boolean pinnedToCap = sampler.isPinnedToAnyCap(client, averageFrameTimeMs);
        boolean pinnedAtOrAboveTarget = pinnedToCap && currentFps >= config.targetFps;
        boolean shouldLower = averageFrameTimeMs > dropThresholdMs && !pinnedAtOrAboveTarget;
        boolean shouldRaise = hasComfortablePerformance(averageFrameTimeMs, frameBudgetMs,
                riseThresholdMs, bufferedRiseFrameBudgetMs, pinnedAtOrAboveTarget);

        DebugStep nextStep = null;
        boolean lowering = false;
        if (shouldLower) {
            nextStep = getNextLowerDebugStep(client, config);
            lowering = nextStep != null;
        }
        if (nextStep == null && shouldRaise) {
            nextStep = getNextRaiseDebugStep(client, config);
        }

        if (nextStep != null) {
            DebugReadinessResult readiness = getDebugReadiness(config, now, averageFrameTimeMs,
                    sampleCount, lowering);
            String statusKey = readiness.readyAt() > now
                    ? "debug.rhodium.status.waiting"
                    : lowering ? "debug.rhodium.status.lowering" : "debug.rhodium.status.raising";
            return new DebugInfo("debug.rhodium.mode.automatic", currentFps, config.targetFps,
                    statusKey, secondsUntil(now, readiness.readyAt()), readiness.preparationPercent(),
                    nextStep, quality);
        }

        String statusKey;
        if (shouldLower) {
            statusKey = "debug.rhodium.status.lowering";
        } else if (pinnedAtOrAboveTarget) {
            statusKey = "debug.rhodium.status.cap";
        } else {
            statusKey = "debug.rhodium.status.ok";
        }
        return new DebugInfo("debug.rhodium.mode.automatic", currentFps, config.targetFps,
                statusKey, 0, 0, null, quality);
    }

    private DebugQuality getDebugQuality(Options options, RhodiumConfig config) {
        String dash = Component.translatable("debug.rhodium.unmanaged").getString();
        String renderCurrent = config.manageRenderDistance ? Integer.toString(options.renderDistance().get()) : dash;
        String renderCeiling = config.manageRenderDistance && baselineSettings != null
                ? Integer.toString(config.maxRenderDistance) : dash;
        String simulationCurrent = config.manageSimulationDistance
                ? Integer.toString(options.simulationDistance().get()) : dash;
        String simulationCeiling = config.manageSimulationDistance && baselineSettings != null
                ? Integer.toString(config.maxSimulationDistance) : dash;
        String entityCurrent = config.manageEntityDistance
                ? percentValue(options.entityDistanceScaling().get()) : dash;
        String entityCeiling = config.manageEntityDistance && baselineSettings != null
                ? percentValue(baselineSettings.entityDistanceScaling) : dash;
        String particlesCurrent = config.manageParticles
                ? getParticleLabel(options.particles().get()) : dash;
        String particlesCeiling = config.manageParticles && baselineSettings != null
                ? getParticleLabel(parseParticleStatus(baselineSettings.particles)) : dash;

        return new DebugQuality(renderCurrent, renderCeiling,
                simulationCurrent, simulationCeiling, entityCurrent, entityCeiling,
                particlesCurrent, particlesCeiling);
    }

    private DebugStep getNextLowerDebugStep(Minecraft client, RhodiumConfig config) {
        if (baselineSettings == null) {
            return null;
        }

        Options options = client.options;
        if (config.manageParticles) {
            ParticleStatus current = options.particles().get();
            if (current == ParticleStatus.ALL) {
                return new DebugStep("config.rhodium.button.particles",
                        getParticleLabel(current), getParticleLabel(ParticleStatus.DECREASED));
            }
            if (current == ParticleStatus.DECREASED) {
                return new DebugStep("config.rhodium.button.particles",
                        getParticleLabel(current), getParticleLabel(ParticleStatus.MINIMAL));
            }
        }

        if (config.manageEntityDistance) {
            double current = options.entityDistanceScaling().get();
            double entityMinPercentVal = baselineSettings.entityDistanceScaling * (config.minEntityDistancePercent / 100.0);
            double effectiveEntityMin = Math.max(config.minEntityDistanceScaling, entityMinPercentVal);
            if (current > effectiveEntityMin + 0.01) {
                double next = Math.max(effectiveEntityMin,
                        Math.round((current - config.entityDistanceStep) * 100.0) / 100.0);
                return new DebugStep("config.rhodium.button.entity_distance",
                        percentValue(current), percentValue(next));
            }
        }

        if (config.manageSimulationDistance && client.hasSingleplayerServer()) {
            int current = options.simulationDistance().get();
            int simMinPercentVal = (int) Math.ceil(baselineSettings.simulationDistance * (config.minSimulationDistancePercent / 100.0));
            int effectiveSimMin = Math.max(config.minSimulationDistance, simMinPercentVal);
            if (current > effectiveSimMin) {
                return new DebugStep("config.rhodium.button.simulation_distance",
                        Integer.toString(current), Integer.toString(current - 1));
            }
        }

        if (config.manageRenderDistance) {
            int current = options.renderDistance().get();
            int renderMinPercentVal = (int) Math.ceil(baselineSettings.renderDistance * (config.minRenderDistancePercent / 100.0));
            int effectiveRenderMin = Math.max(config.minRenderDistance, renderMinPercentVal);
            if (current > effectiveRenderMin) {
                return new DebugStep("config.rhodium.button.render_distance",
                        Integer.toString(current), Integer.toString(current - 1));
            }
        }
        return null;
    }

    private DebugStep getNextRaiseDebugStep(Minecraft client, RhodiumConfig config) {
        if (baselineSettings == null) {
            return null;
        }

        Options options = client.options;
        int renderCeiling = config.maxRenderDistance;
        int phaseOneRenderTarget = client.hasSingleplayerServer()
                ? Math.min(renderCeiling, config.phaseOneRenderDistance)
                : renderCeiling;

        if (config.manageRenderDistance) {
            int current = options.renderDistance().get();
            if (current < phaseOneRenderTarget) {
                return new DebugStep("config.rhodium.button.render_distance",
                        Integer.toString(current), Integer.toString(current + 1));
            }
        }

        if (config.manageSimulationDistance && client.hasSingleplayerServer()) {
            int current = options.simulationDistance().get();
            if (current < config.maxSimulationDistance) {
                return new DebugStep("config.rhodium.button.simulation_distance",
                        Integer.toString(current), Integer.toString(current + 1));
            }
        }

        if (config.manageEntityDistance) {
            double current = options.entityDistanceScaling().get();
            if (current < baselineSettings.entityDistanceScaling - 0.01) {
                double next = Math.min(baselineSettings.entityDistanceScaling,
                        Math.round((current + config.entityDistanceStep) * 100.0) / 100.0);
                return new DebugStep("config.rhodium.button.entity_distance",
                        percentValue(current), percentValue(next));
            }
        }

        if (config.manageParticles) {
            ParticleStatus current = options.particles().get();
            ParticleStatus starting = parseParticleStatus(baselineSettings.particles);
            if (starting != ParticleStatus.MINIMAL && current == ParticleStatus.MINIMAL) {
                return new DebugStep("config.rhodium.button.particles",
                        getParticleLabel(current), getParticleLabel(ParticleStatus.DECREASED));
            }
            if (starting == ParticleStatus.ALL && current == ParticleStatus.DECREASED) {
                return new DebugStep("config.rhodium.button.particles",
                        getParticleLabel(current), getParticleLabel(ParticleStatus.ALL));
            }
        }

        if (config.manageRenderDistance) {
            int current = options.renderDistance().get();
            if (current < renderCeiling) {
                return new DebugStep("config.rhodium.button.render_distance",
                        Integer.toString(current), Integer.toString(current + 1));
            }
        }
        return null;
    }

    private DebugReadinessResult getDebugWarmupReadiness(RhodiumConfig config, long now, int sampleCount) {
        DebugReadinessBuilder readiness = new DebugReadinessBuilder(now);
        readiness.includeSamples(now, sampleCount);
        readiness.include(now, worldJoinGracePeriodUntil,
                (long) (config.worldJoinGracePeriodSeconds * 1000));
        readiness.include(now, settingChangeGracePeriodUntil,
                settingChangeGracePeriodUntil - lastAdjustmentTime);
        return readiness.finish();
    }

    private DebugReadinessResult getDebugReadiness(RhodiumConfig config, long now,
                                                   double averageFrameTimeMs, int sampleCount, boolean lowering) {
        DebugReadinessBuilder readiness = new DebugReadinessBuilder(now);
        readiness.includeSamples(now, sampleCount);
        readiness.include(now, worldJoinGracePeriodUntil,
                (long) (config.worldJoinGracePeriodSeconds * 1000));

        long settingGraceDuration = settingChangeGracePeriodUntil - lastAdjustmentTime;
        if (settingGraceDuration <= 0) {
            settingGraceDuration = (long) (config.dropGracePeriodSeconds * 1000);
        }
        readiness.include(now, settingChangeGracePeriodUntil, settingGraceDuration);

        if (lowering) {
            double frameBudgetMs = 1000.0 / config.targetFps;
            double neededGraceSeconds = averageFrameTimeMs > (2.0 * frameBudgetMs)
                    ? config.urgentDropGracePeriodSeconds : config.dropGracePeriodSeconds;
            long graceDuration = (long) (neededGraceSeconds * 1000);
            readiness.include(now, lastAdjustmentTime + graceDuration, graceDuration);
            readiness.include(now, preventDropUntil,
                    (long) (config.postRiseDropLockoutSeconds * 1000));
            readiness.include(now, getRateLimitReadyAt(recentDropTimestamps, config.maxDropsPerMinute, now), 60_000);
        } else {
            long stabilityDuration = (long) (config.riseStabilitySeconds * 1000);
            long stabilityStart = riseConditionStartTime == 0 ? now : riseConditionStartTime;
            readiness.include(now, stabilityStart + stabilityDuration, stabilityDuration);
            long riseCooldown = (long) (config.riseGracePeriodSeconds * 1000);
            readiness.include(now, nextAllowedRiseTime, riseCooldown);
            readiness.include(now, getRateLimitReadyAt(recentRiseTimestamps, config.maxRisesPerMinute, now), 60_000);
        }
        return readiness.finish();
    }

    private long getRateLimitReadyAt(List<Long> timestamps, int limit, long now) {
        long windowStart = now - 60_000;
        int activeCount = 0;
        long oldestActive = Long.MAX_VALUE;
        for (long timestamp : timestamps) {
            if (timestamp >= windowStart) {
                activeCount++;
                oldestActive = Math.min(oldestActive, timestamp);
            }
        }
        if (limit <= 0) {
            return now + 60_000;
        }
        return activeCount >= limit ? oldestActive + 60_000 : now;
    }

    private static int secondsUntil(long now, long deadline) {
        if (deadline <= now) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (deadline - now + 999) / 1000);
    }

    private static String percentValue(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static String getParticleLabel(ParticleStatus status) {
        String key = switch (status) {
            case MINIMAL -> "config.rhodium.particle.minimal";
            case DECREASED -> "config.rhodium.particle.decreased";
            case ALL -> "config.rhodium.particle.all";
        };
        return Component.translatable(key).getString();
    }

    private static final class DebugReadinessBuilder {
        private long readyAt;
        private int preparationPercent = 100;

        private DebugReadinessBuilder(long now) {
            this.readyAt = now;
        }

        private void includeSamples(long currentTime, int sampleCount) {
            if (sampleCount >= 20) {
                return;
            }
            long missingSamplesMs = (20L - Math.max(0, sampleCount)) * 50L;
            readyAt = Math.max(readyAt, currentTime + missingSamplesMs);
            preparationPercent = Math.min(preparationPercent, Math.max(0, sampleCount) * 100 / 20);
        }

        private void include(long currentTime, long deadline, long fullDurationMs) {
            if (deadline <= currentTime) {
                return;
            }
            readyAt = Math.max(readyAt, deadline);
            long duration = Math.max(1, fullDurationMs);
            long elapsed = currentTime - (deadline - duration);
            int progress = (int) Math.max(0, Math.min(100, elapsed * 100 / duration));
            preparationPercent = Math.min(preparationPercent, progress);
        }

        private DebugReadinessResult finish() {
            return new DebugReadinessResult(readyAt, preparationPercent);
        }
    }

    /**
     * Checks if the user manually modified any setting in Minecraft's options menu.
     * Updates baseline so user changes are never overwritten upon exit.
     */
    private void checkUserManualOverride(Minecraft client) {
        if (baselineSettings == null || client == null || client.options == null) {
            return;
        }

        Options options = client.options;
        boolean overrideDetected = false;

        int curRender = options.renderDistance().get();
        if (lastAppliedRenderDistance != -1 && curRender != lastAppliedRenderDistance) {
            baselineSettings.renderDistance = curRender;
            lastAppliedRenderDistance = curRender;
            overrideDetected = true;
        }

        if (client.hasSingleplayerServer()) {
            int curSim = options.simulationDistance().get();
            if (lastAppliedSimulationDistance != -1 && curSim != lastAppliedSimulationDistance) {
                baselineSettings.simulationDistance = curSim;
                lastAppliedSimulationDistance = curSim;
                overrideDetected = true;
            }
        }

        double curEntity = options.entityDistanceScaling().get();
        if (lastAppliedEntityDistanceScaling > 0 && Math.abs(curEntity - lastAppliedEntityDistanceScaling) > 0.001) {
            baselineSettings.entityDistanceScaling = curEntity;
            lastAppliedEntityDistanceScaling = curEntity;
            overrideDetected = true;
        }

        ParticleStatus curParticles = options.particles().get();
        if (lastAppliedParticles != null && curParticles != lastAppliedParticles) {
            baselineSettings.particles = curParticles.name();
            lastAppliedParticles = curParticles;
            overrideDetected = true;
        }

        if (overrideDetected) {
            RhodiumConfigManager.saveOriginalSettings(baselineSettings);
            LOGGER.info("[Rhodium] User manual override detected, updated baseline settings.");
        }
    }

    /**
     * Düşürme sırası: parçacıklar -> entity çizim mesafesi -> simülasyon mesafesi -> render mesafesi.
     * Respects individual manage toggles (config.manageX).
     */
    private boolean tryLowerQuality(Minecraft client, RhodiumConfig config) {
        if (baselineSettings == null) {
            return false;
        }
        Options options = client.options;

        // 1. Particles (ALL -> DECREASED -> MINIMAL)
        if (config.manageParticles) {
            ParticleStatus currentParticles = options.particles().get();
            if (currentParticles == ParticleStatus.ALL) {
                options.particles().set(ParticleStatus.DECREASED);
                lastAppliedParticles = ParticleStatus.DECREASED;
                showTranslatedMessage(client, "notification.rhodium.particles.changed",
                        Component.translatable("notification.rhodium.particles"),
                        Component.translatable("notification.rhodium.particle.all"),
                        Component.translatable("notification.rhodium.particle.decreased"));
                return true;
            } else if (currentParticles == ParticleStatus.DECREASED) {
                options.particles().set(ParticleStatus.MINIMAL);
                lastAppliedParticles = ParticleStatus.MINIMAL;
                showTranslatedMessage(client, "notification.rhodium.particles.changed",
                        Component.translatable("notification.rhodium.particles"),
                        Component.translatable("notification.rhodium.particle.decreased"),
                        Component.translatable("notification.rhodium.particle.minimal"));
                return true;
            }
        }

        // 2. Entity Distance Scaling
        if (config.manageEntityDistance) {
            double currentEntity = options.entityDistanceScaling().get();
            double entityMinPercentVal = baselineSettings.entityDistanceScaling * (config.minEntityDistancePercent / 100.0);
            double effectiveEntityMin = Math.max(config.minEntityDistanceScaling, entityMinPercentVal);

            if (currentEntity > effectiveEntityMin + 0.01) {
                double nextVal = Math.max(effectiveEntityMin, Math.round((currentEntity - config.entityDistanceStep) * 100.0) / 100.0);
                options.entityDistanceScaling().set(nextVal);
                lastAppliedEntityDistanceScaling = nextVal;
                showTranslatedMessage(client, "notification.rhodium.setting.changed",
                        Component.translatable("notification.rhodium.entity_distance"),
                        Math.round(currentEntity * 100) + "%", Math.round(nextVal * 100) + "%");
                return true;
            }
        }

        // 3. Simulation Distance (Singleplayer only!)
        if (config.manageSimulationDistance && client.hasSingleplayerServer()) {
            int currentSim = options.simulationDistance().get();
            int simMinPercentVal = (int) Math.ceil(baselineSettings.simulationDistance * (config.minSimulationDistancePercent / 100.0));
            int effectiveSimMin = Math.max(config.minSimulationDistance, simMinPercentVal);

            if (currentSim > effectiveSimMin) {
                int nextVal = currentSim - 1; // Maximum 1 chunk step
                options.simulationDistance().set(nextVal);
                lastAppliedSimulationDistance = nextVal;
                showTranslatedMessage(client, "notification.rhodium.setting.changed",
                        Component.translatable("notification.rhodium.simulation_distance"), currentSim, nextVal);
                return true;
            }
        }

        // 4. Render Distance
        if (config.manageRenderDistance) {
            int currentRender = options.renderDistance().get();
            int renderMinPercentVal = (int) Math.ceil(baselineSettings.renderDistance * (config.minRenderDistancePercent / 100.0));
            int effectiveRenderMin = Math.max(config.minRenderDistance, renderMinPercentVal);

            if (currentRender > effectiveRenderMin) {
                int nextVal = currentRender - 1; // Maximum 1 chunk step
                options.renderDistance().set(nextVal);
                lastAppliedRenderDistance = nextVal;
                showTranslatedMessage(client, "notification.rhodium.setting.changed",
                        Component.translatable("notification.rhodium.render_distance"), currentRender, nextVal);
                return true;
            }
        }

        return false;
    }

    /**
     * Raise render distance to its phase-one target, then simulation, entity,
     * particles, and finally render distance to its configured maximum.
     * Respects individual manage toggles (config.manageX).
     */
    private boolean tryRaiseQuality(Minecraft client, RhodiumConfig config) {
        if (baselineSettings == null) {
            return false;
        }
        Options options = client.options;

        int renderCeiling = config.maxRenderDistance;
        int phaseOneRenderTarget = client.hasSingleplayerServer()
                ? Math.min(renderCeiling, config.phaseOneRenderDistance)
                : renderCeiling;

        // 1. Render Distance phase one. In multiplayer this restores the
        // configured maximum directly because simulation is skipped.
        if (config.manageRenderDistance) {
            int currentRender = options.renderDistance().get();
            if (currentRender < phaseOneRenderTarget) {
                int nextVal = currentRender + 1; // Increase one chunk per step
                options.renderDistance().set(nextVal);
                lastAppliedRenderDistance = nextVal;
                showTranslatedMessage(client, "notification.rhodium.setting.changed",
                        Component.translatable("notification.rhodium.render_distance"), currentRender, nextVal);
                return true;
            }
        }

        // 2. Simulation Distance (Singleplayer only!)
        if (config.manageSimulationDistance && client.hasSingleplayerServer()) {
            int currentSim = options.simulationDistance().get();
            int simulationTarget = config.maxSimulationDistance;
            if (currentSim < simulationTarget) {
                int nextVal = currentSim + 1; // Maximum 1 chunk step
                options.simulationDistance().set(nextVal);
                lastAppliedSimulationDistance = nextVal;
                showTranslatedMessage(client, "notification.rhodium.setting.changed",
                        Component.translatable("notification.rhodium.simulation_distance"), currentSim, nextVal);
                return true;
            }
        }

        // 3. Entity Distance Scaling
        if (config.manageEntityDistance) {
            double currentEntity = options.entityDistanceScaling().get();
            if (currentEntity < baselineSettings.entityDistanceScaling - 0.01) {
                double nextVal = Math.min(baselineSettings.entityDistanceScaling, Math.round((currentEntity + config.entityDistanceStep) * 100.0) / 100.0);
                options.entityDistanceScaling().set(nextVal);
                lastAppliedEntityDistanceScaling = nextVal;
                showTranslatedMessage(client, "notification.rhodium.setting.changed",
                        Component.translatable("notification.rhodium.entity_distance"),
                        Math.round(currentEntity * 100) + "%", Math.round(nextVal * 100) + "%");
                return true;
            }
        }

        // 4. Particles: restore one level, without exceeding the saved setting.
        if (config.manageParticles) {
            ParticleStatus currentParticles = options.particles().get();
            ParticleStatus startingParticles = parseParticleStatus(baselineSettings.particles);

            if (startingParticles != ParticleStatus.MINIMAL && currentParticles == ParticleStatus.MINIMAL) {
                options.particles().set(ParticleStatus.DECREASED);
                lastAppliedParticles = ParticleStatus.DECREASED;
                showTranslatedMessage(client, "notification.rhodium.particles.changed",
                        Component.translatable("notification.rhodium.particles"),
                        Component.translatable("notification.rhodium.particle.minimal"),
                        Component.translatable("notification.rhodium.particle.decreased"));
                return true;
            } else if (startingParticles == ParticleStatus.ALL && currentParticles == ParticleStatus.DECREASED) {
                options.particles().set(ParticleStatus.ALL);
                lastAppliedParticles = ParticleStatus.ALL;
                showTranslatedMessage(client, "notification.rhodium.particles.changed",
                        Component.translatable("notification.rhodium.particles"),
                        Component.translatable("notification.rhodium.particle.decreased"),
                        Component.translatable("notification.rhodium.particle.all"));
                return true;
            }
        }

        // 5. Render Distance phase two: restore the remaining distance above
        // phase one, up to (and never beyond) the configured maximum.
        if (config.manageRenderDistance) {
            int currentRender = options.renderDistance().get();
            if (currentRender < renderCeiling) {
                int nextVal = currentRender + 1;
                options.renderDistance().set(nextVal);
                lastAppliedRenderDistance = nextVal;
                showTranslatedMessage(client, "notification.rhodium.setting.changed",
                        Component.translatable("notification.rhodium.render_distance"), currentRender, nextVal);
                return true;
            }
        }

        return false;
    }

    private void pruneTimestamps(List<Long> timestamps, long now) {
        long oneMinuteAgo = now - 60_000;
        Iterator<Long> iterator = timestamps.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() < oneMinuteAgo) {
                iterator.remove();
            }
        }
    }

    private ParticleStatus parseParticleStatus(String name) {
        try {
            return ParticleStatus.valueOf(name);
        } catch (Exception e) {
            return ParticleStatus.ALL;
        }
    }

    private void showTranslatedMessage(Minecraft client, String key, Object... args) {
        NotificationHelper.showMessage(client, Component.translatable(key, args));
    }
}
