package com.fimkov.witchsatelier.registry;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.ink.LoadedInk;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(WitchsAtelier.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LoadedInk>> LOADED_INK =
            COMPONENTS.registerComponentType("loaded_ink", b -> b.persistent(LoadedInk.CODEC).networkSynchronized(LoadedInk.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlyphPages>> GLYPH_PAGES =
            COMPONENTS.registerComponentType("glyph_pages", b -> b.persistent(GlyphPages.CODEC).networkSynchronized(GlyphPages.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LoadedInk>> INKWELL_INK =
            COMPONENTS.registerComponentType("inkwell_ink", b -> b.persistent(LoadedInk.CODEC).networkSynchronized(LoadedInk.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> GRIMOIRE_ENTRIES =
            COMPONENTS.registerComponentType("grimoire_entries", b -> b.persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64))));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> PAGE_TITLES =
            COMPONENTS.registerComponentType("page_titles", b -> b.persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64))));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> PAGE_NOTES =
            COMPONENTS.registerComponentType("page_notes", b -> b.persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64))));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> KEEPER =
            COMPONENTS.registerComponentType("keeper", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> BOND =
            COMPONENTS.registerComponentType("bond", b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> SELF_ID =
            COMPONENTS.registerComponentType("self_id", b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> DORMANT =
            COMPONENTS.registerComponentType("dormant", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SEAL_CHARGE =
            COMPONENTS.registerComponentType("seal_charge", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LENT =
            COMPONENTS.registerComponentType("lent", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.fimkov.witchsatelier.item.artifact.GuidanceOrbItem.Fragment>> FRAGMENT =
            COMPONENTS.registerComponentType("fragment", b -> b.persistent(com.fimkov.witchsatelier.item.artifact.GuidanceOrbItem.Fragment.CODEC)
                    .networkSynchronized(com.fimkov.witchsatelier.item.artifact.GuidanceOrbItem.Fragment.STREAM_CODEC));

    private ModComponents() {}
}
