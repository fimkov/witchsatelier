package com.fimkov.witchsatelier.registry;

import com.mojang.serialization.MapCodec;
import com.fimkov.witchsatelier.WitchsAtelier;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, WitchsAtelier.MODID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> MOTE = colored("mote");
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> SPARK = colored("spark");
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> RUNE = colored("rune");

    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> DROPLET = colored("droplet");

    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> MIST = colored("mist");

    private static DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> colored(String name) {
        return TYPES.register(name, () -> new ParticleType<ColorParticleOption>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        });
    }

    public static ColorParticleOption mote(int argb) { return ColorParticleOption.create(MOTE.get(), argb); }
    public static ColorParticleOption spark(int argb) { return ColorParticleOption.create(SPARK.get(), argb); }
    public static ColorParticleOption rune(int argb) { return ColorParticleOption.create(RUNE.get(), argb); }
    public static ColorParticleOption droplet(int argb) { return ColorParticleOption.create(DROPLET.get(), argb); }
    public static ColorParticleOption mist(int argb) { return ColorParticleOption.create(MIST.get(), argb); }

    private ModParticles() {}
}
