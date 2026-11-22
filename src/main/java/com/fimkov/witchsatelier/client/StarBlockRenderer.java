package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.block.StarBlock;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;

/** A whole block of stars: the end portal's sky on every open face, top and bottom at the block's own edges. */
public class StarBlockRenderer extends TheEndPortalRenderer<StarBlock.Entity> {
    public StarBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override protected float getOffsetUp() { return 1.0f; }
    @Override protected float getOffsetDown() { return 0.0f; }
}
