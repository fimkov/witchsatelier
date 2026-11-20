package com.witchcraft.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;

/** A self-lit, additive particle tinted with the spell's colour. */
public class GlowParticle extends TextureSheetParticle {
    public enum Kind { MOTE, SPARK, RUNE }

    private final Kind kind;
    private final SpriteSet sprites;
    private final float baseAlpha;
    private final float baseSize;
    private final float spin;

    GlowParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                 ColorParticleOption o, SpriteSet sprites, Kind kind) {
        super(level, x, y, z, vx, vy, vz);
        this.kind = kind;
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.rCol = o.getRed();
        this.gCol = o.getGreen();
        this.bCol = o.getBlue();
        this.baseAlpha = o.getAlpha() <= 0 ? 1 : o.getAlpha();
        this.alpha = baseAlpha;
        float jitter = 0.7f + random.nextFloat() * 0.6f;
        switch (kind) {
            case SPARK -> { lifetime = 8 + random.nextInt(8); baseSize = 0.07f * jitter; gravity = 0.03f; friction = 0.88f; }
            case RUNE -> { lifetime = 28 + random.nextInt(20); baseSize = 0.11f * jitter; gravity = -0.004f; friction = 0.93f; }
            default -> { lifetime = 18 + random.nextInt(16); baseSize = 0.10f * jitter; gravity = -0.006f; friction = 0.94f; }
        }
        this.quadSize = baseSize;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.spin = (random.nextFloat() - 0.5f) * 0.12f;
        if (kind == Kind.RUNE) pickSprite(sprites); else setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) age / lifetime;
        oRoll = roll;
        roll += spin;
        switch (kind) {
            case SPARK -> { alpha = baseAlpha * (1 - t) * (0.6f + 0.4f * Mth.sin(age * 1.7f)); quadSize = baseSize * (1 - t * 0.5f); }
            case RUNE -> alpha = baseAlpha * Mth.sin(Mth.PI * t);
            default -> { alpha = baseAlpha * (1 - t * t); quadSize = baseSize * (1 - t * 0.6f); setSpriteFromAge(sprites); }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return WitchRenderTypes.GLOW_PARTICLES;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<ColorParticleOption> {
        @Override
        public Particle createParticle(ColorParticleOption o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GlowParticle(level, x, y, z, vx, vy, vz, o, sprites, kind);
        }
    }
}
