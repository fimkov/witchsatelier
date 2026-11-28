package com.fimkov.witchsatelier.windowway;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every marked Windowway in every dimension, kept with the overworld. The first window drawn with a mark is that
 * mark's Sill: the place the mark names. The same witch's second window with it is the Sill's Kin, and the two answer
 * each other. Every other window bearing the mark only visits the Sill. What a witch has seen is kept here too: a
 * Sill answers a stranger's page only once the stranger has stood by it.
 */
public final class WindowwayNetwork extends SavedData {
    private static final String NAME = "witchsatelier_windowways";

    public static final int PER_OWNER = 64, PER_SERVER = 4096, REMEMBERED_PER_PLAYER = 512;

    /** Owner of windows drawn by no one, such as those inked by the world itself. */
    public static final UUID NOBODY = new UUID(0, 0);

    public enum Role { SILL, KIN, VISITOR }

    /**
     * One window as the network knows it. {@code up} is kept from the first casting so a window on a floor faces the
     * same way after every restart; {@code sealedAt} orders the windows of a mark, oldest first.
     */
    public record Station(UUID wid, ResourceKey<Level> dim, Set<Long> strokes, Vec3 center, Vec3 up, Vec3 n, double radius, UUID owner,
                          String mark, Role role, boolean blood, boolean gated, long sealedAt) {
        public long frameId() {
            return WindowwayNetwork.frameId(wid);
        }

        Station withRole(Role r) {
            return new Station(wid, dim, strokes, center, up, n, radius, owner, mark, r, blood, gated, sealedAt);
        }

        Station withBody(Set<Long> strokes, Vec3 center, Vec3 n, double radius, boolean blood, boolean gated) {
            return new Station(wid, dim, Set.copyOf(strokes), center, up, n, radius, owner, mark, role, blood, gated, sealedAt);
        }
    }

    /** The frame id a window's lasting name gives: never negative, and never one of the passing windows' ids. */
    public static long frameId(UUID wid) {
        return (wid.getMostSignificantBits() ^ wid.getLeastSignificantBits()) & (WindowwayService.TRANSIENT - 1);
    }

    private final Map<UUID, Station> byWid = new LinkedHashMap<>();
    private final Map<Long, UUID> byFrame = new HashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> seen = new HashMap<>();
    private long nextSeal = 1;

