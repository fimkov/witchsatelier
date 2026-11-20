package com.witchcraft.event;

import com.witchcraft.glyph.GlyphService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/** Game-bus hooks: spell ticking, ink sync, and ink loss when the surface under it goes away. */
public final class ServerEvents {
    private ServerEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(ServerEvents::onLevelTick);
        bus.addListener(ServerEvents::onLogin);
        bus.addListener(ServerEvents::onChangeDimension);
        bus.addListener(ServerEvents::onRespawn);
        bus.addListener(ServerEvents::onBreak);
        bus.addListener(ServerEvents::onExplosion);
        bus.addListener(ServerEvents::onPiston);
        bus.addListener(ServerEvents::onLevelUnload);
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel level) {
            GlyphService.tick(level);
            com.witchcraft.windowway.WindowwayService.tick(level);
            com.witchcraft.util.TimedBlocks.tick(level);
            com.witchcraft.worldgen.WorldGlyphs.tick(level);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { GlyphService.syncAll(p); com.witchcraft.windowway.WindowwayService.syncTo(p); com.witchcraft.glyph.Techniques.sync(p); }
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { GlyphService.syncAll(p); com.witchcraft.windowway.WindowwayService.syncTo(p); com.witchcraft.glyph.Techniques.sync(p); com.witchcraft.glyph.Tracing.forget(p.getUUID()); }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { GlyphService.syncAll(p); com.witchcraft.windowway.WindowwayService.syncTo(p); com.witchcraft.glyph.Techniques.sync(p); }
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel level) GlyphService.onBlocksRemoved(level, List.of(e.getPos().immutable()));
    }

    /** Ink rides along with the blocks a piston moves. */
    private static void onPiston(net.neoforged.neoforge.event.level.PistonEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel level) GlyphService.onPistonMoved(level, e.getPos());
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (e.getLevel() instanceof ServerLevel level) GlyphService.onBlocksRemoved(level, List.copyOf(e.getAffectedBlocks()));
    }

    private static void onLevelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel level) {
            com.witchcraft.windowway.WindowwayService.closeAll(level);
            com.witchcraft.util.TimedBlocks.restoreAll(level);
            GlyphService.clearLevel(level.dimension());
        }
    }
}
