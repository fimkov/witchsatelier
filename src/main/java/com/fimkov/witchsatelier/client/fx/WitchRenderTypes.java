package com.fimkov.witchsatelier.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/** Additive "light" rendering: colours add up and glow, nothing writes depth, nothing is culled. */
public final class WitchRenderTypes extends RenderType {
    /** The little cloud under a levitating thing: a soft, lumpy cloud picture, see-through at its edges. */
    public static final RenderType CLOUD = create("witchsatelier_cloud", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
            1024, false, true, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new TextureStateShard(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            com.fimkov.witchsatelier.WitchsAtelier.MODID, "textures/fx/lift_cloud.png"), true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    public static final RenderType GLOW = create("witchsatelier_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            4096, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Ordinary see-through colour (water, cloud, steam): blended, not added, so it can be darker than what is behind. */
    public static final RenderType VEIL = create("witchsatelier_veil", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            4096, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    public static final ParticleRenderType GLOW_PARTICLES = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator t, TextureManager tm) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.depthMask(true); // the particle shader discards clear pixels, so only the visible glow writes depth
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "witchsatelier:glow";
        }
    };

    private WitchRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                             Runnable setup, Runnable clear) {
        super(name, fmt, mode, size, crumbling, sort, setup, clear);
    }
}
