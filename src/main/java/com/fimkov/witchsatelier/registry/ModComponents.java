package com.fimkov.witchsatelier.registry;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.ink.LoadedInk;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(WitchsAtelier.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LoadedInk>> LOADED_INK =
            COMPONENTS.registerComponentType("loaded_ink", b -> b.persistent(LoadedInk.CODEC).networkSynchronized(LoadedInk.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlyphPages>> GLYPH_PAGES =
            COMPONENTS.registerComponentType("glyph_pages", b -> b.persistent(GlyphPages.CODEC).networkSynchronized(GlyphPages.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LoadedInk>> INKWELL_INK =
            COMPONENTS.registerComponentType("inkwell_ink", b -> b.persistent(LoadedInk.CODEC).networkSynchronized(LoadedInk.STREAM_CODEC));

    /** What each page of a grimoire shows (a glyph library name per page, "basics" for the opening page). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<String>>> GRIMOIRE_ENTRIES =
            COMPONENTS.registerComponentType("grimoire_entries", b -> b.persistent(com.mojang.serialization.Codec.STRING.listOf())
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list(64))));

    /** Pieces cut from one crystal share a bond. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> BOND =
            COMPONENTS.registerComponentType("bond", b -> b.persistent(net.minecraft.core.UUIDUtil.CODEC).networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC));
    /** One piece's own identity. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> SELF_ID =
            COMPONENTS.registerComponentType("self_id", b -> b.persistent(net.minecraft.core.UUIDUtil.CODEC).networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC));

    /** An artifact made but not yet awake: it does nothing until its glyph is drawn on it (see Awakening). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> DORMANT =
            COMPONENTS.registerComponentType("dormant", b -> b.persistent(com.mojang.serialization.Codec.BOOL).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL));

    private ModComponents() {}
}
