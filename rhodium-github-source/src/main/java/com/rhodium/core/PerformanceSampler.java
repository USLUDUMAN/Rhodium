package com.rhodium.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Samples frame times and detects all forms of framerate capping conditions:
 * - User maximum FPS limit
 * - VSync / display refresh rate
 * - Inactivity / AFK throttling from Mojang's FramerateLimitTracker (or 30 FPS fallback)
 */
public class PerformanceSampler {
    // 60 tick samples at 20 ticks/sec corresponds to the last 3.0 seconds
    private static final int MAX_SAMPLES = 60;
    private final Deque<Double> frameTimeSamples = new ArrayDeque<>();
    private double totalFrameTimeMs;
    private double cachedNinetiethPercentileFrameTimeMs;
    private boolean percentileCacheDirty = true;

    public void clear() {
        frameTimeSamples.clear();
        totalFrameTimeMs = 0.0;
        cachedNinetiethPercentileFrameTimeMs = 0.0;
        percentileCacheDirty = true;
    }

    /**
     * Determines whether the current state allows sampling according to the safety rules.
     */
    public boolean canSample(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            return false;
        }

        // Do not sample if paused, in any screen/menu, or window is unfocused
        boolean hasScreenOpen = (client.gui != null && client.gui.screen() != null);
        if (client.isPaused() || hasScreenOpen || !client.isWindowActive()) {
            return false;
        }

        return true;
    }

    /**
     * Records a frame time sample in milliseconds.
     */
    public void recordSample(double frameTimeMs) {
        if (frameTimeMs <= 0.0) {
            return;
        }

        if (frameTimeSamples.size() >= MAX_SAMPLES) {
            totalFrameTimeMs -= frameTimeSamples.pollFirst();
        }
        frameTimeSamples.addLast(frameTimeMs);
        totalFrameTimeMs += frameTimeMs;
        percentileCacheDirty = true;
    }

    public int getSampleCount() {
        return frameTimeSamples.size();
    }

    public double getAverageFrameTimeMs() {
        if (frameTimeSamples.isEmpty()) {
            return 0.0;
        }

        return totalFrameTimeMs / frameTimeSamples.size();
    }

    /**
     * Returns the 90th-percentile frame time from the current rolling window.
     * A cached sorted snapshot keeps repeated F3 reads cheap between samples.
     */
    public double getNinetiethPercentileFrameTimeMs() {
        if (frameTimeSamples.isEmpty()) {
            return 0.0;
        }

        if (percentileCacheDirty) {
            double[] sortedSamples = new double[frameTimeSamples.size()];
            int index = 0;
            for (double sample : frameTimeSamples) {
                sortedSamples[index++] = sample;
            }
            Arrays.sort(sortedSamples);
            int percentileIndex = Math.max(0, (int) Math.ceil(sortedSamples.length * 0.90) - 1);
            cachedNinetiethPercentileFrameTimeMs = sortedSamples[percentileIndex];
            percentileCacheDirty = false;
        }

        return cachedNinetiethPercentileFrameTimeMs;
    }

    /**
     * Determines whether the current performance is pinned/locked to any FPS limit,
     * such as max FPS slider, VSync refresh rate, or AFK throttle limit.
     * When pinned to a cap, the frametime does NOT indicate genuine system stress.
     */
    public boolean isPinnedToAnyCap(Minecraft client, double avgFrameTimeMs) {
        if (client == null) {
            return false;
        }

        int currentFps = client.getFps();

        // 1. Check Minecraft's FramerateLimitTracker (handles AFK, window throttle, and user cap)
        try {
            if (client.getFramerateLimitTracker() != null) {
                int trackerLimit = client.getFramerateLimitTracker().getFramerateLimit();
                if (trackerLimit > 0 && trackerLimit < 260) {
                    double trackerFrameTimeMs = 1000.0 / trackerLimit;
                    if (Math.abs(avgFrameTimeMs - trackerFrameTimeMs) <= 1.5 || currentFps >= trackerLimit - 2) {
                        return true;
                    }
                }

                if (client.getFramerateLimitTracker().isHeavilyThrottled()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Check Options Max FPS slider
        if (client.options != null) {
            int fpsLimit = client.options.framerateLimit().get();
            if (fpsLimit > 0 && fpsLimit < 260) {
                double limitFrameTimeMs = 1000.0 / fpsLimit;
                if (Math.abs(avgFrameTimeMs - limitFrameTimeMs) <= 1.5 || currentFps >= fpsLimit - 2) {
                    return true;
                }
            }

            // 3. Check VSync & Display Refresh Rate
            if (Boolean.TRUE.equals(client.options.enableVsync().get()) && client.getWindow() != null) {
                float refreshRate = client.getWindow().getActiveVideoMode().getRefreshRate();
                if (refreshRate > 0) {
                    double vsyncFrameTimeMs = 1000.0 / refreshRate;
                    if (Math.abs(avgFrameTimeMs - vsyncFrameTimeMs) <= 1.5 || currentFps >= (int) (refreshRate - 2)) {
                        return true;
                    }
                }
            }
        }

        // 4. AFK 30 FPS Fallback: If framerate is hovering tightly around 30 FPS (33.3 ms)
        if (currentFps >= 28 && currentFps <= 31 && Math.abs(avgFrameTimeMs - 33.33) <= 2.0) {
            return true;
        }

        return false;
    }
}
