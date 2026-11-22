package com.fimkov.witchsatelier.client.dev;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.block.Starfall;
import com.fimkov.witchsatelier.compat.SableFilm;
import com.fimkov.witchsatelier.compat.Ships;
import com.fimkov.witchsatelier.entity.GlyphArrow;
import com.fimkov.witchsatelier.gametest.GlyphSketch;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

final class SpellFilms {
    private SpellFilms() {}

    private static final BlockPos B = VisualCheck.BASE;

    private static final Vec3 EYE = new Vec3(0.5, 224.8, 7.0);
    private static final int CLOSE_AT = 30, LENGTH = 200;

    private record Glyph(SurfacePlane plane, List<double[]> open, List<double[]> closer) {}

    static List<VisualCheck.Shot> shots() {
        List<VisualCheck.Shot> out = new ArrayList<>();
        List<GlyphLibrary.Recipe> all = new ArrayList<>(GlyphLibrary.ALL);
        all.addAll(GlyphLibrary.FORBIDDEN);
        for (GlyphLibrary.Recipe r : all) {
            if (r.name().equals("windowway")) continue;
            out.add(recipeFilm(r));
        }
        out.add(windowwayFilm());
        if (ModList.get().isLoaded("sable")) {
            out.add(shipFilm());
            out.add(flyingShipFilm());
        }
        out.add(voidBasinFilm());
        out.add(bootsFilm());
        out.add(integrationFilm());
        out.add(chainFilm());
        out.add(arrowFilm());
        out.add(mixedFilm());
        out.add(wearFilm());
        out.add(sketchFilm("empty_ring", 1.4, g -> g, LENGTH, false));

        VisualCheck.Shot apple = sketchFilm("apple", 1.5, g -> g.sigil(Sigil.APPLE, 1.0), 500, false);
        out.add(new VisualCheck.Shot(apple.name(), apple.delay(), apple.setup(), new Vec3(0.5, 224.6, 3.4), 180, 52, false, apple.film(), apple.during()));
        return out;
    }

    private static VisualCheck.Shot recipeFilm(GlyphLibrary.Recipe r) {
        String name = r.name();
        boolean wall = name.equals("stone_breaker");
        boolean night = r.sigil() == Sigil.LIGHT || name.equals("night_eye") || name.equals("petrification");
        double radius = r.band() != null ? 2.3 : 1.6;
        Glyph[] glyph = new Glyph[1];
        Consumer<ServerLevel> setup = l -> {
            stage(l, night);
            if (wall) {
                wall(l);
                BlockPos at = B.offset(0, 3, -2);
                SurfacePlane plane = SurfacePlane.of(at, Direction.SOUTH);
                glyph[0] = page(r, plane, plane.toUV(Vec3.atCenterOf(at).add(0, 0, 0.5)), 1.4, new Vec3(0, 1, 0));
            } else {
                SurfacePlane floor = SurfacePlane.of(B, Direction.UP);
                glyph[0] = page(r, floor, floor.toUV(Vec3.atCenterOf(B).add(0, 0.5, 0)), radius, new Vec3(0, 0, -1));
                props(l, name);
                pigs(l, radius);
            }
            GlyphService.inscribe(l, glyph[0].plane(), glyph[0].open(), null);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            if (t == CLOSE_AT) GlyphService.inscribe(l, glyph[0].plane(), glyph[0].closer(), null);
            if (t == CLOSE_AT + 3) report(l, name);
            events(l, name, t);
        };
        if (name.equals("night_eye")) {
            return new VisualCheck.Shot("film_" + name, 10, setup, new Vec3(0.5, 222.62, 2.6), 0, 6, false, LENGTH, during);
        }
        if (name.equals("petrification"))

            return new VisualCheck.Shot("film_" + name, 10, setup, new Vec3(0.5, 227.0, 12.5), 180, 22, false, 260, during);
        return film(name, setup, during, LENGTH);
    }