    public static WindowwayNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WindowwayNetwork::new, WindowwayNetwork::load), NAME);
    }

    public Station station(UUID wid) {
        return wid == null ? null : byWid.get(wid);
    }

    public Station byFrame(long frameId) {
        UUID wid = byFrame.get(frameId);
        return wid == null ? null : byWid.get(wid);
    }

    public Collection<Station> all() {
        return List.copyOf(byWid.values());
    }

    /** The windows bearing a mark, oldest first. */
    public List<Station> withMark(String mark) {
        List<Station> out = new ArrayList<>();
        for (Station s : byWid.values()) if (s.mark().equals(mark)) out.add(s);
        out.sort(Comparator.comparingLong(Station::sealedAt));
        return out;
    }

    public Station sill(String mark) {
        for (Station s : byWid.values()) if (s.role() == Role.SILL && s.mark().equals(mark)) return s;
        return null;
    }

    public Station kin(String mark) {
        for (Station s : byWid.values()) if (s.role() == Role.KIN && s.mark().equals(mark)) return s;
        return null;
    }

    /** Whose a mark is: the owner of its Sill, or null when no window bears it. */
    public UUID claimOf(String mark) {
        Station sill = sill(mark);
        return sill == null ? null : sill.owner();
    }

    public int ownedBy(UUID owner) {
        int n = 0;
        for (Station s : byWid.values()) if (s.owner().equals(owner)) n++;
        return n;
    }

    public int size() {
        return byWid.size();
    }

    /**
     * Enters a newly drawn window and gives it its role. Null when the witch, or the world, already keeps as many
     * windows as it may: such a window stays a quiet rim.
     */
    public Station seal(UUID wid, ResourceKey<Level> dim, Set<Long> strokes, Vec3 center, Vec3 up, Vec3 n, double radius, UUID owner,
                        String mark, boolean blood, boolean gated) {
        UUID who = owner == null ? NOBODY : owner;
        if (byWid.containsKey(wid) || byWid.size() >= PER_SERVER || ownedBy(who) >= PER_OWNER) return null;
        Station s = new Station(wid, dim, Set.copyOf(strokes), center, up, n, radius, who, mark, roleFor(who, mark), blood, gated, nextSeal++);
        put(s);
        return s;
    }

    private Role roleFor(UUID owner, String mark) {
        Station sill = sill(mark);
        if (sill == null) return Role.SILL;
        if (sill.owner().equals(owner) && kin(mark) == null) return Role.KIN;
        return Role.VISITOR;
    }

    /** Updates where a window's ink is and how it was drawn; its name, owner, role and facing stay. */
    public Station update(UUID wid, Set<Long> strokes, Vec3 center, Vec3 n, double radius, boolean blood, boolean gated) {
        Station old = byWid.get(wid);
        if (old == null) return null;
        Station s = old.withBody(strokes, center, n, radius, blood, gated);
        if (!s.equals(old)) put(s);
        return s;
    }

    /**
     * Forgets a window whose ink is gone. A forgotten Sill hands its mark to its witch's oldest other window bearing
     * it, and only failing that to the oldest window anyone else drew; a Sill left without a Kin takes its witch's next
     * window as one.
     */
    public boolean forget(UUID wid) {
        Station gone = byWid.remove(wid);
        if (gone == null) return false;
        byFrame.remove(gone.frameId());
        setDirty();
        if (gone.role() != Role.VISITOR) settle(gone.mark(), gone.owner());
        return true;
    }

    private void settle(String mark, UUID formerOwner) {
        List<Station> all = withMark(mark);
        if (all.isEmpty()) return;
        Station sill = null, kin = null;
        for (Station s : all) if (s.role() == Role.SILL) sill = s;
        if (sill == null) {
            for (Station s : all) if (s.owner().equals(formerOwner)) { sill = s; break; }
            if (sill == null) sill = all.get(0);
        }
        for (Station s : all) if (s != sill && s.role() == Role.KIN && s.owner().equals(sill.owner())) kin = s;
        if (kin == null) {
            for (Station s : all) if (s != sill && s.owner().equals(sill.owner())) { kin = s; break; }
        }
        for (Station s : all) {
            Role want = s == sill ? Role.SILL : s == kin ? Role.KIN : Role.VISITOR;
            if (s.role() != want) put(s.withRole(want));
        }
    }

    private void put(Station s) {
        byWid.put(s.wid(), s);
        byFrame.put(s.frameId(), s.wid());
        setDirty();
    }

    /** Remembers that a player has stood by, or come through, a window. */
    public void discover(UUID player, UUID wid) {
        LinkedHashSet<UUID> known = seen.computeIfAbsent(player, k -> new LinkedHashSet<>());
        if (!known.add(wid)) return;
        if (known.size() > REMEMBERED_PER_PLAYER) {
            Iterator<UUID> it = known.iterator();
            it.next();
            it.remove();
        }
        setDirty();
    }

    public boolean knows(UUID player, UUID wid) {
        Set<UUID> known = seen.get(player);
        return known != null && known.contains(wid);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("nextSeal", nextSeal);
        ListTag list = new ListTag();
        for (Station s : byWid.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("wid", s.wid());
            t.putString("dim", s.dim().location().toString());
            t.putLongArray("strokes", s.strokes().stream().mapToLong(Long::longValue).toArray());
            putVec(t, "center", s.center());
            putVec(t, "up", s.up());
            putVec(t, "n", s.n());
            t.putDouble("radius", s.radius());
            t.putUUID("owner", s.owner());
            t.putString("mark", s.mark());
            t.putString("role", s.role().name());
            t.putBoolean("blood", s.blood());
            t.putBoolean("gated", s.gated());
            t.putLong("sealedAt", s.sealedAt());
            list.add(t);
        }
        tag.put("stations", list);
        ListTag seenList = new ListTag();
        for (Map.Entry<UUID, LinkedHashSet<UUID>> e : seen.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", e.getKey());
            ListTag wids = new ListTag();
            for (UUID w : e.getValue()) wids.add(NbtUtils.createUUID(w));
            t.put("windows", wids);
            seenList.add(t);
        }
        tag.put("seen", seenList);
        return tag;
    }

    private static WindowwayNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        WindowwayNetwork d = new WindowwayNetwork();
        d.nextSeal = Math.max(1, tag.getLong("nextSeal"));
        List<Station> stations = new ArrayList<>();
        for (Tag raw : tag.getList("stations", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            if (dim == null || !t.hasUUID("wid") || !t.hasUUID("owner") || t.getString("mark").isEmpty()) continue;
            Role role;
            try {
                role = Role.valueOf(t.getString("role"));
            } catch (IllegalArgumentException e) {
                role = Role.VISITOR;
            }
            Vec3 up = vec(t, "up"), n = vec(t, "n");
            if (up.lengthSqr() < 0.25 || n.lengthSqr() < 0.25) continue;
            Set<Long> strokes = new HashSet<>();
            for (long id : t.getLongArray("strokes")) strokes.add(id);
            stations.add(new Station(t.getUUID("wid"), ResourceKey.create(Registries.DIMENSION, dim), Set.copyOf(strokes), vec(t, "center"),
                    up, n, t.getDouble("radius"), t.getUUID("owner"), t.getString("mark"), role,
                    t.getBoolean("blood"), t.getBoolean("gated"), t.getLong("sealedAt")));
        }
        stations.sort(Comparator.comparingLong(Station::sealedAt));
        for (Station s : stations) {
            d.byWid.put(s.wid(), s);
            d.byFrame.put(s.frameId(), s.wid());
            d.nextSeal = Math.max(d.nextSeal, s.sealedAt() + 1);
        }
        for (Tag raw : tag.getList("seen", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("player")) continue;
            LinkedHashSet<UUID> known = new LinkedHashSet<>();
            for (Tag w : t.getList("windows", Tag.TAG_INT_ARRAY)) known.add(NbtUtils.loadUUID(w));
            d.seen.put(t.getUUID("player"), known);
        }
        return d;
    }

    private static void putVec(CompoundTag t, String key, Vec3 v) {
        ListTag l = new ListTag();
        l.add(net.minecraft.nbt.DoubleTag.valueOf(v.x));
        l.add(net.minecraft.nbt.DoubleTag.valueOf(v.y));
        l.add(net.minecraft.nbt.DoubleTag.valueOf(v.z));
        t.put(key, l);
    }

    private static Vec3 vec(CompoundTag t, String key) {
        ListTag l = t.getList(key, Tag.TAG_DOUBLE);
        return l.size() == 3 ? new Vec3(l.getDouble(0), l.getDouble(1), l.getDouble(2)) : Vec3.ZERO;
    }
}
