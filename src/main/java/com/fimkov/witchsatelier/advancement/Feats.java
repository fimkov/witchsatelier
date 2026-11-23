package com.fimkov.witchsatelier.advancement;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.spell.SpellProgram;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/** Checks behind the canon advancements that reward depth in magic the mod already has. */
public final class Feats {
    private Feats() {}

    public static final String LAST_SEEN = "witchsatelier_seen_last", TRACED = "witchsatelier_traced", WAS_HUNTED = "witchsatelier_was_hunted";
    public static final double CURTAIN_RANGE = 8, RIVER_POWER = 50;
    public static final int CURTAIN_WINDOW = 1200, CHAIN = 8, DEPTH = 3, TRACES = 10;

    public static void register(IEventBus bus) {
        bus.addListener(Feats::onClone);
    }

    /** A villager saw this player at work. */
    public static void seen(ServerPlayer p) {
        p.getPersistentData().putLong(LAST_SEEN, p.serverLevel().getGameTime());
    }

    /** A glyph drawn on a surface by {@code p} has just woken. */
    public static void surfaceCast(ServerPlayer p, SpellProgram program, GlyphReading reading, boolean traced, List<InkStroke> strokes, List<Integer> used) {
        int depth = 0;
        for (GlyphReading r = reading; r != null && depth < 16; r = r.core()) depth++;
        double all = 0, blood = 0;
        for (int i : used) {
            if (i < 0 || i >= strokes.size()) continue;
            InkStroke s = strokes.get(i);
            double len = Math.max(0.05, s.length());
            all += len;
            if (s.ink() == InkType.BLOOD) blood += len;
        }
        cast(p, program, depth, traced, all > 0 && blood >= all * 0.5);
    }

    public static void cast(ServerPlayer p, SpellProgram program, int depth, boolean traced, boolean blood) {
        if (depth >= DEPTH) MagicTrigger.fire(p, "three_rings_deep");
        if (blood && program.power() >= RIVER_POWER) MagicTrigger.fire(p, "river_to_sand");
        if (holds(program, Sigil.FIRE) && holds(program, Sigil.WATER)) MagicTrigger.fire(p, "steam_and_silence");
        if (traced) {
            CompoundTag d = p.getPersistentData();
            int n = d.getInt(TRACED) + 1;
            d.putInt(TRACED, n);
            if (n >= TRACES) MagicTrigger.fire(p, "read_it_twice");
        }
        if (unseen(p)) MagicTrigger.fire(p, "behind_the_curtains");
    }

    static boolean holds(SpellProgram program, Sigil element) {
        if (program.element() == element) return true;
        for (SpellProgram l : program.layers()) if (holds(l, element)) return true;
        return false;
    }

    /** Villagers are close by and awake, yet none of them has seen this player at work for a while. */
    static boolean unseen(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return false;
        long now = p.serverLevel().getGameTime();
        CompoundTag d = p.getPersistentData();
        if (d.contains(LAST_SEEN) && now - d.getLong(LAST_SEEN) < CURTAIN_WINDOW) return false;
        return !p.serverLevel().getEntitiesOfClass(AbstractVillager.class, p.getBoundingBox().inflate(CURTAIN_RANGE),
                v -> v.isAlive() && !v.isSleeping() && !v.isBaby() && v.distanceToSqr(p) <= CURTAIN_RANGE * CURTAIN_RANGE).isEmpty();
    }

    public static void chainWoke(ServerPlayer p, int glyphs) {
        if (glyphs >= CHAIN) MagicTrigger.fire(p, "many_small_seals");
    }

    /** The player's debt to the Pact has just become great enough to be hunted for. */
    public static void hunted(ServerPlayer p) {
        p.getPersistentData().putBoolean(WAS_HUNTED, true);
        MagicTrigger.fire(p, "the_bell_tolls");
    }

    /** The player's debt has faded to nothing. */
    public static void forgiven(Player p) {
        if (!p.getPersistentData().getBoolean(WAS_HUNTED)) return;
        p.getPersistentData().remove(WAS_HUNTED);
        MagicTrigger.fire(p, "forgiven_by_time");
    }

    /** The count of traces follows the player everywhere; a hunt survived so far is kept on leaving the End, but not past death. */
    private static void onClone(PlayerEvent.Clone e) {
        CompoundTag from = e.getOriginal().getPersistentData(), to = e.getEntity().getPersistentData();
        for (String k : new String[]{TRACED, LAST_SEEN}) if (from.contains(k)) to.put(k, from.get(k).copy());
        if (!e.isWasDeath() && from.contains(WAS_HUNTED)) to.put(WAS_HUNTED, from.get(WAS_HUNTED).copy());
    }
}
