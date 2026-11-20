package com.witchcraft.registry;

import com.mojang.serialization.Codec;
import com.witchcraft.WitchCraft;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** What a witch carries with them: the Imagined Ring once read about, and how far Mistress Wren has taught them. */
public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WitchCraft.MODID);

    /** Mistress Wren's lessons: which one the apprentice is on, whether it is done, whether they have met. */
    public static final Supplier<AttachmentType<Integer>> LESSON = TYPES.register("lesson",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<Boolean>> LESSON_DONE = TYPES.register("lesson_done",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());
    public static final Supplier<AttachmentType<Boolean>> MET_MENTOR = TYPES.register("met_mentor",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    /** Read the grimoire page on the Imagined Ring: rings come out true. Kept through death. */
    public static final Supplier<AttachmentType<Boolean>> IMAGINED_RING = TYPES.register("imagined_ring",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());
}
