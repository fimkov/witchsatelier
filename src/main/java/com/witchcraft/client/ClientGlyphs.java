package com.witchcraft.client;

import com.witchcraft.glyph.surface.InkStroke;
import com.witchcraft.glyph.surface.StrokeState;
import com.witchcraft.glyph.surface.SurfacePlane;
import com.witchcraft.network.ModNetwork;
import com.witchcraft.network.Payloads;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Client copy of the ink in the current dimension. */
public final class ClientGlyphs implements ModNetwork.ClientHandlers {
    /**
     * @param abs      cached absolute plane coordinates for rendering
     * @param until    client game time at which the running spell ends (ACTIVE only)
     * @param duration how long it ran in total, for the fade
     */
    public record Entry(SurfacePlane plane, InkStroke stroke, double[] abs, long until, int duration) {}

    public static final ClientGlyphs INSTANCE = new ClientGlyphs();
    private final Map<Long, Entry> strokes = new HashMap<>();

    public Collection<Entry> all() {
        return strokes.values();
    }

    @Override
    public void strokesAdded(Payloads.StrokesAdded p) {
        for (InkStroke s : p.strokes()) {
            strokes.put(s.id(), new Entry(p.plane(), s, s.absolute(), 0, 0));
            ClientDrawing.arrived(p.plane(), s);
        }
    }

    @Override
    public void strokesRemoved(Payloads.StrokesRemoved p) {
        for (long id : p.ids()) strokes.remove(id);
    }

    @Override
    public void strokeStates(Payloads.StrokeStates p) {
        StrokeState st = StrokeState.byId(p.state());
        var level = net.minecraft.client.Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        for (long id : p.ids()) {
            strokes.computeIfPresent(id, (k, e) -> {
                // a glyph edited while running keeps its original length for the fade
                int total = st == StrokeState.ACTIVE && e.stroke().state() == StrokeState.ACTIVE && e.duration() > 0 ? e.duration() : p.ticksLeft();
                return new Entry(e.plane(), e.stroke().withState(st, p.color()), e.abs(), now + p.ticksLeft(), total);
            });
        }
    }

    @Override
    public void reset() {
        strokes.clear();
        com.witchcraft.client.fx.SpellFxClient.clear();
        PennantRibbons.clear();
        com.witchcraft.client.portal.WindowwayClient.accept(java.util.List.of());
    }

    @Override
    public void spellFx(Payloads.SpellFx p) {
        com.witchcraft.client.fx.SpellFxClient.accept(p);
    }

    @Override
    public void techniques(Payloads.Techniques p) {
        ClientDrawing.imaginedRing = p.imaginedRing();
    }

    @Override
    public void windowways(Payloads.WindowwaySync p) {
        com.witchcraft.client.portal.WindowwayClient.accept(p.frames());
    }

    @Override
    public void birdFx(Payloads.BirdFx p) {
        com.witchcraft.client.fx.SpellFxClient.acceptBird(p);
    }

    @Override
    public void pennantFx(Payloads.PennantFx p) {
        PennantRibbons.add(p);
    }
}
