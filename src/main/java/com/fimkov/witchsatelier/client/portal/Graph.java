package com.fimkov.witchsatelier.client.portal;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

import java.lang.reflect.Field;

/**
 * The level renderer keeps one set of "which sections can be seen from here" bookkeeping, built up over many
 * frames. The view through a window needs its own (its eye is somewhere else entirely), so ours is swapped in for
 * the second pass and out again, and neither eye spoils the other's.
 */
final class Graph {
    private static final Field GRAPH = field("sectionOcclusionGraph"), VISIBLE = field("visibleSections"), VIEW_AREA = field("viewArea");
    private static final Field[] DOUBLES = {field("prevCamX"), field("prevCamY"), field("prevCamZ"), field("prevCamRotX"), field("prevCamRotY"),
            field("xTransparentOld"), field("yTransparentOld"), field("zTransparentOld")};

    private SectionOcclusionGraph graph = new SectionOcclusionGraph();
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = new ObjectArrayList<>(10000);
    private final double[] doubles = {Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, 0, 0, 0};
    private ViewArea area;
    private int frames;

    private SectionOcclusionGraph savedGraph;
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> savedVisible;
    private final double[] savedDoubles = new double[DOUBLES.length];

    private static Field field(String name) {
        try {
            Field f = LevelRenderer.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("LevelRenderer." + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    void swapIn(LevelRenderer lr) throws IllegalAccessException {
        ViewArea now = (ViewArea) VIEW_AREA.get(lr);
        if (now != area) {
            // render distance changed or the world was reloaded: start over
            area = now;
            graph.waitAndReset(now);
            for (int i = 0; i < 5; i++) doubles[i] = Double.MIN_VALUE;
        }
        // newly built sections are only told to the main graph; refresh ours now and then
        if (++frames % 30 == 0) graph.invalidate();
        savedGraph = (SectionOcclusionGraph) GRAPH.get(lr);
        savedVisible = (ObjectArrayList<SectionRenderDispatcher.RenderSection>) VISIBLE.get(lr);
        for (int i = 0; i < DOUBLES.length; i++) savedDoubles[i] = DOUBLES[i].getDouble(lr);
        GRAPH.set(lr, graph);
        VISIBLE.set(lr, visible);
        for (int i = 0; i < DOUBLES.length; i++) DOUBLES[i].setDouble(lr, doubles[i]);
    }

    void swapOut(LevelRenderer lr) {
        if (savedGraph == null) return;
        try {
            for (int i = 0; i < DOUBLES.length; i++) doubles[i] = DOUBLES[i].getDouble(lr);
            GRAPH.set(lr, savedGraph);
            VISIBLE.set(lr, savedVisible);
            for (int i = 0; i < DOUBLES.length; i++) DOUBLES[i].setDouble(lr, savedDoubles[i]);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } finally {
            savedGraph = null;
        }
    }
}
