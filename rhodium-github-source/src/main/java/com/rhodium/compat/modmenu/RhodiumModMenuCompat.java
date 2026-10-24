package com.rhodium.compat.modmenu;

import com.rhodium.gui.RhodiumConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class RhodiumModMenuCompat implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return RhodiumConfigScreen::new;
    }
}
