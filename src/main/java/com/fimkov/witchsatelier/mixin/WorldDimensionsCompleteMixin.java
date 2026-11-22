package com.fimkov.witchsatelier.mixin;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Registry;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game takes any dimension past the three of its own for an experiment, and asks before every load of a world
 * that has one ("Worlds using Experimental Settings are not supported"). Our starfall (beyond the Void Basin) is
 * no experiment: it is left out of that judgement, so a world is still flagged only for what else it holds.
 */
@Mixin(WorldDimensions.Complete.class)
public abstract class WorldDimensionsCompleteMixin {
    @Shadow @Final private Registry<LevelStem> dimensions;

    @Inject(method = "lifecycle", at = @At("RETURN"), cancellable = true)
    private void witchsatelier$oursAreStable(CallbackInfoReturnable<Lifecycle> cir) {
        if (Lifecycle.stable().equals(cir.getReturnValue())) return;
        Lifecycle rest = Lifecycle.stable();
        int others = 0;
        for (var e : dimensions.entrySet()) {
            if (e.getKey().location().getNamespace().equals(WitchsAtelier.MODID)) continue;
            others++;
            rest = rest.add(WorldDimensionsInvoker.witchsatelier$checkStability(e.getKey(), e.getValue()));
        }
        if (others == 3 && Lifecycle.stable().equals(rest)) cir.setReturnValue(Lifecycle.stable());
    }
}