    private static VisualCheck.Shot windowwayFilm() {
        Glyph[] g = new Glyph[2];
        Consumer<ServerLevel> setup = l -> {
            stage(l, false);
            wall(l);
            for (int y = 1; y <= 5; y++) for (int z = -3; z <= 3; z++) l.setBlock(B.offset(-12, y, z), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);
            for (int y = 1; y <= 3; y++) l.setBlock(B.offset(-9, y, 1), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            l.setBlock(B.offset(-8, 1, -1), Blocks.RED_WOOL.defaultBlockState(), 3);
            BlockPos a = B.offset(0, 3, -2), b = B.offset(-12, 3, 0);
            SurfacePlane pa = SurfacePlane.of(a, Direction.SOUTH), pb = SurfacePlane.of(b, Direction.EAST);
            g[0] = sketch(pa, pa.toUV(Vec3.atCenterOf(a).add(0, 0, 0.5)), 1.8, s -> s.centerSign(SignType.WINDOW, 1.0));
            g[1] = sketch(pb, pb.toUV(Vec3.atCenterOf(b).add(0.5, 0, 0)), 1.8, s -> s.centerSign(SignType.WINDOW, 1.0));
            var p = VisualCheck.player(l.getServer());
            GlyphService.inscribe(l, pa, g[0].open(), p);
            GlyphService.inscribe(l, pb, g[1].open(), p);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            var p = VisualCheck.player(l.getServer());
            if (t == 20) GlyphService.inscribe(l, g[1].plane(), g[1].closer(), p);
            if (t == CLOSE_AT + 10) GlyphService.inscribe(l, g[0].plane(), g[0].closer(), p);
            if (t == CLOSE_AT + 13) report(l, "windowway");
        };
        return film("windowway", setup, during, 240);
    }

    private static VisualCheck.Shot shipFilm() {
        Glyph[] g = new Glyph[1];
        Consumer<ServerLevel> setup = l -> {
            stage(l, false);
            for (int x : new int[]{-3, 3}) for (int z : new int[]{-3, 3}) l.setBlock(B.offset(x, 1, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) l.setBlock(B.offset(x, 2, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            for (int y = 3; y <= 6; y++) l.setBlock(B.offset(0, y, 0), Blocks.OAK_LOG.defaultBlockState(), 3);
            for (int x = -1; x <= 1; x++) for (int y = 5; y <= 6; y++) l.setBlock(B.offset(x, y, 1), Blocks.WHITE_WOOL.defaultBlockState(), 3);
            l.setBlock(B.offset(3, 3, 0), Blocks.LANTERN.defaultBlockState(), 3);
            SableFilm.assemble(l, B.offset(-3, 2, -3), B.offset(3, 6, 3));
            SurfacePlane floor = SurfacePlane.of(B, Direction.UP);
            g[0] = page(GlyphLibrary.byName("wind_underfoot"), floor, floor.toUV(Vec3.atCenterOf(B).add(0, 0.5, 0)), 2.6, new Vec3(0, 0, -1));
            GlyphService.inscribe(l, floor, g[0].open(), null);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            if (t == CLOSE_AT) GlyphService.inscribe(l, g[0].plane(), g[0].closer(), null);
            if (t == CLOSE_AT + 3) report(l, "aeronautics_lift");

            if (t == 200) GlyphService.eraseArea(l, new Vec3(B.getX() + 0.5, B.getY() + 1, B.getZ() + 0.5), 3.5);
        };
        return new VisualCheck.Shot("film_aeronautics_lift", 10, setup, new Vec3(9.5, 225.5, 13.5), 146, 12, false, 360, during);
    }

    private static VisualCheck.Shot flyingShipFilm() {
        Glyph[] g = new Glyph[1];
        Vec3[] deckTop = new Vec3[1];
        Consumer<ServerLevel> setup = l -> {
            stage(l, false);
            for (int x : new int[]{-2, 2}) for (int z : new int[]{-3, 3}) for (int y = 1; y <= 2; y++) l.setBlock(B.offset(x, y, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
            for (int x = -2; x <= 2; x++) for (int z = -3; z <= 3; z++) l.setBlock(B.offset(x, 3, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            for (int z = -3; z <= 3; z++) for (int x : new int[]{-2, 2}) l.setBlock(B.offset(x, 4, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
            for (int y = 4; y <= 7; y++) l.setBlock(B.offset(0, y, -3), Blocks.SPRUCE_LOG.defaultBlockState(), 3);
            for (int x = -1; x <= 1; x++) for (int y = 6; y <= 7; y++) l.setBlock(B.offset(x, y, -2), Blocks.WHITE_WOOL.defaultBlockState(), 3);
            var toPlot = SableFilm.assemble(l, B.offset(-2, 3, -3), B.offset(2, 7, 3));
            if (toPlot == null) return;

            Vec3 bottom = toPlot.apply(new Vec3(B.getX() + 0.5, B.getY() + 3, B.getZ() + 0.5));
            deckTop[0] = toPlot.apply(new Vec3(B.getX() + 0.5, B.getY() + 4.05, B.getZ() + 2.4));
            BlockPos hull = BlockPos.containing(bottom.x, bottom.y + 0.5, bottom.z);
            SurfacePlane plane = SurfacePlane.of(hull, Direction.DOWN);
            g[0] = page(GlyphLibrary.byName("wind_underfoot"), plane, plane.toUV(bottom), 1.4, new Vec3(0, 0, -1));
            GlyphService.inscribe(l, plane, g[0].open(), VisualCheck.player(l.getServer()));
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            var p = VisualCheck.player(l.getServer());
            if (p == null) return;
            if (t == 1) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
            if (t == 70 && deckTop[0] != null) {
                Vec3 at = Ships.seen(l, deckTop[0]);
                p.teleportTo(l, at.x, at.y, at.z, 180, 20);
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            }
            if (t == CLOSE_AT) GlyphService.inscribe(l, g[0].plane(), g[0].closer(), p);
            if (t == CLOSE_AT + 3) report(l, "flying_ship");

            float yaw = 180, pitch = 20;
            if (t > 90) pitch = -25;
            if (t > 170) pitch = 2;
            if (t > 230) yaw = 180 - Math.min(90, (t - 230) * 1.2f);

            final float fy = yaw, fp = pitch;
            final boolean crouch = t > 350 && t < 438;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player == null) return;
                if (t > 90) {
                    mc.player.setYRot(fy);
                    mc.player.yRotO = fy;
                    mc.player.setXRot(fp);
                    mc.player.xRotO = fp;
                }
                mc.options.keyShift.setDown(crouch);
            });
        };

        return new VisualCheck.Shot("film_flying_ship", 10, setup, new Vec3(0.5, 222.0, 4.5), 180, -55, false, 440, during);
    }

    private static VisualCheck.Shot bootsFilm() {
        Consumer<ServerLevel> setup = l -> {
            stage(l, false);
            var p = VisualCheck.player(l.getServer());
            if (p == null) return;
            var boots = new ItemStack(Items.LEATHER_BOOTS);
            GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(0, 360).sigil(Sigil.WIND, 0.55)
                    .signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.28, 45);
            List<GlyphPages.PageStroke> halves = new ArrayList<>();
            for (double[] d : g.strokes) {
                float[] f = new float[d.length];
                for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
                for (float[] part : PageKind.splitForSoles(f, 0.82f))
                    halves.add(new GlyphPages.PageStroke(part, InkType.CONJURING));
            }
            boots.set(ModComponents.GLYPH_PAGES.get(), GlyphPages.blank(1)
                    .withPage(0, new GlyphPages.Page(List.copyOf(halves))));
            p.setItemSlot(EquipmentSlot.FEET, boots);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            var p = VisualCheck.player(l.getServer());
            if (p == null) return;
            if (t == 1) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            }
            if (t == CLOSE_AT) PageService.handleBootsJoin(p);

            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player == null || t < CLOSE_AT) return;
                mc.player.setXRot(10);
                mc.player.xRotO = 10;
                var o = mc.options;
                o.keyJump.setDown((t >= 40 && t < 80) || (t >= 330 && t < 333) || (t >= 336 && t < 339));
                o.keyUp.setDown(t >= 80 && t < 170);
                o.keyLeft.setDown(t >= 170 && t < 220);
                o.keyRight.setDown(t >= 220 && t < 270);
                if (t >= 378) { o.keyUp.setDown(false); o.keyLeft.setDown(false); o.keyRight.setDown(false); o.keyJump.setDown(false); o.keyShift.setDown(false); }
            });
        };
        return new VisualCheck.Shot("film_sylph_boots", 10, setup, new Vec3(0.5, 222.62, 6.5), 180, 10, false, 380, during);
    }

    private static VisualCheck.Shot voidBasinFilm() {
        Consumer<ServerLevel> setup = l -> {
            stage(l, false);
            l.setBlock(B.offset(0, 1, 0), ModBlocks.VOID_BASIN.get().defaultBlockState(), 3);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            var p = VisualCheck.player(l.getServer());
            if (p == null) return;
            if (t == 40) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.teleportTo(l, B.getX() + 0.5, B.getY() + 2.2, B.getZ() + 0.5, 180, 50);
                p.setDeltaMovement(0, -0.3, 0);
            }
            if (t == 200 && Starfall.among(p.level(), p.position())) {
                ServerLevel stars = (ServerLevel) p.level();
                BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ() - 2.5);
                SurfacePlane plane = SurfacePlane.of(under, Direction.UP);
                Pt c = plane.toUV(Vec3.atCenterOf(under).add(0, 0.5, 0));
                Glyph g = sketch(plane, c, 1.2, s -> s.sigil(Sigil.LIGHT, 0.7));
                GlyphService.inscribe(stars, plane, g.open(), p);
                g = sketch(plane, c, 1.2, s -> s);
                GlyphService.inscribe(stars, plane, g.closer(), p);
            }
        };
        return new VisualCheck.Shot("film_void_basin", 10, setup, new Vec3(0.5, 222.62, 3.5), 180, 30, false, 420, during);
    }

