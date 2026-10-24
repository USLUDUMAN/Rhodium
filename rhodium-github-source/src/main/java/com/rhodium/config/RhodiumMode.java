package com.rhodium.config;

public enum RhodiumMode {
    AUTOMATIC("config.rhodium.mode.automatic"),
    MANUAL("config.rhodium.mode.manual");

    private final String translationKey;

    RhodiumMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
