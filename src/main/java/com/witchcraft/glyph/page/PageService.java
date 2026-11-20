package com.witchcraft.glyph.page;

import com.witchcraft.glyph.GlyphService;
import com.witchcraft.glyph.ink.InkSupply;
import com.witchcraft.glyph.page.GlyphPages.Page;
import com.witchcraft.glyph.page.GlyphPages.PageStroke;
import com.witchcraft.glyph.model.Sigil;
import com.witchcraft.glyph.recognize.GlyphAnalyzer;
import com.witchcraft.glyph.recognize.GlyphReading;
import com.witchcraft.glyph.recognize.Pt;
import com.witchcraft.glyph.recognize.RingFinder;
import com.witchcraft.glyph.surface.SurfacePlane;
import com.witchcraft.registry.ModComponents;
import com.witchcraft.spell.ElementLook;
import com.witchcraft.spell.GlyphFrame;
import com.witchcraft.spell.SpellCompiler;
import com.witchcraft.spell.SpellProgram;
import com.witchcraft.spell.SpellProgram.Form;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Glyphs on pages held in the hand: paper, books and the palm quire. A page glyph casts from the hand the moment
 * its ring closes, so a witch prepares pages with a small gap and closes them when needed (one short stroke, or
 * the quick-close key). The page's ink burns up in the casting.
 */
public final class PageService {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Line width on a page, in page units. */
    public static final double PAGE_INK = 0.035;
    private static final double MAX_COORD = 1.08;

    private PageService() {}

    public static GlyphPages pages(ItemStack stack, PageKind kind) {
        GlyphPages gp = stack.get(ModComponents.GLYPH_PAGES.get());
        if (gp == null) return GlyphPages.blank(kind.pages);
        int n = gp.pages().size();
        boolean ok = kind.maxPages == kind.pages ? n == kind.pages : n <= kind.maxPages;
        return ok ? gp : GlyphPages.blank(kind.pages);
    }

    public static void handleStroke(ServerPlayer player, InteractionHand hand, int page, float[] pts) {
        ItemStack stack = player.getItemInHand(hand);
        PageKind kind = PageKind.of(stack);
        InkSupply supply = InkSupply.of(player);
        if (kind == null || kind.readOnly() || supply == null || supply.penHand == hand || pts.length < 2 || pts.length % 2 != 0) {
            LOGGER.debug("[page] stroke rejected: kind={} pen={} penHand={} hand={} pts={}", kind, supply != null,
                    supply == null ? null : supply.penHand, hand, pts.length);
            return;
        }
        double len = 0;
        for (int i = 0; i + 1 < pts.length; i += 2) {
            if (!Float.isFinite(pts[i]) || !Float.isFinite(pts[i + 1]) || Math.hypot(pts[i], pts[i + 1]) > MAX_COORD * 1.42) return;
            if (i >= 2) len += Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
        }
        int cost = Math.max(1, (int) Math.ceil(len * kind.scale * GlyphService.INK_PER_BLOCK));
        if (cost > supply.available()) {
            LOGGER.debug("[page] stroke rejected: needs {} ink, has {}", cost, supply.available());
            player.displayClientMessage(Component.translatable("message.witchcraft.pen_dry"), true);
            return;
        }
        supply.consume(cost);
        if (supply.ink == com.witchcraft.glyph.ink.InkType.BLOOD) com.witchcraft.entity.Taboo.add(player, 1);
        stack = single(player, hand, stack);
        GlyphPages gp = pages(stack, kind);
        if (gp.pages().isEmpty()) return;
        int p = Math.floorMod(page, gp.pages().size());
        gp = gp.withPage(p, gp.page(p).with(new PageStroke(pts, supply.ink))).withCurrent(p);
        save(stack, kind, gp);
        evaluate(player, hand, stack, kind, p);
    }

