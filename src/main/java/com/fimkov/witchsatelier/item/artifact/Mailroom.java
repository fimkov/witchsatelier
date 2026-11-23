package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.entity.FluttergramEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fluttergrams in flight. A letter is kept here from the moment its bird takes off until it is in the addressee's hands, so a
 * restart neither loses nor doubles it. A bird that cannot find its addressee for a whole day flies back to its sender.
 */
public final class Mailroom extends SavedData {
    private static final String NAME = "witchsatelier_mailroom";
    public static final long MIN_DELAY = 100, MAX_DELAY = 1200, LANDING = 30, RETURN_AFTER = 24000;

    public record Recipient(UUID id, String name) {}

    private static final class Letter {
        UUID to, from;
        String toName, fromName;
        ItemStack parcel;
        long sent, due;
        boolean returned, landing;
    }

    private final List<Letter> pending = new ArrayList<>();
    private final Map<String, Recipient> known = new HashMap<>();

    public static Mailroom get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Mailroom::new, Mailroom::load), NAME);
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** Every player who has been on this server can be written to, online or not; no name is looked up anywhere else. */
    public static void remember(ServerPlayer p) {
        Mailroom m = get(p.server);
        Recipient r = new Recipient(p.getUUID(), p.getGameProfile().getName());
        if (!r.equals(m.known.put(key(r.name()), r))) m.setDirty();
    }

    public static Recipient resolve(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name.trim());
        if (online != null) return new Recipient(online.getUUID(), online.getGameProfile().getName());
        return get(server).known.get(key(name));
    }

    public static int inFlight(MinecraftServer server) {
        return get(server).pending.size();
    }

    public static void send(ServerPlayer from, Recipient to, ItemStack parcel) {
        MinecraftServer server = from.server;
        long now = server.overworld().getGameTime();
        ServerPlayer target = server.getPlayerList().getPlayer(to.id());
        long delay = target == null || target.level() != from.level() ? MAX_DELAY
                : Mth.clamp(MIN_DELAY + (long) (from.distanceTo(target) / 2), MIN_DELAY, MAX_DELAY);
        Letter l = new Letter();
        l.to = to.id();
        l.toName = to.name();
        l.from = from.getUUID();
        l.fromName = from.getGameProfile().getName();
        l.parcel = parcel.copy();
        l.sent = now;
        l.due = now + delay;
        Mailroom m = get(server);
        m.pending.add(l);
        m.setDirty();
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 10 != 0) return;
        Mailroom m = get(server);
        if (m.pending.isEmpty()) return;
        for (Iterator<Letter> it = m.pending.iterator(); it.hasNext(); ) {
            Letter l = it.next();
            if (!l.returned && now - l.sent > RETURN_AFTER) {
                UUID id = l.to;
                String name = l.toName;
                l.to = l.from;
                l.toName = l.fromName;
                l.from = id;
                l.fromName = name;
                l.returned = true;
                l.landing = false;
                l.due = now;
                m.setDirty();
            }
            if (now < l.due) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(l.to);
            if (p == null || !p.isAlive()) continue;
            if (!l.landing) {
                l.landing = true;
                l.due = now + LANDING;
                m.setDirty();
                FluttergramEntity.arrive(p.serverLevel(), p);
                continue;
            }
            it.remove();
            m.setDirty();
            ItemStack parcel = l.parcel.copy();
            if (!p.getInventory().add(parcel)) p.drop(parcel, false);
            p.displayClientMessage(Component.translatable(l.returned ? "message.witchsatelier.fluttergram.returned"
                    : "message.witchsatelier.fluttergram.arrived", l.fromName), false);
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.3f);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Letter l : pending) {
            CompoundTag t = new CompoundTag();
            t.putUUID("to", l.to);
            t.putUUID("from", l.from);
            t.putString("toName", l.toName);
            t.putString("fromName", l.fromName);
            if (!l.parcel.isEmpty()) t.put("parcel", l.parcel.save(registries));
            t.putLong("sent", l.sent);
            t.putLong("due", l.due);
            t.putBoolean("returned", l.returned);
            t.putBoolean("landing", l.landing);
            list.add(t);
        }
        tag.put("letters", list);
        ListTag names = new ListTag();
        for (Recipient r : known.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", r.id());
            t.putString("name", r.name());
            names.add(t);
        }
        tag.put("known", names);
        return tag;
    }

    private static Mailroom load(CompoundTag tag, HolderLookup.Provider registries) {
        Mailroom m = new Mailroom();
        for (Tag raw : tag.getList("letters", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("to") || !t.hasUUID("from")) continue;
            Letter l = new Letter();
            l.to = t.getUUID("to");
            l.from = t.getUUID("from");
            l.toName = t.getString("toName");
            l.fromName = t.getString("fromName");
            l.parcel = t.contains("parcel") ? ItemStack.parse(registries, t.getCompound("parcel")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
            l.sent = t.getLong("sent");
            l.due = t.getLong("due");
            l.returned = t.getBoolean("returned");
            l.landing = t.getBoolean("landing");
            m.pending.add(l);
        }
        for (Tag raw : tag.getList("known", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("id")) continue;
            Recipient r = new Recipient(t.getUUID("id"), t.getString("name"));
            m.known.put(key(r.name()), r);
        }
        return m;
    }
}
