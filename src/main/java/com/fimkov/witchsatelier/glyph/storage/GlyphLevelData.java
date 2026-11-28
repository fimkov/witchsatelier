package com.fimkov.witchsatelier.glyph.storage;

import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
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

public final class GlyphLevelData extends SavedData {
    public static final int CELL = 16;
    private static final String NAME = "witchsatelier_glyphs";

    public record Cell(SurfacePlane plane, int cu, int cv) {}

    private final Map<Cell, Map<Long, InkStroke>> cells = new HashMap<>();
    private final Map<Long, Cell> index = new HashMap<>();
    private long nextId = 1;

    private final Map<Long, byte[]> wear = new HashMap<>();

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
        wear.remove(id);
        keys.remove(id);
        windowIds.remove(id);
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

    public byte[] wear(long id) {
        return wear.get(id);
    }

    public void setWear(long id, byte[] w) {
        if (w == null) wear.remove(id);
        else if (index.containsKey(id)) wear.put(id, w);
        setDirty();
    }

    public Map<Long, byte[]> allWear() {
        return wear;
    }

    private final Map<Long, java.util.UUID> keys = new java.util.HashMap<>();

    public void setKey(Collection<Long> strokeIds, java.util.UUID who) {
        for (long id : strokeIds) {
            if (!index.containsKey(id)) continue;
            if (who == null) keys.remove(id);
            else keys.put(id, who);
        }
        setDirty();
    }

    public java.util.UUID keyOf(Collection<Long> strokeIds) {
        for (long id : strokeIds) {
            java.util.UUID k = keys.get(id);
            if (k != null) return k;
        }
        return null;
    }

    /**
     * The lasting name of the Windowway drawn with these strokes. A window is cast anew every time the world wakes,
     * so the name lives on its ink: the same window is the same place after any restart, in any order of waking.
     */
    private final Map<Long, java.util.UUID> windowIds = new HashMap<>();

    public void setWindowId(Collection<Long> strokeIds, java.util.UUID wid) {
        for (long id : strokeIds) {
            if (!index.containsKey(id)) continue;
            if (wid == null) windowIds.remove(id);
            else windowIds.put(id, wid);
        }
        setDirty();
    }

    public java.util.UUID windowIdOf(Collection<Long> strokeIds) {
        for (long id : strokeIds) {
            java.util.UUID w = windowIds.get(id);
            if (w != null) return w;
        }
        return null;
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
            for (InkStroke s : e.getValue()) {
                CompoundTag st = s.save();
                byte[] w = wear.get(s.id());
                if (w != null) st.putByteArray("wear", w);
                java.util.UUID key = keys.get(s.id());
                if (key != null) st.putUUID("key", key);
                java.util.UUID wid = windowIds.get(s.id());
                if (wid != null) st.putUUID("window", wid);
                strokes.add(st);
            }
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
                CompoundTag st = strokes.getCompound(k);
                InkStroke s = InkStroke.load(st);
                if (s.size() == 0) continue;
                d.add(plane, s);
                byte[] w = st.getByteArray("wear");
                if (w.length == s.size()) d.wear.put(s.id(), w);
                if (st.hasUUID("key")) d.keys.put(s.id(), st.getUUID("key"));
                if (st.hasUUID("window")) d.windowIds.put(s.id(), st.getUUID("window"));
            }
        }
        d.nextId = Math.max(d.nextId, d.index.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1);
        return d;
    }
}
