package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.client.fx.SpellFxClient;
import com.fimkov.witchsatelier.client.portal.WindowwayClient;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.network.ModNetwork;
import com.fimkov.witchsatelier.network.Payloads;
import net.minecraft.client.Minecraft;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientGlyphs implements ModNetwork.ClientHandlers {
    public record Entry(SurfacePlane plane, InkStroke stroke, double[] abs, long until, int duration) {}

    public static final ClientGlyphs INSTANCE = new ClientGlyphs();
    private final Map<Long, Entry> strokes = new HashMap<>();
    private final Map<Long, byte[]> wear = new HashMap<>();

    public Collection<Entry> all() {
        return strokes.values();
    }

    public byte[] wear(long id) {
        return wear.get(id);
    }

    @Override
    public void strokeWear(Payloads.StrokeWear p) {
        for (int i = 0; i < p.ids().length; i++) {
            byte[] w = p.wear().get(i);
            if (w.length == 0) wear.remove(p.ids()[i]);
            else wear.put(p.ids()[i], w);
        }
    }

    public Entry get(long id) {
        return strokes.get(id);
    }

    @Override
    public void badApple(Payloads.BadApple p) {
        BadAppleShow.start(p);
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
        for (long id : p.ids()) {
            strokes.remove(id);
            wear.remove(id);
        }
    }

    @Override
    public void strokeStates(Payloads.StrokeStates p) {
        StrokeState st = StrokeState.byId(p.state());
        var level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        for (long id : p.ids()) {
            strokes.computeIfPresent(id, (k, e) -> {
                boolean wasLit = st == StrokeState.ACTIVE && e.stroke().state() == StrokeState.ACTIVE;
                int total = wasLit && e.duration() > 0 ? e.duration() : p.ticksLeft();

                if (wasLit && e.duration() == 0) total = p.ticksLeft() + 20;
                return new Entry(e.plane(), e.stroke().withState(st, p.color()), e.abs(), now + p.ticksLeft(), total);
            });
        }
    }

    @Override
    public void reset() {
        strokes.clear();
        wear.clear();
        SpellFxClient.clear();
        PennantRibbons.clear();
        BadAppleShow.clear();
        WindowwayClient.accept(List.of());
        TraceGuide.reset();
    }

    @Override
    public void spellFx(Payloads.SpellFx p) {
        SpellFxClient.accept(p);
    }

    @Override
    public void techniques(Payloads.Techniques p) {
        ClientDrawing.imaginedRing = p.imaginedRing();
    }

    @Override
    public void windowways(Payloads.WindowwaySync p) {
        WindowwayClient.accept(p.frames());
    }

    @Override
    public void birdFx(Payloads.BirdFx p) {
        SpellFxClient.acceptBird(p);
    }

    @Override
    public void pennantFx(Payloads.PennantFx p) {
        PennantRibbons.add(p);
    }
}
