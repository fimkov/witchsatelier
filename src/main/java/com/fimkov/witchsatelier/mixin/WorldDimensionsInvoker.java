package com.fimkov.witchsatelier.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldDimensions.class)
public interface WorldDimensionsInvoker {
    @Invoker("checkStability")
    static Lifecycle witchsatelier$checkStability(ResourceKey<LevelStem> key, LevelStem stem) {
        throw new AssertionError();
    }
}
