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
