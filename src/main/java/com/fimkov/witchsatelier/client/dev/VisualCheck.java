package com.fimkov.witchsatelier.client.dev;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.gametest.GlyphSketch;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModEntities;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development aid, only active with -Dwitchsatelier.visualcheck: in a throw-away world it builds scenes high in the
 * sky (glyphs, creatures, ruins, contraptions, a windowway), points the camera at each, takes
 * screenshots into run/screenshots/witchsatelier_*.png and quits. Lets the visuals be checked without a person at
 * the keyboard.
 */
public final class VisualCheck {
    /**
     * @param film   ticks to film instead of taking a screenshot (0: a screenshot)
     * @param during while filming, called on the server each tick with the tick number (from 1)
     */
    record Shot(String name, int delay, Consumer<ServerLevel> setup, Vec3 eye, float yaw, float pitch, boolean firstPerson,
                int film, java.util.function.BiConsumer<ServerLevel, Integer> during) {
        Shot(String name, int delay, Consumer<ServerLevel> setup, Vec3 eye, float yaw, float pitch, boolean firstPerson) {
            this(name, delay, setup, eye, yaw, pitch, firstPerson, 0, null);
        }
    }

    private static FilmRecorder recorder;
    private static int filmTick;

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int index = -1, timer, ready;
    private static volatile boolean sceneReady;
    static final BlockPos BASE = new BlockPos(0, 220, 0);
    private static final String TEST_WORLD = "WitchcraftVisualTest";

    private static String worldFolder(MinecraftServer server) {
        var p = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return p == null ? "" : p.toString();
    }

