package com.fimkov.witchsatelier.glyph.page;

import com.fimkov.witchsatelier.advancement.MagicTrigger;
import com.fimkov.witchsatelier.entity.Taboo;
import com.fimkov.witchsatelier.entity.Witnesses;
import com.fimkov.witchsatelier.glyph.DrawingDump;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.Techniques;
import com.fimkov.witchsatelier.glyph.ink.InkSupply;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.page.GlyphPages.Page;
import com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke;
import com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.recognize.RingFinder;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.ElementLook;
import com.fimkov.witchsatelier.spell.GlyphFrame;
import com.fimkov.witchsatelier.spell.SpellCompiler;
import com.fimkov.witchsatelier.spell.SpellProgram.Form;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PageService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final double PAGE_INK = 0.035;
    private static final double MAX_COORD = 1.08;

    public static final int MAX_PAGE_STROKES = 480, MAX_PAGE_POINTS = 12000;

    public static boolean pageFull(Page page, int morePoints) {
        if (page.strokes().size() >= MAX_PAGE_STROKES) return true;
        int n = morePoints;
        for (PageStroke s : page.strokes()) n += s.pts().length / 2;
        return n > MAX_PAGE_POINTS;
    }

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
        Witnesses.saw(player);
        double len = 0;
        for (int i = 0; i + 1 < pts.length; i += 2) {
            if (!Float.isFinite(pts[i]) || !Float.isFinite(pts[i + 1]) || Math.hypot(pts[i], pts[i + 1]) > MAX_COORD * 1.42) return;
            if (i >= 2) len += Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
        }
        GlyphPages before = pages(stack, kind);
        if (!before.pages().isEmpty() && pageFull(before.page(Math.floorMod(page, before.pages().size())), pts.length / 2)) {
            player.displayClientMessage(Component.translatable("message.witchsatelier.page_full"), true);
            return;
        }
        int cost = Math.max(1, (int) Math.ceil(len * kind.scale * GlyphService.INK_PER_BLOCK));
        if (cost > supply.available()) {
            LOGGER.debug("[page] stroke rejected: needs {} ink, has {}", cost, supply.available());
            player.displayClientMessage(Component.translatable("message.witchsatelier.pen_dry"), true);
            return;
        }
        supply.consume(cost);
        if (supply.ink == InkType.BLOOD) Taboo.add(player, 1);
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
        stack = single(player, hand, stack);
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
        stack = single(player, hand, stack);
        save(stack, kind, gp.withCurrent(page));
        if (kind == PageKind.GRIMOIRE) {
            GlyphLibrary.refreshPage(stack);
            List<String> names = stack.get(ModComponents.GRIMOIRE_ENTRIES.get());
            int p = Math.floorMod(page, gp.pages().size());
            if (names != null && p < names.size() && GlyphLibrary.IMAGINED_RING.equals(names.get(p))) Techniques.learnImaginedRing(player);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

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
        ItemStack crumpled = new ItemStack(ModItems.TORN_PAGE.get());
        crumpled.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(torn), 0));
        player.drop(crumpled, false, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.PAGE_TEAR.get(), SoundSource.PLAYERS, 1f, 1f);
    }

    public static void handleQuickClose(ServerPlayer player) {
        InteractionHand hand = castable(player.getOffhandItem()) ? InteractionHand.OFF_HAND
                : castable(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : null;
        if (hand == null) return;
        Witnesses.saw(player);
        ItemStack stack = player.getItemInHand(hand);
        PageKind kind = PageKind.of(stack);
        GlyphPages gp = pages(stack, kind);
        int p = gp.current();
        List<List<Pt>> pts = points(gp.page(p));
        GlyphReading r = GlyphAnalyzer.analyze(pts, PAGE_INK);
        if (r.ring() == null) {
            player.displayClientMessage(Component.translatable("message.witchsatelier.no_ring"), true);
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
                if (pageFull(gp.page(p), arc.length / 2)) return;
                stack = single(player, hand, stack);
                gp = gp.withPage(p, gp.page(p).with(new PageStroke(arc, ink)));
                save(stack, kind, gp);
            }
        }
        evaluate(player, hand, stack, kind, p);
    }

    private static boolean castable(ItemStack stack) {
        PageKind k = PageKind.of(stack);
        return k != null && !k.readOnly() && k != PageKind.BOOTS && k != PageKind.ARTIFACT && k != PageKind.WITCH_GRIMOIRE;
    }

    private static void evaluate(ServerPlayer player, InteractionHand hand, ItemStack stack, PageKind kind, int page) {
        if (kind == PageKind.WITCH_GRIMOIRE) return;
        GlyphPages gp = pages(stack, kind);
        Page pg = gp.page(page);

        GlyphReading r = GlyphAnalyzer.analyze(kind == PageKind.BOOTS ? PageKind.joinSoles(points(pg)) : points(pg), PAGE_INK);
        if (r.ring() == null) return;
        if (!r.closed()) {
            LOGGER.debug("[page] {}", GlyphService.describeOpen(r));
            return;
        }
        if (kind == PageKind.ARTIFACT) {
            awaken(player, stack, r);
            return;
        }
        DrawingDump.savePage(points(pg), PAGE_INK, r);
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
            BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(look.scale(8)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                player.displayClientMessage(Component.translatable("message.witchsatelier.no_target"), true);
                return;
            }
            SurfacePlane plane = SurfacePlane.of(hit.getBlockPos(), hit.getDirection());
            Vec3 at = hit.getLocation();
            program = SpellCompiler.compile(r, plane, at.add(plane.normal().scale(0.05)), kind.scale, pot / len, dur / len, mis / len);
            GlyphService.startSpell(level, plane, program, player, false).bloodInk = inBlood(pg, r);
        } else {
            var spell = GlyphService.startSpell(level, null, program, player, true);
            spell.ticksLeft = Math.min(spell.ticksLeft, HELD_TICKS);
        }
        if (program.form() == Form.PETRIFY) Taboo.add(player, 40);
        MagicTrigger.cast(player, program, "page");
        LOGGER.debug("[cast] {}", program.summary());
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 1.6f);

        if (kind == PageKind.PAPER || kind == PageKind.TORN_PAGE) {
            stack.shrink(1);
        } else {
            save(stack, kind, gp.withPage(page, Page.EMPTY).withCurrent(page + 1));
        }
        int color = ElementLook.color(program.element(), program.centerSign());
        level.sendParticles(new DustParticleOptions(new Vector3f(((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f, (color & 255) / 255f), 1.2f), origin.x, origin.y, origin.z, 20, 0.25, 0.25, 0.25, 0.02);
    }

    private static void awaken(ServerPlayer player, ItemStack stack, GlyphReading r) {
        ServerLevel level = player.serverLevel();
        GlyphLibrary.Recipe want = Awakening.glyphOf(stack);
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.7)).subtract(0, 0.3, 0);
        if (want == null || !GlyphLibrary.readsAs(want, r)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.01);
            return;
        }
        Awakening.wake(stack);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.2f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RING_IGNITE.get(), SoundSource.PLAYERS, 0.8f, 1.1f);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 24, 0.25, 0.25, 0.25, 0.04);
        MagicTrigger.fire(player, "awaken");
    }

    public static void handleBootsJoin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var running = GlyphService.wornSpell(level, player.getUUID());
        if (running != null) {
            GlyphService.stop(level, running);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 0.7f);
            return;
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (PageKind.of(boots) != PageKind.BOOTS || !boots.has(ModComponents.GLYPH_PAGES.get())) return;
        Page pg = pages(boots, PageKind.BOOTS).page(0);
        GlyphReading r = GlyphAnalyzer.analyze(PageKind.joinSoles(points(pg)), PAGE_INK);
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
        SpellProgram program = SpellCompiler.compile(r, ActiveSpell.wornFrame(right, fwd), feet, PageKind.BOOTS.scale,
                pot / len, dur / len, mis / len);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 1.6f);
        if (program.form() == Form.FIZZLE || program.form() == Form.CRUSH || program.form() == Form.RESTORE) {
            GlyphService.fizzle(level, feet, ActiveSpell.wornFrame(right, fwd), 0.3);
            return;
        }
        var spell = GlyphService.startSpell(level, null, program, player, true);
        spell.worn = true;

        if (program.form() == Form.LIFT) spell.ticksLeft = Integer.MAX_VALUE / 2;
        MagicTrigger.cast(player, program, "boots");
        boots.hurtAndBreak(1, player, EquipmentSlot.FEET);
        LOGGER.debug("[boots] {}", program.summary());
    }

    public static boolean hasBootsGlyph(net.minecraft.world.entity.player.Player p) {
        ItemStack boots = p.getItemBySlot(EquipmentSlot.FEET);
        if (PageKind.of(boots) != PageKind.BOOTS) return false;
        GlyphPages gp = boots.get(ModComponents.GLYPH_PAGES.get());
        return gp != null && !gp.pages().isEmpty() && !gp.page(0).isEmpty();
    }

    public static void washBoots(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isInWater() || p.tickCount % 5 != 0) return;
        ItemStack boots = p.getItemBySlot(EquipmentSlot.FEET);
        if (PageKind.of(boots) != PageKind.BOOTS) return;
        GlyphPages gp = boots.get(ModComponents.GLYPH_PAGES.get());
        if (gp == null || gp.page(0).isEmpty()) return;
        boolean lasting = gp.page(0).strokes().stream().allMatch(s -> s.ink().waterproof);
        if (lasting) return;
        save(boots, PageKind.BOOTS, gp.withPage(0, Page.EMPTY));
        var running = GlyphService.wornSpell(p.serverLevel(), p.getUUID());
        if (running != null) GlyphService.stop(p.serverLevel(), running);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.8f);
        p.serverLevel().sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 0.1, p.getZ(), 12, 0.2, 0.05, 0.2, 0.02);
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
        if (kind == PageKind.PAPER || kind == PageKind.BOOK || kind == PageKind.BOOTS) {
            if (any) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            else stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    public static final int CARRIED_TICKS = 200, HELD_TICKS = 1200, TREAD_WAIT_TICKS = 6000;

    public static ActiveSpell castOnSurface(ServerLevel level, Page pg, SurfacePlane plane,
                                                                            Vec3 at, UUID caster) {
        if (pg == null || pg.isEmpty()) return null;
        GlyphReading r = GlyphAnalyzer.analyze(points(pg), PAGE_INK);
        if (r.ring() == null) return null;
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
        SpellProgram program = SpellCompiler.compile(r, plane, at.add(plane.normal().scale(0.05)), PageKind.PAPER.scale, pot / len, dur / len, mis / len);
        if (program.form() == Form.FIZZLE) {
            GlyphService.fizzle(level, at, plane, 0.35);
            return null;
        }
        var spell = GlyphService.startSpell(level, plane, program, caster, false);
        spell.windowMark = com.fimkov.witchsatelier.windowway.WindowMark.of(r);
        spell.fromPage = true;
        spell.bloodInk = inBlood(pg, r);
        spell.ticksLeft = Math.min(spell.ticksLeft, CARRIED_TICKS);
        spell.expiresAt = level.getGameTime() + (program.has(SpellProgram.TREAD) ? TREAD_WAIT_TICKS : CARRIED_TICKS) + CARRIED_TICKS;
        if (program.form() == Form.PETRIFY && caster != null && level.getPlayerByUUID(caster) instanceof ServerPlayer sp) Taboo.add(sp, 40);
        int color = ElementLook.color(program.element(), program.centerSign());
        level.sendParticles(new DustParticleOptions(new Vector3f(((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f, (color & 255) / 255f), 1.2f), at.x, at.y, at.z, 16, 0.25, 0.25, 0.25, 0.02);
        return spell;
    }

    /** Whether the strokes a page's glyph was read from are mostly blood ink. */
    static boolean inBlood(Page pg, GlyphReading r) {
        double all = 0, blood = 0;
        for (int i : r.usedStrokes()) {
            PageStroke s = pg.strokes().get(i);
            double l = Math.max(0.02, s.length());
            all += l;
            if (s.ink() == InkType.BLOOD) blood += l;
        }
        return all > 0 && blood * 2 > all;
    }

    public static final int TITLE_MAX = 40, NOTES_MAX = 320;

    public static void handleCopy(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        ItemStack book = player.getItemInHand(hand);
        if (PageKind.of(book) != PageKind.WITCH_GRIMOIRE) return;
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        Page found = null;
        String title = "";
        if (PageKind.of(other) == PageKind.GRIMOIRE) {
            GlyphPages from = pages(other, PageKind.GRIMOIRE);
            int at = Math.floorMod(from.current(), Math.max(1, from.pages().size()));
            found = from.page(at);
            List<String> names = other.get(ModComponents.GRIMOIRE_ENTRIES.get());
            if (names != null && at < names.size() && !names.get(at).isEmpty()) title = "#glyph.witchsatelier." + names.get(at);
        } else if (hit != null && player.level() instanceof ServerLevel level) {
            var plane = SurfacePlane.of(hit.getBlockPos(), hit.getDirection());
            found = GlyphService.glyphAsPage(level, plane, plane.toUV(hit.getLocation()), player);
        }
        if (found == null || found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.witchsatelier.grimoire.nothing"), true);
            return;
        }
        if (pageFull(found, 0)) {
            player.displayClientMessage(Component.translatable("message.witchsatelier.page_full"), true);
            return;
        }
        book = single(player, hand, book);
        GlyphPages gp = pages(book, PageKind.WITCH_GRIMOIRE);
        int target = gp.page(gp.current()).isEmpty() ? gp.current() : -1;
        for (int i = 0; target < 0 && i < gp.pages().size(); i++) if (gp.page(i).isEmpty()) target = i;
        if (target < 0) {
            player.displayClientMessage(Component.translatable("message.witchsatelier.grimoire.full"), true);
            return;
        }
        save(book, PageKind.WITCH_GRIMOIRE, gp.withPage(target, found).withCurrent(target));
        setNote(book, target, title, null);
        claim(book, player);
        Witnesses.saw(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.QUILL_SCRATCH.get(), SoundSource.PLAYERS, 1f, 1f);
        player.displayClientMessage(Component.translatable("message.witchsatelier.grimoire.copied", target + 1), true);
    }

    public static void handleNote(ServerPlayer player, InteractionHand hand, int page, String title, String notes) {
        ItemStack book = player.getItemInHand(hand);
        if (PageKind.of(book) != PageKind.WITCH_GRIMOIRE) return;
        book = single(player, hand, book);
        GlyphPages gp = pages(book, PageKind.WITCH_GRIMOIRE);
        if (!book.has(ModComponents.GLYPH_PAGES.get())) save(book, PageKind.WITCH_GRIMOIRE, gp);
        setNote(book, Math.floorMod(page, gp.pages().size()), clean(title, TITLE_MAX), clean(notes, NOTES_MAX));
        claim(book, player);
    }

    private static String clean(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("[\\p{Cntrl}&&[^\n]]", "");
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static void setNote(ItemStack book, int i, String title, String notes) {
        int n = PageKind.WITCH_GRIMOIRE.maxPages;
        List<String> titles = new ArrayList<>(book.getOrDefault(ModComponents.PAGE_TITLES.get(), List.of()));
        List<String> texts = new ArrayList<>(book.getOrDefault(ModComponents.PAGE_NOTES.get(), List.of()));
        while (titles.size() < n) titles.add("");
        while (texts.size() < n) texts.add("");
        titles.set(i, title == null ? "" : title);
        if (notes != null) texts.set(i, notes);
        book.set(ModComponents.PAGE_TITLES.get(), List.copyOf(titles));
        book.set(ModComponents.PAGE_NOTES.get(), List.copyOf(texts));
    }

    private static void claim(ItemStack book, ServerPlayer player) {
        if (!book.has(ModComponents.KEEPER.get())) book.set(ModComponents.KEEPER.get(), player.getGameProfile().getName());
    }

    private static ItemStack single(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (stack.getCount() <= 1 || Awakening.wakesAsStack(stack)) return stack;
        ItemStack sheet = stack.split(1);
        ItemStack rest = stack.copy();
        player.setItemInHand(hand, sheet);
        int free = player.getInventory().getFreeSlot();
        if (free >= 0) player.getInventory().setItem(free, rest);
        else player.drop(rest, false);
        return sheet;
    }
}
