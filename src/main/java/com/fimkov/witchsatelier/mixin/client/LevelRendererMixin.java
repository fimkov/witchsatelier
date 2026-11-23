package com.fimkov.witchsatelier.mixin.client;

import com.fimkov.witchsatelier.client.portal.WindowwayClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "addRecentlyCompiledSection", at = @At("HEAD"))
    private void witchsatelier$tellMainView(SectionRenderDispatcher.RenderSection section, CallbackInfo ci) {
        var main = WindowwayClient.mainGraphDuringPass();
        if (main != null) main.onSectionCompiled(section);
    }
}
