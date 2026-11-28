package com.fimkov.witchsatelier.network;

import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.Tracing;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.windowway.WindowwayService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static ClientHandlers client;

    public interface ClientHandlers {
        void strokesAdded(Payloads.StrokesAdded p);
        void strokesRemoved(Payloads.StrokesRemoved p);
        void strokeStates(Payloads.StrokeStates p);
        void strokeWear(Payloads.StrokeWear p);
        void reset();
        void spellFx(Payloads.SpellFx p);
        void birdFx(Payloads.BirdFx p);

        void pennantFx(Payloads.PennantFx p);
        void windowways(Payloads.WindowwaySync p);
        void windowwayLink(Payloads.WindowwayLink p);
        void windowwayRefuse(Payloads.WindowwayRefuse p);
        void techniques(Payloads.Techniques p);
        void badApple(Payloads.BadApple p);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Payloads.DrawStroke.TYPE, Payloads.DrawStroke.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) GlyphService.handleDraw(sp, p.plane(), p.absolute());
        });
        r.playToServer(Payloads.TracePin.TYPE, Payloads.TracePin.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            boolean sane = p.pinned() && Double.isFinite(p.u()) && Double.isFinite(p.v()) && Double.isFinite(p.turn())
                    && p.scale() > 0.3 && p.scale() < 8 && p.plane().toWorld(p.u(), p.v(), 0).distanceTo(GlyphService.eyeFor(sp.serverLevel(), p.plane(), p.u(), p.v(), sp)) < 48;
            Tracing.pin(sp, sane ? new Tracing.Pin(p.plane(), p.u(), p.v(), p.turn(), p.scale()) : null);
        });
        r.playToServer(Payloads.EraseAt.TYPE, Payloads.EraseAt.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) GlyphService.handleErase(sp, p.plane(), p.u(), p.v(), p.radius());
        });
        r.playToServer(Payloads.PageStroke.TYPE, Payloads.PageStroke.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PageService.handleStroke(sp, p.hand(), p.page(), p.pts());
        });
        r.playToServer(Payloads.PageErase.TYPE, Payloads.PageErase.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PageService.handleErase(sp, p.hand(), p.page(), p.u(), p.v());
        });
        r.playToServer(Payloads.PageNote.TYPE, Payloads.PageNote.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PageService.handleNote(sp, p.hand(), p.page(), p.title(), p.notes());
        });
        r.playToServer(Payloads.PageTurn.TYPE, Payloads.PageTurn.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PageService.handleTurn(sp, p.hand(), p.page());
        });
        r.playToServer(Payloads.PageTear.TYPE, Payloads.PageTear.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PageService.handleTear(sp, p.hand(), p.page());
        });
        r.playToServer(Payloads.QuickClose.TYPE, Payloads.QuickClose.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PageService.handleQuickClose(sp);
        });
        r.playToClient(Payloads.StrokesAdded.TYPE, Payloads.StrokesAdded.CODEC, (p, ctx) -> { if (client != null) client.strokesAdded(p); });
        r.playToClient(Payloads.StrokesRemoved.TYPE, Payloads.StrokesRemoved.CODEC, (p, ctx) -> { if (client != null) client.strokesRemoved(p); });
        r.playToClient(Payloads.StrokeWear.TYPE, Payloads.StrokeWear.CODEC, (p, ctx) -> { if (client != null) client.strokeWear(p); });
        r.playToClient(Payloads.StrokeStates.TYPE, Payloads.StrokeStates.CODEC, (p, ctx) -> { if (client != null) client.strokeStates(p); });
        r.playToClient(Payloads.SpellFx.TYPE, Payloads.SpellFx.CODEC, (p, ctx) -> { if (client != null) client.spellFx(p); });
        r.playToClient(Payloads.BirdFx.TYPE, Payloads.BirdFx.CODEC, (p, ctx) -> { if (client != null) client.birdFx(p); });
        r.playToClient(Payloads.PennantFx.TYPE, Payloads.PennantFx.CODEC, (p, ctx) -> { if (client != null) client.pennantFx(p); });
        r.playToClient(Payloads.BadApple.TYPE, Payloads.BadApple.CODEC, (p, ctx) -> { if (client != null) client.badApple(p); });
        r.playToClient(Payloads.Techniques.TYPE, Payloads.Techniques.CODEC, (p, ctx) -> { if (client != null) client.techniques(p); });
        r.playToClient(Payloads.StarShower.TYPE, Payloads.StarShower.CODEC, (p, ctx) -> com.fimkov.witchsatelier.world.PassageStars.toClient(p));
        r.playToClient(Payloads.WindowwaySync.TYPE, Payloads.WindowwaySync.CODEC, (p, ctx) -> { if (client != null) client.windowways(p); });
        r.playToClient(Payloads.WindowwayLink.TYPE, Payloads.WindowwayLink.CODEC, (p, ctx) -> { if (client != null) client.windowwayLink(p); });
        r.playToClient(Payloads.WindowwayRefuse.TYPE, Payloads.WindowwayRefuse.CODEC, (p, ctx) -> { if (client != null) client.windowwayRefuse(p); });
        r.playToServer(Payloads.PortalCross.TYPE, Payloads.PortalCross.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) WindowwayService.handleCross(sp, p.id(), p.expectedLink(), p.carried());
        });
        r.playToServer(Payloads.BootsJoin.TYPE, Payloads.BootsJoin.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PageService.handleBootsJoin(sp);
        });
        r.playToClient(Payloads.ResetGlyphs.TYPE, Payloads.ResetGlyphs.CODEC, (p, ctx) -> { if (client != null) client.reset(); });
    }
}