    private static VisualCheck.Shot integrationFilm() {
        Glyph[] g = new Glyph[1];
        Consumer<ServerLevel> setup = l -> {
            stage(l, false);
            wall(l);
            BlockPos at = B.offset(0, 3, -2);
            SurfacePlane plane = SurfacePlane.of(at, Direction.SOUTH);
            GlyphService.inscribe(l, plane, new GlyphSketch(plane.toUV(Vec3.atCenterOf(at).add(0, 0, 0.5)), 1.4).ring(0, 362).sigil(Sigil.EARTH, 0.8)
                    .signs(SignType.CRUSH, 4, Orientation.OUTWARD, 0.45, 45).strokes, null);
            SurfacePlane floor = SurfacePlane.of(B.offset(0, 0, 2), Direction.UP);
            g[0] = sketch(floor, floor.toUV(Vec3.atCenterOf(B.offset(0, 0, 2)).add(0, 0.5, 0)), 1.4,
                    s -> s.sigil(Sigil.EARTH, 0.8).signs(SignType.CRUSH, 4, Orientation.INWARD, 0.45, 45));
            GlyphService.inscribe(l, floor, g[0].open(), null);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            if (t == CLOSE_AT) GlyphService.inscribe(l, g[0].plane(), g[0].closer(), null);
            if (t == CLOSE_AT + 3) report(l, "integration");
        };
        return film("integration", setup, during, LENGTH);
    }

