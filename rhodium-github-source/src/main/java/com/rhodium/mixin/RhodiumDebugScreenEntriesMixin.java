package com.rhodium.mixin;

import com.rhodium.debug.RhodiumDebugEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Mixin(DebugScreenEntries.class)
public abstract class RhodiumDebugScreenEntriesMixin {
    @Shadow @Final @Mutable
    private static Map<Identifier, DebugScreenEntry> ENTRIES_BY_ID;

    @Shadow @Final @Mutable
    private static Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> PROFILES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void rhodium$registerDebugEntry(CallbackInfo callbackInfo) {
        Identifier rhodiumEntry = Identifier.fromNamespaceAndPath("rhodium", "adaptive_quality");
        ENTRIES_BY_ID.put(rhodiumEntry, new RhodiumDebugEntry());

        Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> profiles = new EnumMap<>(PROFILES);
        for (DebugScreenProfile profile : DebugScreenProfile.values()) {
            Map<Identifier, DebugScreenEntryStatus> entries = new HashMap<>(profiles.getOrDefault(profile, Map.of()));
            entries.put(rhodiumEntry, DebugScreenEntryStatus.IN_OVERLAY);
            profiles.put(profile, Map.copyOf(entries));
        }
        PROFILES = Map.copyOf(profiles);
    }
}
