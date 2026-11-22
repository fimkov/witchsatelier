package com.fimkov.witchsatelier.event;

import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.Techniques;
import com.fimkov.witchsatelier.glyph.Tracing;
import com.fimkov.witchsatelier.util.TimedBlocks;
import com.fimkov.witchsatelier.windowway.WindowwayService;
import com.fimkov.witchsatelier.worldgen.WorldGlyphs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

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
            WindowwayService.tick(level);
            TimedBlocks.tick(level);
            WorldGlyphs.tick(level);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { GlyphService.syncAll(p); WindowwayService.syncTo(p); Techniques.sync(p); }
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { GlyphService.syncAll(p); WindowwayService.syncTo(p); Techniques.sync(p); Tracing.forget(p.getUUID()); }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { GlyphService.syncAll(p); WindowwayService.syncTo(p); Techniques.sync(p); }
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel level) GlyphService.onBlocksRemoved(level, List.of(e.getPos().immutable()));
    }

    private static void onPiston(PistonEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel level) GlyphService.onPistonMoved(level, e.getPos());
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (e.getLevel() instanceof ServerLevel level) GlyphService.onBlocksRemoved(level, List.copyOf(e.getAffectedBlocks()));
    }

    private static void onLevelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel level) {
            WindowwayService.closeAll(level);
            TimedBlocks.restoreAll(level);
            GlyphService.clearLevel(level.dimension());
        }
    }
}
