package com.rhodium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class RhodiumConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Rhodium");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("rhodium.json").toFile();
    private static final File ORIGINAL_SETTINGS_FILE = FabricLoader.getInstance().getConfigDir().resolve("rhodium-original.json").toFile();

    private static RhodiumConfig currentConfig = new RhodiumConfig();

    public static RhodiumConfig getConfig() {
        return currentConfig;
    }

    public static void setConfig(RhodiumConfig config) {
        if (config != null) {
            config.validate();
            currentConfig = config;
            saveConfig();
        }
    }

    public static void resetToDefaults() {
        currentConfig = new RhodiumConfig();
        saveConfig();
        LOGGER.info("[Rhodium] Configuration reset to defaults.");
    }

    public static void loadConfig() {
        if (!CONFIG_FILE.exists()) {
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            RhodiumConfig loaded = GSON.fromJson(json, RhodiumConfig.class);
            if (loaded != null) {
                boolean hasUpgradeMargin = json.has("upgradeMarginPercent") && !json.get("upgradeMarginPercent").isJsonNull();
                if (!hasUpgradeMargin) {
                    loaded.upgradeMarginPercent = 15;
                }
                boolean hasPhaseOneRenderDistance = json.has("phaseOneRenderDistance")
                        && !json.get("phaseOneRenderDistance").isJsonNull();
                if (!hasPhaseOneRenderDistance) {
                    loaded.phaseOneRenderDistance = 12;
                }
                boolean hasMaxRenderDistance = json.has("maxRenderDistance")
                        && !json.get("maxRenderDistance").isJsonNull();
                if (!hasMaxRenderDistance) {
                    loaded.maxRenderDistance = 16;
                }
                boolean hasMaxSimulationDistance = json.has("maxSimulationDistance")
                        && !json.get("maxSimulationDistance").isJsonNull();
                if (!hasMaxSimulationDistance) {
                    loaded.maxSimulationDistance = 12;
                }
                if (!json.has("configVersion") || json.get("configVersion").isJsonNull()) {
                    loaded.configVersion = 0;
                }
                boolean migrateConfig = !json.has("configVersion")
                        || json.get("configVersion").isJsonNull()
                        || loaded.configVersion < 7
                        || !hasUpgradeMargin
                        || !hasPhaseOneRenderDistance
                        || !hasMaxRenderDistance
                        || !hasMaxSimulationDistance;
                loaded.validate(); // Ensure all fields and newly added keys are valid
                currentConfig = loaded;
                if (migrateConfig) {
                    saveConfig();
                }
                LOGGER.info("[Rhodium] Configuration loaded successfully.");
            }
        } catch (Exception e) {
            LOGGER.error("[Rhodium] Failed to load configuration, keeping defaults.", e);
        }
    }

    public static void saveConfig() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(currentConfig, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[Rhodium] Failed to save configuration.", e);
        }
    }

    public static boolean hasOriginalSettings() {
        return ORIGINAL_SETTINGS_FILE.exists();
    }

    public static RhodiumOriginalSettings loadOriginalSettings() {
        if (!ORIGINAL_SETTINGS_FILE.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(ORIGINAL_SETTINGS_FILE)) {
            return GSON.fromJson(reader, RhodiumOriginalSettings.class);
        } catch (Exception e) {
            LOGGER.error("[Rhodium] Failed to load original settings file.", e);
            return null;
        }
    }

    public static void saveOriginalSettings(RhodiumOriginalSettings settings) {
        try {
            File parent = ORIGINAL_SETTINGS_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(ORIGINAL_SETTINGS_FILE)) {
                GSON.toJson(settings, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[Rhodium] Failed to save original settings backup.", e);
        }
    }

    public static void deleteOriginalSettings() {
        if (ORIGINAL_SETTINGS_FILE.exists()) {
            boolean deleted = ORIGINAL_SETTINGS_FILE.delete();
            if (!deleted) {
                ORIGINAL_SETTINGS_FILE.deleteOnExit();
            }
        }
    }
}
