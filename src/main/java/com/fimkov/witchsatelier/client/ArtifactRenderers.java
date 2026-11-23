package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The doorknob on a door and the fluttergram in flight are drawn as their item, always turned to the viewer. */
@EventBusSubscriber(modid = WitchsAtelier.MODID, value = Dist.CLIENT)
public final class ArtifactRenderers {
    private ArtifactRenderers() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.DOORKNOB.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.5f, false));
        e.registerEntityRenderer(ModEntities.FLUTTERGRAM.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.9f, false));
    }
}
