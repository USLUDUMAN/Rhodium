package com.rhodium.config;

/**
 * Stores the user's initial graphics settings prior to any automated adjustments.
 * Persisted to config/rhodium-original.json so settings can be recovered
 * even if the game crashes or terminates unexpectedly.
 */
public class RhodiumOriginalSettings {
    public int renderDistance = 12;
    public int simulationDistance = 10;
    public double entityDistanceScaling = 1.0;
    public String particles = "ALL";

    public RhodiumOriginalSettings() {
    }

    public RhodiumOriginalSettings(int renderDistance, int simulationDistance, double entityDistanceScaling, String particles) {
        this.renderDistance = renderDistance;
        this.simulationDistance = simulationDistance;
        this.entityDistanceScaling = entityDistanceScaling;
        this.particles = particles;
    }
}