    private static VisualCheck.Shot chainFilm() {
        Consumer<ServerLevel> setup = l -> {
            stage(l, true);
            List<double[]> ink = new ArrayList<>();
            double r = 1.0;
            Pt prev = null;
            for (int i = 0; i < 4; i++) {
                BlockPos at = B.offset(-5 + i * 3, 0, -1);
                SurfacePlane floor = SurfacePlane.of(at, Direction.UP);
                Pt c = floor.toUV(Vec3.atCenterOf(at).add(0, 0.5, 0));
                GlyphSketch g = new GlyphSketch(c, r).ring(20, i == 0 ? 382 : 340).sigil(Sigil.LIGHT, 0.55);
                if (i == 0) g.signs(SignType.TREAD, 1, Orientation.OUTWARD, 0.3, 270);
                if (prev != null) {
                    double dx = c.x() - prev.x(), dy = c.y() - prev.y(), len = Math.hypot(dx, dy), ux = dx / len, uy = dy / len;
                    double[] line = new double[42];
                    for (int k = 0; k <= 20; k++) {
                        double t = k / 20.0;
                        line[k * 2] = prev.x() + ux * r + (dx - 2 * ux * r) * t;
                        line[k * 2 + 1] = prev.y() + uy * r + (dy - 2 * uy * r) * t;
                    }
                    ink.add(line);
                }
                if (i > 0) ink.addAll(0, g.strokes); else ink.addAll(g.strokes);
                prev = c;
            }

            List<double[]> first = new ArrayList<>(ink.subList(0, ink.size()));
            GlyphService.inscribe(l, SurfacePlane.of(B.offset(-5, 0, -1), Direction.UP), first, null);
            l.setBlock(B.offset(-5, -1, -1), Blocks.STONE.defaultBlockState(), 3);
            l.setBlock(B.offset(-6, 1, -1), Blocks.LEVER.defaultBlockState()
                    .setValue(LeverBlock.FACE, AttachFace.FLOOR), 3);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            BlockPos lever = B.offset(-6, 1, -1);
            if (t == 50 || t == 190) {
                var st = l.getBlockState(lever);
                if (st.getBlock() instanceof LeverBlock lb) lb.pull(st, l, lever, null);
            }
        };
        return new VisualCheck.Shot("film_chain", 10, setup, new Vec3(-0.5, 224.2, 3.6), 180, 34, false, 320, during);
    }

