package com.fimkov.witchsatelier.registry;

import com.mojang.serialization.Codec;
import com.fimkov.witchsatelier.WitchsAtelier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WitchsAtelier.MODID);

    public static final Supplier<AttachmentType<Boolean>> IMAGINED_RING = TYPES.register("imagined_ring",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());
}