    /**
     * Undoes the scenes in a world they were built in by mistake: the platforms in the sky over the origin go back
     * to air, and the creatures put there are removed (wherever they wandered). Run with -Pshots=cleanup -Pworld=...
     */
    private static void cleanup(Minecraft mc, MinecraftServer server) {
        index = Integer.MAX_VALUE / 2;
        timer = -1;
        server.execute(() -> {
            ServerLevel l = server.overworld();
            int blocks = 0;
            for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) for (int y = 0; y <= 16; y++) {
                BlockPos p = BASE.offset(x, y, z);
                if (!l.getBlockState(p).isAir()) { l.setBlock(p, Blocks.AIR.defaultBlockState(), 2); blocks++; }
            }
            java.util.Set<net.minecraft.world.entity.EntityType<?>> ours = java.util.Set.of(ModEntities.KNIGHT_MORALIS.get(), ModEntities.BRIMHAT.get(),
                    ModEntities.STRANGER.get(), ModEntities.SEALCHAIR.get());
            var box = new net.minecraft.world.phys.AABB(-48, l.getMinBuildHeight(), -48, 48, l.getMaxBuildHeight(), 48);
            for (var e : l.getEntities((net.minecraft.world.entity.Entity) null, box, en -> !(en instanceof net.minecraft.world.entity.player.Player)
                    && (ours.contains(en.getType()) || en.getY() > BASE.getY() - 2))) {
                WitchsAtelier.LOGGER.info("[visualcheck] cleanup: removing {} at {}", net.minecraft.world.entity.EntityType.getKey(e.getType()), e.blockPosition());
                e.discard();
            }
            WitchsAtelier.LOGGER.info("[visualcheck] cleanup: {} blocks back to air", blocks);
            mc.execute(mc::stop);
        });
    }

    private VisualCheck() {}

    public static void init() {
        if (System.getProperty("witchsatelier.visualcheck") == null) return;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VisualCheck::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Post e) -> {
            if (recorder != null) recorder.frame();
        });
        // 1. glyphs one at a time, then together
        SHOTS.add(new Shot("glyph_fire", 30, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 14);
            floorGlyph(l, BASE, new GlyphSketch(new Pt(0, 0), 1.4).ring(0, 362).sigil(Sigil.FIRE, 0.8).signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.4, 45));
        }, new Vec3(0.5, 224.5, 7.5), 180, 25, false));
        SHOTS.add(new Shot("glyph_ward", 30, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 14);
            floorGlyph(l, BASE, new GlyphSketch(new Pt(0, 0), 1.3).ring(0, 362).centerSign(SignType.DIAMOND, 0.8));
        }, new Vec3(0.5, 224.5, 7.5), 180, 25, false));
        SHOTS.add(new Shot("glyph_light", 30, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 14);
            floorGlyph(l, BASE, new GlyphSketch(new Pt(0, 0), 1.2).ring(0, 362).sigil(Sigil.LIGHT, 0.7));
        }, new Vec3(0.5, 224.5, 7.5), 180, 25, false));
        SHOTS.add(new Shot("glyph_ignite", 4, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 14);
            floorGlyph(l, BASE, new GlyphSketch(new Pt(0, 0), 1.6).ring(0, 362).sigil(Sigil.WIND, 0.8).signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.4, 45));
        }, new Vec3(0.5, 224.5, 5.5), 180, 40, false));
        // 2. water: a jet from a wall and a wave on the floor
        SHOTS.add(new Shot("water", 25, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 14);
            for (int y = 1; y <= 4; y++) for (int z = -3; z <= 3; z++) l.setBlock(BASE.offset(-6, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            BlockPos wall = BASE.offset(-6, 2, 0);
            SurfacePlane pw = SurfacePlane.of(wall, Direction.EAST);
            Pt cw = pw.toUV(Vec3.atCenterOf(wall).add(0.5, 0, 0));
            GlyphService.inscribe(l, pw, new GlyphSketch(cw, 1.0).ring(0, 362).sigil(Sigil.WATER, 0.6).signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.3, 45).strokes, null);
            floorGlyph(l, BASE.offset(3, 0, 0), new GlyphSketch(new Pt(0, 0), 1.2).ring(0, 362).sigil(Sigil.WATER, 0.7).signs(SignType.DISPERSION, 4, Orientation.OUTWARD, 0.35, 45));
        }, new Vec3(0.5, 226, 10), 180, 30, false));
        // 3. creatures
        SHOTS.add(new Shot("creatures", 30, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 14);
            spawn(l, ModEntities.BRIMHAT.get(), BASE.offset(-3, 1, 0));
            spawn(l, ModEntities.KNIGHT_MORALIS.get(), BASE.offset(0, 1, 0));
            spawn(l, ModEntities.STRANGER.get(), BASE.offset(3, 1, 0));
            l.setBlock(BASE.offset(-1, 1, 3), com.fimkov.witchsatelier.registry.ModBlocks.SEALCHAIR.get().defaultBlockState()
                    .setValue(com.fimkov.witchsatelier.block.SealchairBlock.FACING, Direction.SOUTH), 3);
            var pig = net.minecraft.world.entity.EntityType.SHEEP.create(l);
            if (pig != null) {
                pig.moveTo(BASE.getX() + 6.5, BASE.getY() + 1, BASE.getZ() + 0.5, 180, 0);
                l.addFreshEntity(pig);
                com.fimkov.witchsatelier.block.StatueBlock.petrify(l, pig);
            }
        }, new Vec3(0.5, 223.2, 8.5), 180, 12, false));
        // 4. contraptions
        SHOTS.add(new Shot("contraptions", 20, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 14);
            l.setBlock(BASE.offset(-4, 1, 0), ModBlocks.WARDING_TOWER.get().defaultBlockState().setValue(com.fimkov.witchsatelier.block.sealed.SealedBlock.LIT, true), 3);
            l.setBlock(BASE.offset(-2, 1, 0), ModBlocks.WASHING_BARREL.get().defaultBlockState().setValue(com.fimkov.witchsatelier.block.sealed.SealedBlock.LIT, true), 3);
            l.setBlock(BASE.offset(0, 1, 0), ModBlocks.ARCANE_LENS.get().defaultBlockState().setValue(com.fimkov.witchsatelier.block.ArcaneLensBlock.FACING, Direction.SOUTH), 3);
            l.setBlock(BASE.offset(2, 1, 0), ModBlocks.VOID_BASIN.get().defaultBlockState(), 3);
            l.setBlock(BASE.offset(4, 1, 0), ModBlocks.INKWELL.get().defaultBlockState(), 3);
            l.setBlock(BASE.offset(-1, 1, 2), ModBlocks.GLOWSTONE_PATH.get().defaultBlockState().setValue(com.fimkov.witchsatelier.block.GlowstonePathBlock.LIT, true), 3);
            l.setBlock(BASE.offset(1, 1, 2), ModBlocks.GLOWSTONE_PATH.get().defaultBlockState(), 3);
            l.setBlock(BASE.offset(3, 1, 2), ModBlocks.DRAGON_TEACUP.get().defaultBlockState().setValue(com.fimkov.witchsatelier.block.DragonTeacupBlock.DANCING, true), 3);
        }, new Vec3(0.5, 224, 6.5), 180, 28, false));
        // 5. the library ruin
        SHOTS.add(new Shot("library", 120, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 16);
            place(l, com.fimkov.witchsatelier.registry.ModFeatures.LIBRARY.get(), BASE.offset(0, 1, 0));
        }, new Vec3(0.5, 229, 16), 180, 25, false));
        // 6. windowway
        SHOTS.add(new Shot("windowway", 40, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 16);
            for (int y = 1; y <= 5; y++) for (int z = -3; z <= 3; z++) {
                l.setBlock(BASE.offset(-6, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                l.setBlock(BASE.offset(6, y, z), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);
            }
            l.setBlock(BASE.offset(9, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            l.setBlock(BASE.offset(9, 2, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            BlockPos a = BASE.offset(-6, 2, 0), b = BASE.offset(6, 2, 0);
            SurfacePlane pa = SurfacePlane.of(a, Direction.EAST), pb = SurfacePlane.of(b, Direction.EAST);
            Pt ca = pa.toUV(Vec3.atCenterOf(a).add(0.5, 0.5, 0)), cb = pb.toUV(Vec3.atCenterOf(b).add(0.5, 0.5, 0));
            GlyphService.inscribe(l, pa, new GlyphSketch(ca, 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, null);
            GlyphService.inscribe(l, pb, new GlyphSketch(cb, 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, null);
        }, new Vec3(-1.5, 223.2, 0.5), 90, 5, false));
        // 6b. windowways the size people draw them, on dirt pillars, the two facing different ways
        SHOTS.add(new Shot("windowway_small", 40, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 16);
            for (int y = 1; y <= 3; y++) for (int k = -1; k <= 1; k++) {
                l.setBlock(BASE.offset(-6, y, k), Blocks.DIRT.defaultBlockState(), 3);
                l.setBlock(BASE.offset(k, y, 10), Blocks.DIRT.defaultBlockState(), 3);
            }
            l.setBlock(BASE.offset(0, 1, 5), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            l.setBlock(BASE.offset(0, 2, 5), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            l.setBlock(BASE.offset(3, 1, 3), Blocks.RED_WOOL.defaultBlockState(), 3);
            BlockPos a = BASE.offset(-6, 2, 0), b = BASE.offset(0, 2, 10);
            SurfacePlane pa = SurfacePlane.of(a, Direction.EAST), pb = SurfacePlane.of(b, Direction.NORTH);
            Pt ca = pa.toUV(Vec3.atCenterOf(a).add(0.5, 0, 0)), cb = pb.toUV(Vec3.atCenterOf(b).add(0, 0, -0.5));
            GlyphService.inscribe(l, pa, new GlyphSketch(ca, 1.4).ring(0, 362).centerSign(SignType.WINDOW, 0.8).strokes, null);
            GlyphService.inscribe(l, pb, new GlyphSketch(cb, 1.4).ring(0, 362).centerSign(SignType.WINDOW, 0.8).strokes, null);
        }, new Vec3(-1.5, 222.6, 0.5), 90, 8, false));
        // 7. gear: attire on stands, artifacts in frames
        SHOTS.add(new Shot("gear", 20, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 12);
            Item[][] sets = {
                    {ModItems.WITCH_HAT.get(), ModItems.WITCH_ROBE.get(), null, ModItems.WITCH_BOOTS.get()},
                    {ModItems.APPRENTICE_HAT.get(), ModItems.WINGCLOAK.get(), null, null},
                    {ModItems.BRIMHAT_MASK.get(), ModItems.BRIMHAT_MANTLE.get(), null, null},
                    {null, ModItems.MIRROR_MANTLE.get(), null, null},
                    {ModItems.WITCH_HAT.get(), ModItems.WITCH_MANTLE.get(), null, null}};
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < sets.length; i++) {
                var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(l);
                if (stand == null) continue;
                stand.moveTo(BASE.getX() - 4 + i * 2 + 0.5, BASE.getY() + 1, BASE.getZ() + 0.5, (i - 2) * -14, 0);
                for (int s = 0; s < 4; s++) if (sets[i][s] != null) stand.setItemSlot(slots[s], new ItemStack(sets[i][s]));
                l.addFreshEntity(stand);
            }
            Item[] shown = {ModItems.CAPTURE_PENNANT.get(), ModItems.TENT_STAFF.get(), ModItems.RAINCLEAVER.get(), ModItems.SEALCHAIR.get(),
                    ModItems.SNUGSTONE.get(), ModItems.VAPOR_BUBBLE.get(), ModItems.LIGHT_TRACER_BANGLE.get(), ModItems.GRIMOIRE.get()};
            for (int i = 0; i < shown.length; i++) {
                BlockPos wall = BASE.offset(-4 + i, 2 + (i % 2), -2);
                l.setBlock(wall, Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
                l.setBlock(wall.below(), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
                var frame = new net.minecraft.world.entity.decoration.ItemFrame(l, wall.south(), Direction.SOUTH);
                frame.setItem(new ItemStack(shown[i]), false);
                l.addFreshEntity(frame);
            }
        }, new Vec3(0.5, 222.9, 4.6), 180, 8, false));
        // 8. hands
        SHOTS.add(new Shot("hands", 20, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 10);
        }, new Vec3(0.5, 222.62, 0.5), 180, 30, true));
        grimoireShots();
        pennantShots();
        bookHandShots();
        locateShot();
        visitShots();
        imaginedShot();
        appleShots();
        // the themed books, open: volume_<id>_<page>
        for (String[] v : new String[][]{{"forbidden", "0"}, {"forbidden", "1"}, {"forbidden", "2"}, {"forbidden", "3"}, {"forbidden", "4"}, {"forbidden", "5"}, {"flame", "1"}, {"imagined_ring", "0"}, {"primer", "0"}}) {
            SHOTS.add(new Shot("volume_" + v[0] + "_" + v[1], 30, l -> {
                ServerPlayer p = player(l.getServer());
                if (p == null) return;
                var vol = v[0].equals("forbidden") ? com.fimkov.witchsatelier.glyph.page.GlyphLibrary.FORBIDDEN_BOOK
                        : v[0].equals("imagined_ring") ? com.fimkov.witchsatelier.glyph.page.GlyphLibrary.RING_NOTES
                        : v[0].equals("primer") ? com.fimkov.witchsatelier.glyph.page.GlyphLibrary.PRIMER
                        : com.fimkov.witchsatelier.glyph.page.GlyphLibrary.VOLUMES.stream().filter(x -> x.id().equals(v[0])).findFirst().orElseThrow();
                p.setItemSlot(EquipmentSlot.MAINHAND, com.fimkov.witchsatelier.glyph.page.GlyphLibrary.volumeBook(vol, net.minecraft.util.RandomSource.create(3)));
            }, new Vec3(0.5, 222.6, 0.5), 180, 10, true));
        }
        // the recipe viewer's Awakening pages (EMI is in the dev run)
        SHOTS.add(new Shot("wiki_icons_black", 40, l -> {}, new Vec3(0.5, 222.62, 3.5), 180, 10, false));
        SHOTS.add(new Shot("wiki_icons_white", 40, l -> {}, new Vec3(0.5, 222.62, 3.5), 180, 10, false));
        SHOTS.add(new Shot("jei_awakening", 60, l -> {}, new Vec3(0.5, 222.62, 3.5), 180, 10, false));
        SHOTS.add(new Shot("emi_awakening", 40, l -> {}, new Vec3(0.5, 222.62, 3.5), 180, 10, false));
        // a dormant artifact on the canvas: the glyph it wants shows faintly, to be traced
        for (String what : new String[]{"raincleaver", "boots"}) {
            SHOTS.add(new Shot("canvas_" + what, 40, l -> {
                ServerPlayer p = player(l.getServer());
                if (p == null) return;
                ItemStack pen = new ItemStack(ModItems.INK_PEN.get());
                com.fimkov.witchsatelier.item.InkPenItem.setLoaded(pen, com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING, com.fimkov.witchsatelier.item.InkPenItem.NIB_CAPACITY);
                p.setItemSlot(EquipmentSlot.MAINHAND, pen);
                ItemStack held = what.equals("boots") ? new ItemStack(ModItems.WITCH_BOOTS.get()) : new ItemStack(ModItems.RAINCLEAVER.get());
                if (!what.equals("boots")) held.set(com.fimkov.witchsatelier.registry.ModComponents.DORMANT.get(), true);
                p.setItemSlot(EquipmentSlot.OFFHAND, held);
            }, new Vec3(0.5, 222.62, 0.5), 180, 0, true));
        }
        SHOTS.addAll(SpellFilms.shots());
        walkingShot();
        traceShot();
    }

    private static void traceShot() {
        SHOTS.add(new Shot("trace", 20, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 10);
            ServerPlayer p = player(l.getServer());
            if (p != null) {
                ItemStack book = com.fimkov.witchsatelier.glyph.page.GlyphLibrary.grimoireBook(net.minecraft.util.RandomSource.create(5), 4, "pillar_of_flame");
                var gp = book.get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
                book.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), gp.withCurrent(2));
                p.setItemSlot(EquipmentSlot.OFFHAND, book);
                ItemStack pen = new ItemStack(ModItems.INK_PEN.get());
                com.fimkov.witchsatelier.item.InkPenItem.setLoaded(pen, com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING, com.fimkov.witchsatelier.item.InkPenItem.NIB_CAPACITY);
                p.setItemSlot(EquipmentSlot.MAINHAND, pen);
            }
            // half the ring already inked over the guide: the ink must lie on top of it
            SurfacePlane floor = SurfacePlane.of(BASE, Direction.UP);
            Pt at = floor.toUV(new Vec3(0.5, BASE.getY() + 1, 1.5));
            GlyphService.inscribe(l, floor, new GlyphSketch(at, 1.7).ring(200, 420).strokes, null);
        }, new Vec3(0.5, 223.4, 3.5), 180, 50, true));
    }

    private static void walkingShot() {
        SHOTS.add(new Shot("walking", 18, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 14);
            net.minecraft.world.entity.EntityType<?>[] types = {ModEntities.BRIMHAT.get(), ModEntities.STRANGER.get(), ModEntities.KNIGHT_MORALIS.get()};
            for (int i = 0; i < types.length; i++) {
                var m = (net.minecraft.world.entity.Mob) types[i].spawn(l, BASE.offset(-5 + i * 3, 1, -9), MobSpawnType.COMMAND);
                if (m == null) continue;
                m.setTarget(null);
                m.getNavigation().moveTo(BASE.getX() - 5 + i * 3 + 0.5, BASE.getY() + 1, BASE.getZ() + 14, 0.8);
            }
        }, new Vec3(0.5, 222.8, 6.5), 180, 8, false));
    }

    /** The apple no book shows, as its glyph fades into the ring and once the film is playing. */
    private static void appleShots() {
        for (int[] at : new int[][]{{25}, {100}, {700}}) {
            SHOTS.add(new Shot("apple_" + at[0], at[0], l -> {
                clear(l, BASE, 18);
                platform(l, BASE, 10);
                SurfacePlane floor = SurfacePlane.of(BASE, Direction.UP);
                Pt c = floor.toUV(new Vec3(0.5, BASE.getY() + 1, 0.5));
                var g = new com.fimkov.witchsatelier.gametest.GlyphSketch(c, 1.4).ring(0, 362).sigil(com.fimkov.witchsatelier.glyph.model.Sigil.APPLE, 1.0);
                com.fimkov.witchsatelier.glyph.GlyphService.inscribe(l, floor, g.strokes, player(l.getServer()));
            }, new Vec3(0.5, 224.2, 3.0), 180, 52, false));
        }
    }

    private static void imaginedShot() {
        SHOTS.add(new Shot("imagined", 20, l -> {
            clear(l, BASE, 18);
            platform(l, BASE, 10);
        }, new Vec3(0.5, 223.2, 4.5), 180, 45, true));
    }

    /** Where a visit_ shot found its place; the camera goes there instead of to the shot's own eye. */
    private static Vec3 visitEye;

    /** Finds the nearest of each place as /locate does, goes there, and looks: it must have been built. */
    private static void visitShots() {
        for (String k : new String[]{"library_ruin"}) {
            SHOTS.add(new Shot("visit_" + k, 160, l -> {
                var reg = l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
                var holder = reg.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, k)));
                var found = holder.isEmpty() ? null : l.getChunkSource().getGenerator().findNearestMapStructure(l, net.minecraft.core.HolderSet.direct(holder.get()), BASE, 100, false);
                if (found == null) { WitchsAtelier.LOGGER.info("[visualcheck] visit {}: none found", k); return; }
                BlockPos at = found.getFirst();
                l.getChunk(at.getX() >> 4, at.getZ() >> 4); // generate it
                int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
                visitEye = new Vec3(at.getX() + 0.5, y + 14, at.getZ() + 14.5);
                WitchsAtelier.LOGGER.info("[visualcheck] visit {}: {} (ground {})", k, at, y);
            }, new Vec3(0.5, 222.62, 0.5), 180, 40, false));
        }
    }

    /** Logs where the nearest of each witch place is, as /locate structure finds them. */
    private static void locateShot() {
        SHOTS.add(new Shot("locate", 5, l -> {
            var reg = l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            for (String k : new String[]{"library_ruin"}) {
                var holder = reg.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, k)));
                if (holder.isEmpty()) { WitchsAtelier.LOGGER.info("[visualcheck] locate {}: not registered", k); continue; }
                var found = l.getChunkSource().getGenerator().findNearestMapStructure(l, net.minecraft.core.HolderSet.direct(holder.get()), BASE, 100, false);
                WitchsAtelier.LOGGER.info("[visualcheck] locate {}: {}", k, found == null ? "none within range" : found.getFirst());
            }
        }, new Vec3(0.5, 222.62, 0.5), 180, 20, false));
    }

    private static void bookHandShots() {
        for (String hand : new String[]{"book_main", "book_off"}) {
            SHOTS.add(new Shot(hand, 20, l -> {
                clear(l, BASE, 18);
                platform(l, BASE, 10);
                ServerPlayer p = player(l.getServer());
                if (p == null) return;
                ItemStack book = com.fimkov.witchsatelier.glyph.page.GlyphLibrary.grimoireBook(net.minecraft.util.RandomSource.create(5), 4, "pillar_of_flame");
                p.setItemSlot(EquipmentSlot.MAINHAND, hand.equals("book_main") ? book : new ItemStack(ModItems.INK_PEN.get()));
                p.setItemSlot(EquipmentSlot.OFFHAND, hand.equals("book_off") ? book : ItemStack.EMPTY);
            }, new Vec3(0.5, 222.62, 0.5), 180, 20, true));
        }
    }

    private static void pennantShots() {
        for (String flip : new String[]{"flip_fwd", "flip_back"}) {
            SHOTS.add(new Shot(flip, 30, l -> {
                ServerPlayer p = player(l.getServer());
                if (p != null) p.setItemSlot(EquipmentSlot.MAINHAND, com.fimkov.witchsatelier.glyph.page.GlyphLibrary.grimoireBook(
                        net.minecraft.util.RandomSource.create(5), 4, "birds_of_light"));
            }, new Vec3(0.5, 222.6, 0.5), 180, 10, true));
        }
        SHOTS.add(new Shot("birdpage", 30, l -> {
            ServerPlayer p = player(l.getServer());
            if (p != null) p.setItemSlot(EquipmentSlot.MAINHAND, com.fimkov.witchsatelier.glyph.page.GlyphLibrary.grimoireBook(
                    net.minecraft.util.RandomSource.create(5), 4, "birds_of_light"));
        }, new Vec3(0.5, 222.6, 0.5), 180, 10, true));
        for (String name : new String[]{"pennant_hand", "pennant_bind", "pennant_wrap"}) {
            SHOTS.add(new Shot(name, 44, l -> {
                clear(l, BASE, 18);
                platform(l, BASE, 12);
                ServerPlayer p = player(l.getServer());
                if (p != null) {
                    p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CAPTURE_PENNANT.get()));
                    p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                }
                if (!name.equals("pennant_hand")) {
                    var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(l);
                    if (z != null) {
                        z.moveTo(BASE.getX() + 0.5, BASE.getY() + 1, BASE.getZ() - 5.5, 0, 0);
                        z.setNoAi(true);
                        z.setYBodyRot(0);
                        z.setYHeadRot(0);
                        l.addFreshEntity(z);
                    }
                }
                var k = (net.minecraft.world.entity.Mob) ModEntities.KNIGHT_MORALIS.get().spawn(l, BASE.offset(3, 1, -4), MobSpawnType.COMMAND);
                if (k != null) {
                    k.setNoAi(true);
                    k.setYRot(150);
                    k.setYBodyRot(150);
                    k.setYHeadRot(150);
                }
            }, new Vec3(0.5, 222.62, 1.5), 180, 6, true));
        }
    }

    private static void grimoireShots() {
        for (int pg = 0; pg < 3; pg++) {
            SHOTS.add(new Shot("grimoire_" + pg, 30, l -> {
                ServerPlayer p = player(l.getServer());
                if (p != null) p.setItemSlot(EquipmentSlot.MAINHAND, com.fimkov.witchsatelier.glyph.page.GlyphLibrary.grimoireBook(
                        net.minecraft.util.RandomSource.create(5), 4, "pillar_of_flame"));
            }, new Vec3(0.5, 222.6, 0.5), 180, 10, true));
        }
    }

    /** -Dwitchsatelier.visualcheck=all, or a comma-separated list of shot name prefixes. */
    private static void filter() {
        String want = System.getProperty("witchsatelier.visualcheck", "all");
        if (want.equals("all")) {
            SHOTS.removeIf(s -> s.film() > 0); // films take minutes: only when asked for (-Pshots=film or film_<spell>)
            return;
        }
        java.util.List<String> keep = java.util.Arrays.asList(want.split(","));
        SHOTS.removeIf(s -> keep.stream().noneMatch(k -> s.name().startsWith(k)));
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        // nobody is at the keyboard: the game must not pause when the window is not focused
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        // the test world, opened after the mod changed (things removed, a dimension added), asks before loading:
        // nobody is there to answer, so go on (only ever the throw-away test world is opened)
        if (mc.screen != null && mc.level == null && !(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) proceed(mc.screen);
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null || mc.level == null) return;
        if (ready < 60) { ready++; return; }
        if (index < 0 && server.getPlayerList().getPlayer(mc.player.getUUID()) == null) return;
        if (index < 0 && !TEST_WORLD.equals(worldFolder(server))) {
            // never build scenes in a real world: if anything but the test world is open, leave it untouched
            if (System.getProperty("witchsatelier.visualcheck").equals("cleanup")) {
                cleanup(mc, server);
            } else {
                WitchsAtelier.LOGGER.error("[visualcheck] refusing to run in world '{}': only '{}' may be used", worldFolder(server), TEST_WORLD);
                index = Integer.MAX_VALUE / 2;
                timer = -1;
                mc.execute(mc::stop);
            }
            return;
        }
        if (index >= Integer.MAX_VALUE / 2) return;
        if (index < 0) {
            filter();
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerLevel l = server.overworld();
                l.setDayTime(6000);
                l.setWeatherParameters(12000, 0, false, false);
                ServerPlayer p = player(server);
                if (p == null) return;
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                ItemStack pen = new ItemStack(ModItems.INK_PEN.get());
                com.fimkov.witchsatelier.item.InkPenItem.setLoaded(pen, com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING, com.fimkov.witchsatelier.item.InkPenItem.NIB_CAPACITY);
                p.setItemSlot(EquipmentSlot.MAINHAND, pen);
                p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.PALM_QUIRE.get()));
            });
            next(mc, server);
            return;
        }
        if (!sceneReady) return; // the server is still building the scene
        Shot now = SHOTS.get(index);
        if (timer == 30 && now.name().startsWith("grimoire_") && !(mc.screen instanceof com.fimkov.witchsatelier.client.PageScreen)) {
            mc.setScreen(new com.fimkov.witchsatelier.client.PageScreen(net.minecraft.world.InteractionHand.MAIN_HAND,
                    Integer.parseInt(now.name().substring("grimoire_".length()))));
        }
        if (timer == 20 && now.name().startsWith("canvas_") && !(mc.screen instanceof com.fimkov.witchsatelier.client.PageScreen)) {
            mc.setScreen(new com.fimkov.witchsatelier.client.PageScreen(net.minecraft.world.InteractionHand.OFF_HAND));
        }
        if (timer == 20 && now.name().startsWith("wiki_icons_")) mc.setScreen(new IconSheetScreen(now.name().endsWith("black") ? 0xFF000000 : 0xFFFFFFFF));
        if (timer == 30 && now.name().equals("jei_awakening") && net.neoforged.fml.ModList.get().isLoaded("jei")) JeiShot.open();
        if (timer == 20 && now.name().equals("emi_awakening") && net.neoforged.fml.ModList.get().isLoaded("emi"))
            dev.emi.emi.api.EmiApi.displayRecipeCategory(com.fimkov.witchsatelier.compat.AwakeningEmiRecipe.CATEGORY);
        if (timer == 30 && now.name().startsWith("volume_") && !(mc.screen instanceof com.fimkov.witchsatelier.client.PageScreen)) {
            String[] parts = now.name().split("_");
            mc.setScreen(new com.fimkov.witchsatelier.client.PageScreen(net.minecraft.world.InteractionHand.MAIN_HAND, Integer.parseInt(parts[parts.length - 1])));
        }
        if (timer == 30 && (now.name().equals("birdpage") || now.name().startsWith("flip_")) && !(mc.screen instanceof com.fimkov.witchsatelier.client.PageScreen)) {
            mc.setScreen(new com.fimkov.witchsatelier.client.PageScreen(net.minecraft.world.InteractionHand.MAIN_HAND, 2));
        }
        // a page caught halfway over: turned four ticks (200 ms of 420) before the picture
        if (timer == 4 && now.name().startsWith("flip_") && mc.screen instanceof com.fimkov.witchsatelier.client.PageScreen ps)
            ps.debugTurn(now.name().equals("flip_fwd") ? 1 : -1);
        if ((timer == 15 && now.name().equals("pennant_bind")) || (timer == 40 && now.name().equals("pennant_wrap"))) {
            var z = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, mc.player.getBoundingBox().inflate(12)).stream().findFirst().orElse(null);
            if (z != null) com.fimkov.witchsatelier.client.PennantRibbons.add(new com.fimkov.witchsatelier.network.Payloads.PennantFx(mc.player.getId(), z.getId(),
                    (float) z.getX(), (float) z.getY(), (float) z.getZ(), 100));
        }
        if (timer == 20 && now.name().equals("imagined")) {
            // one and a half laps of a shaky ring on the floor, held in the air
            SurfacePlane floor = SurfacePlane.of(BASE, Direction.UP);
            Pt c = floor.toUV(Vec3.atCenterOf(BASE).add(0.5, 0.5, 0.5));
            int n = 160;
            double[] uv = new double[n * 2];
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(540.0 * i / (n - 1));
                double rr = 2.2 * (1 + 0.06 * Math.sin(a * 3) + 0.03 * Math.sin(a * 7));
                uv[i * 2] = c.x() + Math.cos(a) * rr;
                uv[i * 2 + 1] = c.y() + Math.sin(a) * rr;
            }
            com.fimkov.witchsatelier.client.ClientDrawing.debugImagine(floor, uv);
        }
        if (recorder != null) {
            int t = ++filmTick;
            if (now.during() != null) server.execute(() -> now.during().accept(server.overworld(), t));
            if (t >= now.film()) {
                recorder.close();
                recorder = null;
                next(mc, server);
            }
            return;
        }
        if (--timer == 0) {
            Shot s = SHOTS.get(index);
            if (s.film() > 0) {
                // films go to run/films/<spell>.mp4
                recorder = FilmRecorder.start(new java.io.File(mc.gameDirectory, "films/" + s.name().substring("film_".length()) + ".mp4"));
                filmTick = 0;
                return;
            }
            Screenshot.grab(mc.gameDirectory, "witchsatelier_" + s.name() + ".png", mc.getMainRenderTarget(), msg -> WitchsAtelier.LOGGER.info("[visualcheck] {}", msg.getString()));
            next(mc, server);
        }
    }

    private static String lastScreen = "";

    private static void proceed(net.minecraft.client.gui.screens.Screen screen) {
        String name = screen.getClass().getName();
        if (!name.equals(lastScreen)) {
            lastScreen = name;
            WitchsAtelier.LOGGER.info("[visualcheck] screen before the world: {} '{}'", name, screen.getTitle().getString());
        }
        for (var c : screen.children()) {
            if (!(c instanceof net.minecraft.client.gui.components.Button b)) continue;
            String t = b.getMessage().getString().toLowerCase(java.util.Locale.ROOT);
            if (t.contains("without backup") || t.contains("proceed") || t.contains("continue") || t.equals("yes") || t.contains("load anyway")) {
                WitchsAtelier.LOGGER.info("[visualcheck] pressing '{}'", b.getMessage().getString());
                b.onPress();
                return;
            }
        }
    }

    private static void next(Minecraft mc, MinecraftServer server) {
        index++;
        if (index >= SHOTS.size()) {
            FilmRecorder.finishAll();
            WitchsAtelier.LOGGER.info("[visualcheck] done");
            mc.execute(mc::stop);
            index = Integer.MAX_VALUE / 2;
            timer = -1;
            return;
        }
        Shot s = SHOTS.get(index);
        sceneReady = false;
        if (mc.screen instanceof com.fimkov.witchsatelier.client.PageScreen) mc.setScreen(null);
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        mc.options.hideGui = !s.firstPerson(); // the hands are hidden along with the HUD
        server.execute(() -> {
            ServerLevel l = server.overworld();
            try {
                s.setup().accept(l);
            } catch (Throwable t) {
                WitchsAtelier.LOGGER.warn("[visualcheck] scene {} failed", s.name(), t);
            }
            ServerPlayer p = player(server);
            if (p == null) return;
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            Vec3 eye = s.name().startsWith("visit_") && visitEye != null ? visitEye : s.eye();
            visitEye = null;
            p.teleportTo(l, eye.x, eye.y - p.getEyeHeight(), eye.z, s.yaw(), s.pitch());
            p.setDeltaMovement(Vec3.ZERO);
            sceneReady = true;
        });
        timer = s.delay() + 50;
    }

    static ServerPlayer player(MinecraftServer server) {
        var me = Minecraft.getInstance().player;
        ServerPlayer p = me == null ? null : server.getPlayerList().getPlayer(me.getUUID());
        if (p == null && !server.getPlayerList().getPlayers().isEmpty()) p = server.getPlayerList().getPlayers().get(0);
        if (p == null) WitchsAtelier.LOGGER.warn("[visualcheck] no server player (players: {})", server.getPlayerList().getPlayerCount());
        return p;
    }

    // ------------------------------------------------------------------ scene helpers

    static void platform(ServerLevel l, BlockPos c, int r) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            l.setBlock(c.offset(x, 0, z), (x + z) % 2 == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
        }
    }

    static void clear(ServerLevel l, BlockPos c, int r) {
        for (var s : GlyphService.activeSpells(l)) GlyphService.stop(l, s);
        GlyphService.eraseArea(l, Vec3.atCenterOf(c), r * 1.5 + 4);
        for (var e : l.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(c).inflate(r + 6, 16, r + 6),
                en -> !(en instanceof net.minecraft.world.entity.player.Player))) e.discard();
        for (int x = -r - 2; x <= r + 2; x++) for (int z = -r - 2; z <= r + 2; z++) for (int y = 1; y <= 14; y++) {
            if (!l.getBlockState(c.offset(x, y, z)).isAir()) l.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void floorGlyph(ServerLevel l, BlockPos block, GlyphSketch g) {
        SurfacePlane plane = SurfacePlane.of(block, Direction.UP);
        Pt c = plane.toUV(Vec3.atCenterOf(block).add(0, 0.5, 0));
        List<double[]> strokes = new ArrayList<>();
        for (double[] d : g.strokes) {
            double[] q = new double[d.length];
            for (int i = 0; i + 1 < d.length; i += 2) { q[i] = d[i] + c.x(); q[i + 1] = d[i + 1] + c.y(); }
            strokes.add(q);
        }
        GlyphService.inscribe(l, plane, strokes, null);
    }

    static void spawn(ServerLevel l, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, BlockPos at) {
        var m = type.spawn(l, at, MobSpawnType.COMMAND);
        if (m != null) {
            m.setNoAi(true);
            // facing the camera (which looks north)
            m.setYRot(0);
            m.setYHeadRot(0);
            m.setYBodyRot(0);
        }
    }

    private static void place(ServerLevel l, net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration> f, BlockPos at) {
        f.place(new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(java.util.Optional.empty(), l, l.getChunkSource().getGenerator(), l.random, at,
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE));
    }

}
