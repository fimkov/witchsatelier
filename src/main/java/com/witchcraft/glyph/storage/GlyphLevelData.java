package com.witchcraft.glyph.storage;

import com.witchcraft.glyph.surface.InkStroke;
import com.witchcraft.glyph.surface.StrokeState;
import com.witchcraft.glyph.surface.SurfacePlane;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All ink drawn in one dimension. Strokes are bucketed into 16x16-block cells of their plane
 * (by the stroke's first point) so lookups stay local even on huge planes like the ground.
 */
public final class GlyphLevelData extends SavedData {
    public static final int CELL = 16;
    private static final String NAME = "witchcraft_glyphs";

    public record Cell(SurfacePlane plane, int cu, int cv) {}

    private final Map<Cell, Map<Long, InkStroke>> cells = new HashMap<>();
    private final Map<Long, Cell> index = new HashMap<>();
    private long nextId = 1;

    public static GlyphLevelData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(GlyphLevelData::new, GlyphLevelData::load), NAME);
    }

    public long newId() {
        setDirty();
        return nextId++;
    }

    public static Cell cellOf(SurfacePlane plane, double u, double v) {
        return new Cell(plane, Math.floorDiv((int) Math.floor(u), CELL), Math.floorDiv((int) Math.floor(v), CELL));
    }

    public void add(SurfacePlane plane, InkStroke stroke) {
        Cell c = cellOf(plane, stroke.u(0), stroke.v(0));
        cells.computeIfAbsent(c, k -> new HashMap<>()).put(stroke.id(), stroke);
        index.put(stroke.id(), c);
        setDirty();
    }

    public InkStroke get(long id) {
        Cell c = index.get(id);
        return c == null ? null : cells.get(c).get(id);
    }

    public SurfacePlane planeOf(long id) {
        Cell c = index.get(id);
        return c == null ? null : c.plane();
    }

    public InkStroke remove(long id) {
        Cell c = index.remove(id);
        if (c == null) return null;
        Map<Long, InkStroke> m = cells.get(c);
        InkStroke s = m.remove(id);
        if (m.isEmpty()) cells.remove(c);
        setDirty();
        return s;
    }

    public void setState(long id, StrokeState state, int color) {
        Cell c = index.get(id);
        if (c == null) return;
        Map<Long, InkStroke> m = cells.get(c);
        m.computeIfPresent(id, (k, s) -> s.withState(state, color));
        setDirty();
    }

    /** Strokes on {@code plane} whose first point lies within {@code radius} cells around (u, v). */
    public List<InkStroke> near(SurfacePlane plane, double u, double v, int radius) {
        Cell c = cellOf(plane, u, v);
        List<InkStroke> out = new ArrayList<>();
        for (int du = -radius; du <= radius; du++) {
            for (int dv = -radius; dv <= radius; dv++) {
                Map<Long, InkStroke> m = cells.get(new Cell(plane, c.cu() + du, c.cv() + dv));
                if (m != null) out.addAll(m.values());
            }
        }
        return out;
    }

    public Map<SurfacePlane, List<InkStroke>> allByPlane() {
        Map<SurfacePlane, List<InkStroke>> out = new HashMap<>();
        for (Map.Entry<Cell, Map<Long, InkStroke>> e : cells.entrySet()) {
            out.computeIfAbsent(e.getKey().plane(), k -> new ArrayList<>()).addAll(e.getValue().values());
        }
        return out;
    }

    public Collection<Long> ids() {
        return index.keySet();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("nextId", nextId);
        ListTag planes = new ListTag();
        for (Map.Entry<SurfacePlane, List<InkStroke>> e : allByPlane().entrySet()) {
            CompoundTag p = e.getKey().save();
            ListTag strokes = new ListTag();
            for (InkStroke s : e.getValue()) strokes.add(s.save());
            p.put("strokes", strokes);
            planes.add(p);
        }
        tag.put("planes", planes);
        return tag;
    }

    private static GlyphLevelData load(CompoundTag tag, HolderLookup.Provider registries) {
        GlyphLevelData d = new GlyphLevelData();
        d.nextId = tag.getLong("nextId");
        ListTag planes = tag.getList("planes", Tag.TAG_COMPOUND);
        for (int i = 0; i < planes.size(); i++) {
            CompoundTag p = planes.getCompound(i);
            SurfacePlane plane = SurfacePlane.load(p);
            ListTag strokes = p.getList("strokes", Tag.TAG_COMPOUND);
            for (int k = 0; k < strokes.size(); k++) {
                InkStroke s = InkStroke.load(strokes.getCompound(k));
                if (s.size() > 0) d.add(plane, s);
            }
        }
        d.nextId = Math.max(d.nextId, d.index.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1);
        return d;
    }
}
