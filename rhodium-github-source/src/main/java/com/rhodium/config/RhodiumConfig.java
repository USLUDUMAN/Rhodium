package com.rhodium.config;

/**
 * Configuration model for Rhodium 1.1.9.
 * Supports both AUTOMATIC and MANUAL modes.
 * Backward compatible with existing configuration files.
 */
public class RhodiumConfig {
    public int configVersion = 7;
    public boolean enabled = true;
    public RhodiumMode mode = RhodiumMode.AUTOMATIC;

    public int targetFps = 60;
    public int phaseOneRenderDistance = 12;
    public int maxRenderDistance = 16;
    public int maxSimulationDistance = 12;

    // Hysteresis thresholds as percentages of target frame budget (1000.0 / targetFps)
    public double dropThresholdPercent = 115.0; // Frametime > 115% triggers drop
    public double riseThresholdPercent = 100.0; // Frametime at or below the target budget triggers rise
    public int upgradeMarginPercent = 15;

    // Timing and grace periods (in seconds)
    public double dropGracePeriodSeconds = 4.0;
    public double urgentDropGracePeriodSeconds = 2.0; // Used when frametime > 2x budget
    public double riseGracePeriodSeconds = 6.0;
    public double riseStabilitySeconds = 5.0; // Must be continuously stable for 5s before rise
    public double postRiseDropLockoutSeconds = 8.0; // Briefly protect each quality increase
    public double worldJoinGracePeriodSeconds = 15.0; // No adjustments for first 15s after joining

    // Rate limits
    public int maxDropsPerMinute = 15;
    public int maxRisesPerMinute = 12;

    // Individual toggles for managed settings (which settings Rhodium is allowed to touch)
    public boolean manageParticles = true;
    public boolean manageEntityDistance = true;
    public boolean manageSimulationDistance = true;
    public boolean manageRenderDistance = true;

    // Hard minimum limits and percentage-of-initial limits
    public int minRenderDistance = 6;
    public double minRenderDistancePercent = 65.0;

    public int minSimulationDistance = 5;
    public double minSimulationDistancePercent = 65.0;

    public double minEntityDistanceScaling = 0.50; // 50%
    public double minEntityDistancePercent = 65.0;
    public double entityDistanceStep = 0.10; // 10% steps

    // Manual mode specific settings
    public int manualRenderDistance = 12;
    public int manualSimulationDistance = 10;
    public double manualEntityDistanceScaling = 1.0;
    public String manualParticles = "ALL";

    // Notification settings
    public boolean showOverlayMessages = true; // In action bar above hotbar
    public boolean showChatMessages = false;   // In chat history

    /**
     * Sanitizes values and ensures missing keys or invalid states are fixed.
     */
    public void validate() {
        if (configVersion < 2) {
            migrateLegacyRiseDefaults();
        }
        if (configVersion < 3) {
            migrateRecoveryCadenceDefaults();
        }
        if (configVersion < 4) {
            configVersion = 4;
        }
        if (configVersion < 5) {
            configVersion = 5;
        }
        if (configVersion < 6) {
            configVersion = 6;
        }
        if (configVersion < 7) {
            if (configVersion == 6 && maxRenderDistance == 20) {
                maxRenderDistance = 16;
            }
            configVersion = 7;
        }
        if (mode == null) {
            mode = RhodiumMode.AUTOMATIC;
        }
        if (targetFps < 15) {
            targetFps = 60;
        }
        upgradeMarginPercent = Math.max(5, Math.min(40, upgradeMarginPercent));
        phaseOneRenderDistance = Math.max(6, Math.min(32, phaseOneRenderDistance));
        maxRenderDistance = Math.max(2, Math.min(32, maxRenderDistance));
        maxSimulationDistance = Math.max(3, Math.min(32, maxSimulationDistance));
        if (riseThresholdPercent >= dropThresholdPercent) {
            riseThresholdPercent = Math.max(50.0, dropThresholdPercent - 15.0);
        }
        if (minRenderDistance < 2) {
            minRenderDistance = 2;
        }
        if (minSimulationDistance < 3) {
            minSimulationDistance = 3;
        }
        if (minEntityDistanceScaling < 0.25) {
            minEntityDistanceScaling = 0.25;
        }
        if (manualRenderDistance < 2) {
            manualRenderDistance = 12;
        }
        if (manualSimulationDistance < 3) {
            manualSimulationDistance = 10;
        }
        if (manualParticles == null || manualParticles.isEmpty()) {
            manualParticles = "ALL";
        }
    }

    /** Upgrades old default rise pacing without overwriting custom user values. */
    public void migrateLegacyRiseDefaults() {
        if (riseThresholdPercent == 80.0) {
            riseThresholdPercent = 100.0;
        }
        if (riseGracePeriodSeconds == 20.0) {
            riseGracePeriodSeconds = 3.0;
        }
        if (riseStabilitySeconds == 15.0) {
            riseStabilitySeconds = 5.0;
        }
        if (postRiseDropLockoutSeconds == 30.0) {
            postRiseDropLockoutSeconds = 8.0;
        }
        if (maxRisesPerMinute == 3) {
            maxRisesPerMinute = 12;
        }
        configVersion = 2;
    }

    /** Updates the defaults from 1.1.1 while preserving other user-selected values. */
    public void migrateRecoveryCadenceDefaults() {
        if (riseGracePeriodSeconds == 3.0) {
            riseGracePeriodSeconds = 6.0;
        }
        if (maxDropsPerMinute == 10) {
            maxDropsPerMinute = 15;
        }
        configVersion = 3;
    }
}