    public static void handleErase(ServerPlayer player, InteractionHand hand, int page, float u, float v) {
        ItemStack stack = player.getItemInHand(hand);
        PageKind kind = PageKind.of(stack);
        if (kind == null || !kind.erasable || InkSupply.of(player) == null) return;
        GlyphPages gp = pages(stack, kind);
        if (gp.pages().isEmpty()) return;
        int p = Math.floorMod(page, gp.pages().size());
        List<PageStroke> keep = new ArrayList<>();
        for (PageStroke s : gp.page(p).strokes()) {
            boolean hit = false;
            for (int i = 0; i + 1 < s.pts().length && !hit; i += 2) hit = Math.hypot(s.pts()[i] - u, s.pts()[i + 1] - v) < 0.07;
            if (!hit) keep.add(s);
        }
        if (keep.size() == gp.page(p).strokes().size()) return;
        save(stack, kind, gp.withPage(p, new Page(List.copyOf(keep))));
    }

    public static void handleTurn(ServerPlayer player, InteractionHand hand, int page) {
        ItemStack stack = player.getItemInHand(hand);
        PageKind kind = PageKind.of(stack);
        if (kind == null) return;
        GlyphPages gp = pages(stack, kind);
        if (gp.pages().size() <= 1) return;
        save(stack, kind, gp.withCurrent(page));
        if (kind == PageKind.GRIMOIRE) {
            // a page drawn by an older hand (before a sign was redrawn) is drawn again as the sign is now
            GlyphLibrary.refreshPage(stack);
            java.util.List<String> names = stack.get(com.witchcraft.registry.ModComponents.GRIMOIRE_ENTRIES.get());
            int p = Math.floorMod(page, gp.pages().size());
            if (names != null && p < names.size() && GlyphLibrary.IMAGINED_RING.equals(names.get(p))) com.witchcraft.glyph.Techniques.learnImaginedRing(player);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** Tears the page out of the palm quire; it lands crumpled on the ground (and lasts there five minutes). */
    public static void handleTear(ServerPlayer player, InteractionHand hand, int page) {
        ItemStack stack = player.getItemInHand(hand);
        if (PageKind.of(stack) != PageKind.PALM_QUIRE) return;
        GlyphPages gp = pages(stack, PageKind.PALM_QUIRE);
        if (gp.pages().isEmpty()) return;
        int p = Math.floorMod(page, gp.pages().size());
        Page torn = gp.page(p);
        List<Page> left = new ArrayList<>(gp.pages());
        left.remove(p);
        stack.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.copyOf(left), left.isEmpty() ? 0 : Math.min(p, left.size() - 1)));
        ItemStack crumpled = new ItemStack(com.witchcraft.registry.ModItems.TORN_PAGE.get());
        crumpled.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(torn), 0));
        player.drop(crumpled, false, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.witchcraft.registry.ModSounds.PAGE_TEAR.get(), SoundSource.PLAYERS, 1f, 1f);
    }

    /** Quick close: finish the ring on the current page of the held paper/book/quire by inking over its gap. */
    public static void handleQuickClose(ServerPlayer player) {
        InteractionHand hand = castable(player.getOffhandItem()) ? InteractionHand.OFF_HAND
                : castable(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : null;
        if (hand == null) return;
        ItemStack stack = player.getItemInHand(hand);
        PageKind kind = PageKind.of(stack);
        GlyphPages gp = pages(stack, kind);
        int p = gp.current();
        List<List<Pt>> pts = points(gp.page(p));
        GlyphReading r = GlyphAnalyzer.analyze(pts, PAGE_INK);
        if (r.ring() == null) {
            player.displayClientMessage(Component.translatable("message.witchcraft.no_ring"), true);
            return;
        }
        if (!r.closed()) {
            double[] gap = RingFinder.widestGap(pts, r.ring(), PAGE_INK);
            if (gap != null) {
                double pad = Math.toRadians(6);
                int n = Math.max(4, (int) Math.ceil((gap[1] - gap[0] + 2 * pad) / Math.toRadians(3)));
                float[] arc = new float[(n + 1) * 2];
                for (int i = 0; i <= n; i++) {
                    double a = gap[0] - pad + (gap[1] - gap[0] + 2 * pad) * i / n;
                    arc[i * 2] = (float) (r.ring().center().x() + Math.cos(a) * r.ring().radius());
                    arc[i * 2 + 1] = (float) (r.ring().center().y() + Math.sin(a) * r.ring().radius());
                }
                InkSupply supply = InkSupply.of(player);
                var ink = supply != null ? supply.ink : gp.page(p).strokes().get(0).ink();
                gp = gp.withPage(p, gp.page(p).with(new PageStroke(arc, ink)));
                save(stack, kind, gp);
            }
        }
        evaluate(player, hand, stack, kind, p);
    }

    private static boolean castable(ItemStack stack) {
        PageKind k = PageKind.of(stack);
        return k != null && !k.readOnly() && k != PageKind.BOOTS;
    }

    private static void evaluate(ServerPlayer player, InteractionHand hand, ItemStack stack, PageKind kind, int page) {
        GlyphPages gp = pages(stack, kind);
        Page pg = gp.page(page);
        GlyphReading r = GlyphAnalyzer.analyze(points(pg), PAGE_INK);
        if (r.ring() == null) return;
        if (!r.closed()) {
            LOGGER.debug("[page] {}", GlyphService.describeOpen(r));
            return;
        }
        com.witchcraft.glyph.DrawingDump.savePage(points(pg), PAGE_INK, r);
        double len = 0, pot = 0, dur = 0, mis = 0;
        for (int i : r.usedStrokes()) {
            PageStroke s = pg.strokes().get(i);
            double l = Math.max(0.02, s.length());
            len += l;
            pot += l * s.ink().potency;
            dur += l * s.ink().duration;
            mis += l * s.ink().misfire;
        }
        if (len == 0) { len = 1; pot = 1; dur = 1; }
        ServerLevel level = player.serverLevel();
        if (kind == PageKind.BOOTS) {
            // boots keep their glyph: it waits until the heels click together
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.8f);
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 origin = player.getEyePosition().add(look.scale(0.9)).subtract(0, 0.25, 0);
        SpellProgram program = SpellCompiler.compile(r, GlyphFrame.facing(look), origin, kind.scale, pot / len, dur / len, mis / len);
        if (program.form() == Form.FIZZLE) {
            GlyphService.fizzle(level, origin, GlyphFrame.facing(look), 0.35);
            LOGGER.debug("[page] {}", GlyphService.describeOpen(r));
            return;
        }

        boolean needsSurface = program.form() == Form.CRUSH || program.form() == Form.RESTORE;
        if (program.element() == Sigil.EARTH && program.form() == Form.BEAM) program = program.withForm(Form.BOLTS);
        if (needsSurface) {
            // earthworks need something to work on: the surface the caster looks at
            BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(look.scale(8)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                player.displayClientMessage(Component.translatable("message.witchcraft.no_target"), true);
                return;
            }
            SurfacePlane plane = SurfacePlane.of(hit.getBlockPos(), hit.getDirection());
            Vec3 at = hit.getLocation();
            program = SpellCompiler.compile(r, plane, at.add(plane.normal().scale(0.05)), kind.scale, pot / len, dur / len, mis / len);
            GlyphService.startSpell(level, plane, program, player, false);
        } else {
            GlyphService.startSpell(level, null, program, player, true);
        }
        if (program.form() == Form.PETRIFY) com.witchcraft.entity.Taboo.add(player, 40);
        com.witchcraft.advancement.MagicTrigger.cast(player, program, "page");
        LOGGER.debug("[cast] {}", program.summary());
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 1.6f);

        // the ink burns away in the casting: a sheet of paper is used up, a book or quire turns to its next page
        if (kind == PageKind.PAPER || kind == PageKind.TORN_PAGE) {
            stack.shrink(1);
        } else {
            save(stack, kind, gp.withPage(page, Page.EMPTY).withCurrent(page + 1));
        }
        int color = ElementLook.color(program.element(), program.centerSign());
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f, (color & 255) / 255f), 1.2f), origin.x, origin.y, origin.z, 20, 0.25, 0.25, 0.25, 0.02);
    }

    /**
     * Heels together (jump twice quickly): the glyph on the boots being worn comes alive, or, if it is already
     * running, stops. The glyph stays on the boots; every casting wears them a little.
     */
    public static void handleBootsJoin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var running = GlyphService.wornSpell(level, player.getUUID());
        if (running != null) {
            GlyphService.stop(level, running);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 0.7f);
            return;
        }
        ItemStack boots = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        if (PageKind.of(boots) != PageKind.BOOTS || !boots.has(ModComponents.GLYPH_PAGES.get())) return;
        Page pg = pages(boots, PageKind.BOOTS).page(0);
        GlyphReading r = GlyphAnalyzer.analyze(points(pg), PAGE_INK);
        if (r.ring() == null || !r.closed()) return;
        double len = 0, pot = 0, dur = 0, mis = 0;
        for (int i : r.usedStrokes()) {
            PageStroke s = pg.strokes().get(i);
            double l = Math.max(0.02, s.length());
            len += l;
            pot += l * s.ink().potency;
            dur += l * s.ink().duration;
            mis += l * s.ink().misfire;
        }
        if (len == 0) { len = 1; pot = 1; dur = 1; }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        Vec3 fwd = flat.lengthSqr() > 1e-4 ? flat.normalize() : new Vec3(0, 0, 1);
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 feet = player.position().add(0, 0.03, 0);
        SpellProgram program = SpellCompiler.compile(r, com.witchcraft.spell.ActiveSpell.wornFrame(right, fwd), feet, PageKind.BOOTS.scale,
                pot / len, dur / len, mis / len);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 1.6f);
        if (program.form() == Form.FIZZLE || program.form() == Form.CRUSH || program.form() == Form.RESTORE) {
            GlyphService.fizzle(level, feet, com.witchcraft.spell.ActiveSpell.wornFrame(right, fwd), 0.3);
            return;
        }
        var spell = GlyphService.startSpell(level, null, program, player, true);
        spell.worn = true;
        com.witchcraft.advancement.MagicTrigger.cast(player, program, "boots");
        boots.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.FEET);
        LOGGER.debug("[boots] {}", program.summary());
    }

    public static List<List<Pt>> pointsOf(Page p) {
        return points(p);
    }

    static List<List<Pt>> points(Page p) {
        List<List<Pt>> out = new ArrayList<>();
        for (PageStroke s : p.strokes()) out.add(s.points());
        return out;
    }

    private static void save(ItemStack stack, PageKind kind, GlyphPages gp) {
        boolean any = gp.pages().stream().anyMatch(pg -> !pg.isEmpty());
        if (!any && (kind == PageKind.PAPER)) {
            stack.remove(ModComponents.GLYPH_PAGES.get());
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            return;
        }
        stack.set(ModComponents.GLYPH_PAGES.get(), gp);
        if (kind == PageKind.PAPER || kind == PageKind.BOOK || kind == PageKind.BOOTS) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, any);
    }

    /** Drawing on a stack of paper (or books) takes one sheet off the stack; the rest goes back to the inventory. */
    private static ItemStack single(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (stack.getCount() <= 1 || stack.has(ModComponents.GLYPH_PAGES.get())) return stack;
        ItemStack sheet = stack.split(1);
        ItemStack rest = stack.copy();
        player.setItemInHand(hand, sheet);
        if (!player.getInventory().add(rest)) player.drop(rest, false);
        return sheet;
    }
}