    private static VisualCheck.Shot arrowFilm() {
        Consumer<ServerLevel> setup = l -> {
            stage(l, false);
            wall(l);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            if (t == 30 || t == 120) {
                var page = GlyphLibrary.draw(GlyphLibrary.byName(t == 30 ? "pillar_of_flame" : "water_jet"), RandomSource.create(t), true, InkType.CONJURING);
                ItemStack ammo = new ItemStack(ModItems.GLYPH_ARROW.get());
                ammo.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(page), 0));
                Vec3 from = Vec3.atCenterOf(B.offset(t == 30 ? -2 : 2, 3, 4));
                var arrow = new GlyphArrow(l, from.x, from.y, from.z, ammo);
                arrow.shoot(0, 0.05, -1, 2.2f, 0);
                l.addFreshEntity(arrow);
            }
        };
        return new VisualCheck.Shot("film_glyph_arrow", 10, setup, new Vec3(0.5, 224.6, 8.5), 180, 12, false, 240, during);
    }

    private static VisualCheck.Shot mixedFilm() {
        Glyph[] g = new Glyph[1];
        Consumer<ServerLevel> setup = l -> {
            stage(l, true);
            SurfacePlane floor = SurfacePlane.of(B, Direction.UP);
            Pt c = floor.toUV(Vec3.atCenterOf(B).add(0, 0.5, 0));
            List<double[]> open = new ArrayList<>(new GlyphSketch(c, 1.6).ring(0, 320).sigil(Sigil.WIND, 0.8).strokes);
            for (double ox : new double[]{1.0, -1.0}) open.addAll(new GlyphSketch(new Pt(c.x() + ox, c.y()), 0.3).sigil(Sigil.WATER, 0.55).strokes);
            g[0] = new Glyph(floor, open, new GlyphSketch(c, 1.6).ring(316, 364).strokes);
            GlyphService.inscribe(l, floor, open, null);
            var pig = EntityType.PIG.create(l);
            if (pig != null) {
                pig.moveTo(B.getX() + 1.5, B.getY() + 1, B.getZ() + 0.5, 180, 0);
                pig.setNoAi(true);
                pig.igniteForSeconds(60);
                l.addFreshEntity(pig);
            }
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            if (t == CLOSE_AT) GlyphService.inscribe(l, g[0].plane(), g[0].closer(), null);
        };
        return new VisualCheck.Shot("film_floating_drops", 10, setup, new Vec3(0.5, 223.6, 3.4), 180, 30, false, 220, during);
    }

    private static VisualCheck.Shot wearFilm() {
        List<Pig> walkers = new ArrayList<>();
        int length = 900;
        Consumer<ServerLevel> setup = l -> {
            stage(l, false);
            wall(l);
            BlockPos at = B.offset(0, 3, -2);
            SurfacePlane plane = SurfacePlane.of(at, Direction.SOUTH);
            GlyphService.inscribe(l, plane, new GlyphSketch(plane.toUV(Vec3.atCenterOf(at).add(0, 0, 0.5)), 1.4).ring(0, 362).sigil(Sigil.LIGHT, 0.9).strokes, null);
            SurfacePlane floor = SurfacePlane.of(B.offset(0, 0, 1), Direction.UP);
            GlyphService.inscribe(l, floor, new GlyphSketch(floor.toUV(Vec3.atCenterOf(B.offset(0, 0, 1)).add(0, 0.5, 0)), 1.6).ring(0, 362).sigil(Sigil.FIRE, 1.0)
                    .signs(SignType.RADIAL, 4, Orientation.OUTWARD, 0.5, 45).strokes, InkType.WATERPROOF, null);
            walkers.clear();
            for (int i = 0; i < 2; i++) {
                var pig = EntityType.PIG.create(l);
                if (pig == null) continue;
                pig.moveTo(B.getX() - 2.5 + i * 5, B.getY() + 1, B.getZ() + 1.5 - i * 0.6, 0, 0);
                l.addFreshEntity(pig);
                walkers.add(pig);
            }

            l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(l),
                    "fillbiome " + (B.getX() - 16) + " " + (B.getY() - 2) + " " + (B.getZ() - 16) + " " + (B.getX() + 16) + " " + (B.getY() + 20) + " " + (B.getZ() + 16) + " minecraft:plains");
            l.setWeatherParameters(0, 20000, true, false);
            GlyphService.wearBoost = 4;
            report(l, "ink_wear");
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            if (t % 50 == 0) for (int i = 0; i < walkers.size(); i++) {
                var pig = walkers.get(i);
                boolean east = (t / 50 + i) % 2 == 0;
                pig.getNavigation().moveTo(B.getX() + (east ? 3.2 : -2.2), B.getY() + 1, B.getZ() + 1.2 + i * 0.7, 1.1);
            }
            if (t % 150 == 0) report(l, "ink_wear");
            if (t >= length - 2) {
                GlyphService.wearBoost = 1;
                l.setWeatherParameters(20000, 0, false, false);
            }
        };
        return new VisualCheck.Shot("film_ink_wear", 10, setup, new Vec3(0.5, 224.0, 3.9), 180, 34, false, length, during);
    }

    private static VisualCheck.Shot sketchFilm(String name, double radius, UnaryOperator<GlyphSketch> inside, int length, boolean night) {
        Glyph[] g = new Glyph[1];
        Consumer<ServerLevel> setup = l -> {
            stage(l, night);
            SurfacePlane floor = SurfacePlane.of(B, Direction.UP);
            g[0] = sketch(floor, floor.toUV(Vec3.atCenterOf(B).add(0, 0.5, 0)), radius, inside);
            if (!name.equals("apple")) pigs(l, radius);
            GlyphService.inscribe(l, floor, g[0].open(), null);
        };
        BiConsumer<ServerLevel, Integer> during = (l, t) -> {
            if (t == CLOSE_AT) GlyphService.inscribe(l, g[0].plane(), g[0].closer(), VisualCheck.player(l.getServer()));
            if (t == CLOSE_AT + 3) report(l, name);
        };
        return film(name, setup, during, length);
    }

    private static VisualCheck.Shot film(String name, Consumer<ServerLevel> setup, BiConsumer<ServerLevel, Integer> during, int length) {
        return new VisualCheck.Shot("film_" + name, 10, setup, EYE, 180, 24, false, length, during);
    }

    private static void stage(ServerLevel l, boolean night) {
        if (ModList.get().isLoaded("sable")) SableFilm.clearShips(l);
        VisualCheck.clear(l, B, 18);

        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) for (int y = 15; y <= 60; y++)
            if (!l.getBlockState(B.offset(x, y, z)).isAir()) l.setBlock(B.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        VisualCheck.platform(l, B, 14);
        l.setDayTime(night ? 18000 : 6000);

        l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, l.getServer());
    }

    private static void wall(ServerLevel l) {
        for (int x = -4; x <= 4; x++) for (int y = 1; y <= 6; y++) l.setBlock(B.offset(x, y, -2), Blocks.STONE_BRICKS.defaultBlockState(), 3);
    }

    private static void pigs(ServerLevel l, double radius) {
        double[][] at = {{-0.6, 0.4}, {0.7, -0.3}, {0.1, -0.9}, {radius + 2, 0.5}, {-radius - 2, -0.5}};
        for (double[] a : at) {
            var pig = EntityType.PIG.create(l);
            if (pig == null) continue;
            pig.moveTo(B.getX() + 0.5 + a[0], B.getY() + 1, B.getZ() + 0.5 + a[1], 180, 0);
            pig.finalizeSpawn(l, l.getCurrentDifficultyAt(pig.blockPosition()), MobSpawnType.COMMAND, null);
            l.addFreshEntity(pig);
        }
    }

    private static void props(ServerLevel l, String name) {
        switch (name) {
            case "petrification" -> {
                for (int x = -6; x <= 6; x++) for (int z = -8; z <= 6; z++) l.setBlock(B.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int x = -3; x <= 3; x++) for (int z = -7; z <= -3; z++) {
                    l.setBlock(B.offset(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
                    for (int y = 1; y <= 3; y++) {
                        boolean edge = x == -3 || x == 3 || z == -7 || z == -3;
                        boolean door = z == -3 && x == 0 && y <= 2, window = edge && y == 2 && (x == -2 || x == 2 || (z == -5 && Math.abs(x) == 3));
                        l.setBlock(B.offset(x, y, z), !edge || door ? Blocks.AIR.defaultBlockState() : window ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    }
                    l.setBlock(B.offset(x, 4, z), Blocks.SPRUCE_SLAB.defaultBlockState(), 3);
                }
                l.setBlock(B.offset(-2, 1, -6), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                l.setBlock(B.offset(2, 1, -6), Blocks.BOOKSHELF.defaultBlockState(), 3);
                for (int x = -5; x <= 5; x++) if (Math.abs(x) > 1) l.setBlock(B.offset(x, 1, 4), Blocks.OAK_FENCE.defaultBlockState(), 3);
                for (int[] f : new int[][]{{-4, 2}, {-2, 3}, {3, 2}, {5, 0}, {-5, -1}, {4, -2}})
                    l.setBlock(B.offset(f[0], 1, f[1]), (f[0] % 2 == 0 ? Blocks.POPPY : Blocks.CORNFLOWER).defaultBlockState(), 3);
                for (int[] v : new int[][]{{0, -5}, {2, 1}}) {
                    var villager = EntityType.VILLAGER.create(l);
                    if (villager == null) continue;
                    villager.moveTo(B.getX() + 0.5 + v[0], B.getY() + 1, B.getZ() + 0.5 + v[1], 180, 0);
                    villager.setNoAi(true);
                    l.addFreshEntity(villager);
                }
            }
            case "quickening_soil" -> {
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) l.setBlock(B.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int x = -4; x <= 4; x++) for (int z : new int[]{-4, 3}) {
                    l.setBlock(B.offset(x, 0, z), Blocks.FARMLAND.defaultBlockState(), 3);
                    l.setBlock(B.offset(x, 1, z), ((CropBlock) Blocks.WHEAT).getStateForAge(0), 3);
                }
                for (int[] s : new int[][]{{-4, -2}, {4, -2}, {-4, 1}, {4, 1}}) l.setBlock(B.offset(s[0], 1, s[1]), Blocks.OAK_SAPLING.defaultBlockState(), 3);
            }
            case "warming_hearth" -> {
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                    double d = Math.hypot(x, z);
                    if (d < 2 || d > 3.6) continue;
                    l.setBlock(B.offset(x, 0, z), Blocks.ICE.defaultBlockState(), 3);
                    if ((x + z) % 2 == 0) l.setBlock(B.offset(x, 1, z), Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                }
            }
            case "gathering_breeze" -> {
                RandomSource rnd = RandomSource.create(3);
                for (int i = 0; i < 14; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = 3 + rnd.nextDouble() * 3;
                    ItemStack st = new ItemStack(i % 3 == 0 ? Items.APPLE : i % 3 == 1 ? Items.STICK : Items.WHEAT_SEEDS, 1 + rnd.nextInt(3));
                    l.addFreshEntity(new ItemEntity(l, B.getX() + 0.5 + Math.cos(a) * d, B.getY() + 1.2, B.getZ() + 0.5 + Math.sin(a) * d, st));
                }
            }
            case "sentinel_bolts" -> {
                for (int x : new int[]{-5, 5}) {
                    var z = EntityType.ZOMBIE.create(l);
                    if (z == null) continue;
                    z.moveTo(B.getX() + 0.5 + x, B.getY() + 1, B.getZ() - 3.5, 180, 0);
                    z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                    l.addFreshEntity(z);
                }
            }
            default -> {
            }
        }
    }

    private static void events(ServerLevel l, String name, int t) {
        if (name.equals("petrification") && t == 170) {
            SurfacePlane floor = SurfacePlane.of(B.offset(3, 0, 2), Direction.UP);
            GlyphService.inscribe(l, floor, new GlyphSketch(floor.toUV(Vec3.atCenterOf(B.offset(3, 0, 2)).add(0, 0.5, 0)), 1.2).ring(0, 362)
                    .sigil(Sigil.EARTH, 0.8).signs(SignType.CRUSH, 4, Orientation.INWARD, 0.45, 45).strokes, null);
        }
        if (name.equals("snare_of_flame") && t == 90) {
            var pigs = l.getEntitiesOfClass(Pig.class, new AABB(B).inflate(8));
            pigs.stream().filter(p -> p.distanceToSqr(Vec3.atCenterOf(B)) > 4).findFirst()
                    .ifPresent(p -> p.teleportTo(B.getX() + 0.5, B.getY() + 1, B.getZ() + 0.5));
        }
        if (name.equals("feather_fall") && t == 60) {
            for (int k = -1; k <= 1; k += 2) {
                var pig = EntityType.PIG.create(l);
                if (pig == null) continue;
                pig.moveTo(B.getX() + 0.5 + k * 0.6, B.getY() + 9, B.getZ() + 0.5, 180, 0);
                l.addFreshEntity(pig);
            }
        }
    }

    private static void report(ServerLevel l, String name) {
        List<String> running = new ArrayList<>();
        for (var s : GlyphService.activeSpells(l)) {
            if (Ships.seen(l, s.program.origin()).distanceTo(Vec3.atCenterOf(B)) < 16) running.add(s.program.summary());
        }
        WitchsAtelier.LOGGER.info("[visualcheck] film {}: {}", name, running.isEmpty() ? "no spell running" : String.join(" + ", running));
    }

    private static Glyph page(GlyphLibrary.Recipe r, SurfacePlane plane, Pt c, double radius, Vec3 up) {
        var page = GlyphLibrary.draw(r, RandomSource.create(r.name().hashCode()), true, InkType.CONJURING);
        List<List<Pt>> st = GlyphLibrary.strokesOf(page);
        double k = radius / 0.85, rot = turn(plane, up);
        List<double[]> open = new ArrayList<>();
        for (List<Pt> s : st) open.add(place(s, c, k, rot));

        List<Pt> ring = st.get(0);
        double a0 = angle(ring.get(ring.size() - 1)), a1 = angle(ring.get(0));
        while (a1 <= a0) a1 += Math.PI * 2;
        List<Pt> close = new ArrayList<>();
        for (double a = a0 - 0.12; a <= a1 + 0.12; a += 0.04) close.add(new Pt(Math.cos(a) * 0.85, Math.sin(a) * 0.85));
        return new Glyph(plane, open, List.of(place(close, c, k, rot)));
    }

    private static Glyph sketch(SurfacePlane plane, Pt c, double radius, UnaryOperator<GlyphSketch> inside) {
        List<double[]> open = inside.apply(new GlyphSketch(c, radius).ring(0, 320)).strokes;
        return new Glyph(plane, open, new GlyphSketch(c, radius).ring(316, 364).strokes);
    }

    private static double angle(Pt p) {
        return Math.atan2(p.y(), p.x());
    }

    private static double turn(SurfacePlane plane, Vec3 up) {
        Vec3 pu = plane.planeVector(1, 0), pv = plane.planeVector(0, 1);
        return Math.atan2(up.dot(pv) / pv.lengthSqr(), up.dot(pu) / pu.lengthSqr()) - Math.PI / 2;
    }

    private static double[] place(List<Pt> s, Pt c, double k, double rot) {
        double[] out = new double[s.size() * 2];
        for (int i = 0; i < s.size(); i++) {
            Pt p = s.get(i).scale(k).rotate(rot).plus(c);
            out[i * 2] = p.x();
            out[i * 2 + 1] = p.y();
        }
        return out;
    }
}
