package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.registry.ModParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

public final class ElementLook {
    private ElementLook() {}

    public static int color(Sigil s, SignType center) {
        if (s == null) {
            if (center == SignType.REPETITION) return 0xFFB58CFF;
            if (center == SignType.VISION) return 0xFFE0E0FF;
            if (center == SignType.BILLOWING) return 0xFFF2F2F2;
            return 0xFFC0C0C0;
        }
        return switch (s) {
            case FIRE -> 0xFFFF7A1F;
            case WATER -> 0xFF39B6FF;
            case EARTH -> 0xFFD8A24E;
            case WIND, WIND_UNDERFOOT -> 0xFF9CF5BC;
            case AERIFORMS -> 0xFFCFF3FF;
            case LIGHT -> 0xFFFFF0A0;
            case CRYSTAL -> 0xFFD48CFF;
            case APPLE -> 0xFFE8E8E8;
        };
    }

    public static ParticleOptions particle(Sigil s) {
        if (s == Sigil.WATER) return droplet();
        return ModParticles.mote(withAlpha(color(s, null), s == Sigil.WIND || s == Sigil.AERIFORMS ? 0.55f : 0.85f));
    }

    public static ParticleOptions trail(Sigil s) {
        if (s == Sigil.EARTH) return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
        if (s == Sigil.WATER) return spray();
        return ModParticles.spark(withAlpha(color(s, null), 1f));
    }

    public static ParticleOptions rune(Sigil s, SignType center) {
        return ModParticles.rune(withAlpha(color(s, center), 0.9f));
    }

    public static ParticleOptions droplet() {
        return ModParticles.droplet(0xE8A8D8FF);
    }

    public static ParticleOptions spray() {
        return ModParticles.mist(0x99E6F4FF);
    }

    public static ParticleOptions steam() {
        return ModParticles.mist(0xCCF4F4F4);
    }

    static int withAlpha(int argb, float a) {
        return ((int) (a * 255) << 24) | (argb & 0xFFFFFF);
    }
}
