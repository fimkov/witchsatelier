package com.fimkov.witchsatelier.registry;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.worldgen.LibraryRuinFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, WitchsAtelier.MODID);

    public static final DeferredHolder<Feature<?>, LibraryRuinFeature> LIBRARY = FEATURES.register("library_ruin",
            () -> new LibraryRuinFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}
