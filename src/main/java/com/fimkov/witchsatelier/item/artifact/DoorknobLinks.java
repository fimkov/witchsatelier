package com.fimkov.witchsatelier.item.artifact;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every doorknob that sits on a door, in any dimension, kept with the overworld so a knob can always find its partner. */
public final class DoorknobLinks extends SavedData {
    private static final String NAME = "witchsatelier_doorknobs";

    /**
     * One knob on one door. {@code door} is the lower half of the door, {@code side} the side of the doorway the knob is on:
     * walking in from that side leads to the partner door, and you come out of the partner on its own knob side.
     */
    public record Knob(UUID self, UUID bond, GlobalPos door, Direction side, ItemStack item, long placed) {}

    private final Map<UUID, Knob> bySelf = new LinkedHashMap<>();
    private final Map<GlobalPos, UUID> byDoor = new HashMap<>();

    public static DoorknobLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DoorknobLinks::new, DoorknobLinks::load), NAME);
    }

    public Knob at(GlobalPos door) {
        UUID id = byDoor.get(door);
        return id == null ? null : bySelf.get(id);
    }

    public Knob bySelf(UUID self) {
        return bySelf.get(self);
    }

    /** The other knob of the same pair that sits on a door, the latest placed if there is more than one. */
    public Knob partnerOf(Knob k) {
        Knob best = null;
        for (Knob o : bySelf.values()) {
            if (o.self().equals(k.self()) || !o.bond().equals(k.bond()) || o.door().equals(k.door())) continue;
            if (best == null || o.placed() >= best.placed()) best = o;
        }
        return best;
    }

    public List<Knob> in(ResourceKey<Level> dim) {
        List<Knob> out = new ArrayList<>();
        for (Knob k : bySelf.values()) if (k.door().dimension().equals(dim)) out.add(k);
        return out;
    }

    public void put(Knob k) {
        Knob old = at(k.door());
        if (old != null) bySelf.remove(old.self());
        bySelf.put(k.self(), k);
        byDoor.put(k.door(), k.self());
        setDirty();
    }

    public Knob remove(GlobalPos door) {
        UUID id = byDoor.remove(door);
        if (id == null) return null;
        setDirty();
        return bySelf.remove(id);
    }

    /** Removes this very knob, if it is still on record; false if it was already taken off or replaced. */
    public boolean remove(Knob k) {
        if (!k.equals(bySelf.get(k.self()))) return false;
        return remove(k.door()) != null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Knob k : bySelf.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("self", k.self());
            t.putUUID("bond", k.bond());
            t.putString("dim", k.door().dimension().location().toString());
            t.putLong("pos", k.door().pos().asLong());
            t.putInt("side", k.side().get3DDataValue());
            t.putLong("placed", k.placed());
            if (!k.item().isEmpty()) t.put("item", k.item().save(registries));
            list.add(t);
        }
        tag.put("knobs", list);
        return tag;
    }

    private static DoorknobLinks load(CompoundTag tag, HolderLookup.Provider registries) {
        DoorknobLinks d = new DoorknobLinks();
        for (Tag raw : tag.getList("knobs", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            Direction side = Direction.from3DDataValue(t.getInt("side"));
            if (dim == null || !t.hasUUID("self") || !t.hasUUID("bond") || side.getAxis().isVertical()) continue;
            GlobalPos door = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("pos")));
            ItemStack item = t.contains("item") ? ItemStack.parse(registries, t.getCompound("item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
            Knob k = new Knob(t.getUUID("self"), t.getUUID("bond"), door, side, item, t.getLong("placed"));
            d.bySelf.put(k.self(), k);
            d.byDoor.put(door, k.self());
        }
        return d;
    }
}
