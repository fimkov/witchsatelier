package com.witchcraft.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;

/**
 * Water that looks like water, not like light: real drops that arc under their own weight and burst into a
 * splash where they land, and mist that billows out and thins away. Lit by the world, alpha-blended.
 */
public class WaterParticle extends TextureSheetParticle {
    public enum Kind { DROPLET, MIST }

    private final Kind kind;
    private final float baseAlpha, baseSize;

    WaterParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, ColorParticleOption o, SpriteSet sprites, Kind kind) {
        super(level, x, y, z, vx, vy, vz);
        this.kind = kind;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        rCol = o.getRed();
        gCol = o.getGreen();
        bCol = o.getBlue();
        baseAlpha = o.getAlpha() <= 0 ? 1 : o.getAlpha();
        alpha = baseAlpha;
        float j = 0.7f + random.nextFloat() * 0.6f;
        if (kind == Kind.DROPLET) {
            lifetime = 30 + random.nextInt(20);
            gravity = 0.9f;
            friction = 0.985f;
            hasPhysics = true;
            baseSize = 0.07f * j;
        } else {
            lifetime = 22 + random.nextInt(18);
            gravity = -0.01f;
            friction = 0.9f;
            hasPhysics = false;
            baseSize = 0.22f * j;
        }
        quadSize = baseSize;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) age / lifetime;
        if (kind == Kind.DROPLET) {
            if (onGround || (x == xo && z == zo && yd == 0)) {
                // it hit something: a little splash and gone
                level.addParticle(ParticleTypes.SPLASH, x, y + 0.05, z, (random.nextFloat() - 0.5f) * 0.1, 0.1, (random.nextFloat() - 0.5f) * 0.1);
                remove();
                return;
            }
            if (level.getFluidState(net.minecraft.core.BlockPos.containing(x, y, z)).isSource()) {
                level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0, 0.05, 0);
                remove();
                return;
            }
            alpha = baseAlpha * (t > 0.85f ? (1 - t) / 0.15f : 1);
        } else {
            oRoll = roll;
            roll += 0.02f;
            quadSize = baseSize * (1 + t * 1.6f);
            alpha = baseAlpha * (1 - t) * (1 - t) * Math.min(1, age / 3f);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<ColorParticleOption> {
        @Override
        public Particle createParticle(ColorParticleOption o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new WaterParticle(level, x, y, z, vx, vy, vz, o, sprites, kind);
        }
    }
}
