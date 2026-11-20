package com.witchcraft.registry;

import com.witchcraft.WitchCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own sounds (synthesised: chimes of glyphs coming alive, the scratch of a nib, a page tearing...). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WitchCraft.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BIRD_CALL = sound("bird_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> BRIMHAT_CAST = sound("brimhat_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLYPH_HUM = sound("glyph_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> KNIGHT_BIND = sound("knight_bind");
    public static final DeferredHolder<SoundEvent, SoundEvent> PAGE_TEAR = sound("page_tear");
    public static final DeferredHolder<SoundEvent, SoundEvent> PETRIFY = sound("petrify");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUILL_SCRATCH = sound("quill_scratch");
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_IGNITE = sound("ring_ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAL_RESTORE = sound("seal_restore");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATER_RUSH = sound("water_rush");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOWWAY = sound("windowway");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEMENT_FIRE = sound("element_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEMENT_WATER = sound("element_water");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEMENT_EARTH = sound("element_earth");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEMENT_WIND = sound("element_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEMENT_LIGHT = sound("element_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOTH_RUSTLE = sound("cloth_rustle");

    /** The voice of an element as its glyph wakes. */
    public static SoundEvent element(com.witchcraft.glyph.model.Sigil s) {
        if (s == null) return null;
        return switch (s) {
            case FIRE -> ELEMENT_FIRE.get();
            case WATER -> ELEMENT_WATER.get();
            case EARTH -> ELEMENT_EARTH.get();
            case WIND, WIND_UNDERFOOT, AERIFORMS -> ELEMENT_WIND.get();
            case LIGHT -> ELEMENT_LIGHT.get();
            default -> null;
        };
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, name)));
    }

    private ModSounds() {}
}
