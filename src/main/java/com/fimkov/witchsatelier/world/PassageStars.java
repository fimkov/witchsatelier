package com.fimkov.witchsatelier.world;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.advancement.MagicTrigger;
import com.fimkov.witchsatelier.network.Net;
import com.fimkov.witchsatelier.network.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Passage Stars: about once every 24 days, on a clear night, a meteor shower crosses the overworld sky,
 * and for a few days after it crops and berries near players ripen faster ("the harvest after the stars").
 * The schedule lives in the overworld's saved data; the shower itself is drawn on the client.
 */
public final class PassageStars {
    private PassageStars() {}

    public static final String NAME = "witchsatelier_passage_stars";
    public static final int DAY = 24000;
    public static final int INTERVAL = 24, JITTER = 2, FIRST = 6, FIRST_SPREAD = 6;
    public static final int NIGHT_FROM = 13500, NIGHT_TO = 18000;
    public static final int SHOWER = 4800, HARVEST = 3 * DAY;
    public static final int HARVEST_RADIUS = 24, HARVEST_HEIGHT = 6, HARVEST_SAMPLES = 16;

    /** Turns new showers off; registered on the main thread during common setup. */
    public static GameRules.Key<GameRules.BooleanValue> rule;

    /** Set by the client so the payload handler can reach the sky renderer without common code touching client classes. */
    public static Consumer<Payloads.StarShower> clientSink;

    public static void register(IEventBus modBus, IEventBus bus) {
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            if (rule == null) rule = GameRules.register("witchsatelierPassageStars", GameRules.Category.MISC, GameRules.BooleanValue.create(true));
        }));
        bus.addListener(PassageStars::onLevelTick);
        bus.addListener(PassageStars::onLogin);
        bus.addListener(PassageStars::onChangeDimension);
        bus.addListener(PassageStars::onRespawn);
    }

    public static void toClient(Payloads.StarShower p) {
        if (clientSink != null) clientSink.accept(p);
    }

    public static final class Data extends SavedData {
        long next = -1;
        long start = -1;
        long seed;

        public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.next = tag.contains("next") ? tag.getLong("next") : -1;
            d.start = tag.contains("start") ? tag.getLong("start") : -1;
            d.seed = tag.getLong("seed");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("next", next);
            tag.putLong("start", start);
            tag.putLong("seed", seed);
            return tag;
        }

        public long next() { return next; }
        public long start() { return start; }
        public long seed() { return seed; }

        public boolean falling(long now) {
            return start >= 0 && now >= start && now < start + SHOWER;
        }

        public boolean harvest(long now) {
            return start >= 0 && now >= start + SHOWER && now < start + SHOWER + HARVEST;
        }

        void plan(long day, RandomSource rnd) {
            if (next < 0) {
                next = day + FIRST + rnd.nextInt(FIRST_SPREAD + 1);
                setDirty();
            } else if (next - day > INTERVAL + JITTER + 1) {
                next = day + 1 + rnd.nextInt(INTERVAL);
                setDirty();
            }
        }

        /** Starts the shower if tonight is its night; {@code clock} is the time of day that counts (day time, or game time without a daylight cycle). */
        public boolean tryBegin(long now, long clock, boolean clear, RandomSource rnd) {
            long day = Math.floorDiv(clock, DAY), time = Math.floorMod(clock, DAY);
            plan(day, rnd);
            if (falling(now) || day < next || time < NIGHT_FROM || time >= NIGHT_TO || !clear) return false;
            start = now;
            seed = rnd.nextLong();
            next = day + INTERVAL - JITTER + rnd.nextInt(2 * JITTER + 1);
            setDirty();
            return true;
        }
    }

    public static Data get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), NAME);
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        long now = level.getGameTime();
        if (now % 20 != 0) return;
        Data d = get(level);
        if (rule == null || level.getGameRules().getBoolean(rule)) {
            long clock = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) ? level.getDayTime() : now;
            if (d.tryBegin(now, clock, !level.isRaining(), level.random)) {
                WitchsAtelier.LOGGER.debug("The Passage Stars are falling; the next ones are due on day {}", d.next);
                Net.toDimension(level, payload(d));
            }
        }
        if (d.falling(now) && now % 100 == 0) witness(level);
        if (d.harvest(now)) harvest(level);
    }

    public static void beginNow(ServerLevel level) {
        Data d = get(level);
        d.start = level.getGameTime();
        d.seed = level.random.nextLong();
        d.setDirty();
        Net.toDimension(level, payload(d));
    }

    static Payloads.StarShower payload(Data d) {
        return new Payloads.StarShower(d.start, SHOWER, d.seed);
    }

    public static void syncTo(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return;
        Data d = get(p.serverLevel());
        if (d.falling(p.serverLevel().getGameTime())) Net.toPlayer(p, payload(d));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncTo(p);
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncTo(p);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncTo(p);
    }

    /** Everyone under the open sky while the stars fall has seen them. */
    public static void witness(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (level.canSeeSky(BlockPos.containing(p.getEyePosition()))) MagicTrigger.fire(p, "passage_stars");
        }
    }

    /** One player per neighbourhood drives the boost, so a crowd at one farm does not multiply it. */
    static void harvest(ServerLevel level) {
        List<ServerPlayer> leaders = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            boolean covered = false;
            for (ServerPlayer l : leaders) {
                if (l.distanceToSqr(p) < (double) HARVEST_RADIUS * HARVEST_RADIUS) { covered = true; break; }
            }
            if (covered) continue;
            leaders.add(p);
            harvestAround(level, p.blockPosition(), level.random, HARVEST_SAMPLES, HARVEST_RADIUS, HARVEST_HEIGHT);
        }
    }

    /** Gives a few random columns around {@code centre} an extra growth tick; returns how many plants were touched. */
    public static int harvestAround(ServerLevel level, BlockPos centre, RandomSource rnd, int samples, int radius, int height) {
        int touched = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int s = 0; s < samples; s++) {
            int x = centre.getX() + rnd.nextInt(2 * radius + 1) - radius, z = centre.getZ() + rnd.nextInt(2 * radius + 1) - radius;
            for (int y = centre.getY() + height; y >= centre.getY() - height; y--) {
                at.set(x, y, z);
                if (!level.isLoaded(at)) break;
                BlockState st = level.getBlockState(at);
                if (st.isAir()) continue;
                if (st.is(BlockTags.CROPS) || st.is(Blocks.SWEET_BERRY_BUSH)) {
                    st.randomTick(level, at.immutable(), rnd);
                    touched++;
                } else if (st.getBlock() instanceof CaveVines && !CaveVines.hasGlowBerries(st)) {
                    if (rnd.nextInt(4) == 0) level.setBlock(at.immutable(), st.setValue(CaveVines.BERRIES, true), 2);
                    touched++;
                }
            }
        }
        return touched;
    }
}
