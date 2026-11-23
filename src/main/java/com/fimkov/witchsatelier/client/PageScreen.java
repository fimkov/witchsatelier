package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.glyph.recognize.ImaginedRing;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PageScreen extends Screen {
    private static final int PARCHMENT = 0xFFE9DEC2;
    private static final int PAPER = 0xFFF3EFE4;
    private static final int EDGE = 0xFF6B4E2E;

    private final InteractionHand hand;
    private int page;
    private float[] cur = new float[512];
    private int count;
    private boolean drawing, erasing;
    private long lastErase;
    private long lastScratch;

    public PageScreen(InteractionHand hand) {
        super(Component.translatable("screen.witchsatelier.page"));
        this.hand = hand;
        ItemStack st = stack();
        GlyphPages gp = st.get(ModComponents.GLYPH_PAGES.get());
        this.page = gp == null ? 0 : gp.current();
    }

    public PageScreen(InteractionHand hand, int page) {
        this(hand);
        this.page = page;
    }

    private ItemStack stack() {
        return Minecraft.getInstance().player.getItemInHand(hand);
    }

    private PageKind kind() {
        return PageKind.of(stack());
    }

    private float radius() {
        return Math.min(width, height) * 0.36f;
    }

    private float[] toPage(double mx, double my) {
        PageKind k = kind();
        float r = radius(), cx = width / 2f;
        if (k != null && k.book()) {
            r = Math.min(r, width / 4.5f);
            cx = width / 2f + r;
        }
        return new float[]{(float) ((mx - cx) / r), (float) (-(my - height / 2.0) / r)};
    }

    private boolean onPage(float[] p) {
        PageKind k = kind();
        if (k == null || pageCount() == 0) return false;
        if (k == PageKind.BOOTS) return onSole(p[0], p[1]);
        return k.round ? p[0] * p[0] + p[1] * p[1] <= 1.0f : Math.abs(p[0]) <= 1 && Math.abs(p[1]) <= 1;
    }

    private static float soleHalfWidth(float y) {
        if (y > 0.98f || y < -0.98f) return 0;
        double t = (y + 0.98) / 1.96;
        double w = t > 0.8 ? Math.sqrt(Math.max(0, 1 - Math.pow((t - 0.8) / 0.2, 2))) * 0.47 : 0.47;
        if (t < 0.18) w = Math.sqrt(Math.max(0, 1 - Math.pow((0.18 - t) / 0.18, 2))) * 0.40;
        double waist = Math.exp(-Math.pow((t - 0.42) / 0.12, 2));
        w *= 1 - 0.2 * waist;
        if (t >= 0.18 && t < 0.3) w = 0.40 + (w - 0.40) * (t - 0.18) / 0.12;
        return (float) w;
    }

    private static boolean onSole(float x, float y) {
        float hw = soleHalfWidth(y);
        return hw > 0 && (Math.abs(x + 0.5f) <= hw || Math.abs(x - 0.5f) <= hw);
    }

    private void renderSoles(GuiGraphics g, float cx, float cy, float r) {
        int top = Math.round(cy - r), bottom = Math.round(cy + r);
        for (int py = top; py <= bottom; py++) {
            float y = -(py - cy) / r;
            float hw = soleHalfWidth(y);
            if (hw <= 0) continue;
            boolean heel = y < -0.55f;
            for (int side = -1; side <= 1; side += 2) {
                float mid = cx + side * 0.5f * r;
                int x0 = Math.round(mid - hw * r), x1 = Math.round(mid + hw * r);
                g.fill(x0, py, x1, py + 1, 0xFF5B3F2C);
                int inset = Math.max(2, Math.round(r * 0.035f));
                if (x1 - x0 > inset * 2 && soleHalfWidth(y + inset / r) > 0 && soleHalfWidth(y - inset / r) > 0) {
                    g.fill(x0 + inset, py, x1 - inset, py + 1, heel ? 0xFFB29274 : 0xFFC9AB88);
                }
            }
            if (Math.abs(y + 0.55f) < 0.6f / r) {
                for (int side = -1; side <= 1; side += 2) {
                    float mid = cx + side * 0.5f * r;
                    g.fill(Math.round(mid - hw * r), py, Math.round(mid + hw * r), py + 1, 0xFF7A5A40);
                }
            }
        }

        for (int py = Math.round(cy - r * 0.9f); py < Math.round(cy + r * 0.9f); py += 6) {
            g.fill(Math.round(cx) - 1, py, Math.round(cx) + 1, py + 3, 0x66FFF2D8);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        PageKind k = kind();
        if (k == null) { onClose(); return; }
        if (erasing && Util.getMillis() - lastErase > 60) {
            float[] p = toPage(mouseX, mouseY);
            PacketDistributor.sendToServer(new Payloads.PageErase(hand, page, p[0], p[1]));
            lastErase = Util.getMillis();
        }

        float cx = width / 2f, cy = height / 2f, r = radius();
        if (k.book()) {
            r = Math.min(r, width / 4.5f);
            cx = width / 2f + r;
            renderBookBody(g, width / 2f, cy, r);
            renderExplanation(g, width / 2f - r, cy, r);
        }

        String tex = k.round ? "page_round" : k == PageKind.BOOK || k.book() ? "page_book" : "page_square";
        int size = Math.round(r * 2);
        RenderSystem.enableBlend();
        if (k == PageKind.BOOTS) {
            renderSoles(g, cx, cy, r);
        } else if (k == PageKind.ARTIFACT) {
            ItemStack it = stack();
            boolean upright = it.getItem() instanceof TieredItem || it.is(ModItems.TENT_STAFF.get());
            float big = r * (upright ? 2.5f : 2.1f);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            if (upright) g.pose().mulPose(Axis.ZP.rotationDegrees(-45));
            g.pose().scale(big / 16f, big / 16f, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(it, 0, 0);
            g.pose().popPose();
        } else {
            g.blit(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/gui/" + tex + ".png"),
                    Math.round(cx - r), Math.round(cy - r), size, size, 0, 0, 256, 256, 256, 256);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());

        var guide = Awakening.guideFor(stack());
        if (guide != null) renderGuide(vc, m, guide, cx, cy, r, k == PageKind.BOOTS);
        if (k == PageKind.GRIMOIRE && isBasics()) renderAnatomy(g, vc, m, cx, cy, r);
        if (k == PageKind.GRIMOIRE && isEntry(GlyphLibrary.IMAGINED_RING)) renderImaginedDiagram(g, vc, m, cx, cy, r);

        float w = (float) (PageService.PAGE_INK * r);
        GlyphPages gp = stack().get(ModComponents.GLYPH_PAGES.get());
        if (gp != null) {
            for (GlyphPages.PageStroke s : gp.page(page).strokes()) {
                line(vc, m, s.pts(), s.pts().length / 2, cx, cy, r, w, s.ink().invisible ? 0x70503A90 : s.ink().color | 0xFF000000);
            }
        }
        if (count > 0 && imagining) {
            g.pose().pushPose();
            g.pose().translate(0, -3, 0);
            Matrix4f lifted = g.pose().last().pose();
            line(vc, m, cur, count, cx, cy + 3, r, w * 0.7f, 0x203A2A1A);
            line(vc, lifted, cur, count, cx, cy, r, w * 2.2f, 0x389A84FF);
            line(vc, lifted, cur, count, cx, cy, r, w * 0.7f, 0xC0B4A4FF);
            g.pose().popPose();
        } else if (count > 0) {
            line(vc, m, cur, count, cx, cy, r, w, ClientDrawing.currentInk().color | 0xFF000000);
        }
        g.flush();
        g.pose().popPose();
        renderPenCursor(g, mouseX, mouseY);

        if (k.book()) renderGutter(g, width / 2f, cy, r);
        int count = pageCount();
        if (count > 1) {
            if (!k.book()) renderPageEdges(g, cx, cy, r, count, k.round);
            renderBookmark(g, k.book() ? cx - r * 1.6f : cx, cy, r, page + 1);
        }
        renderFlip(g, k, cx, cy, r);
    }

    private static final long FLIP_MS = 420;

    private void renderFlip(GuiGraphics g, PageKind k, float cx, float cy, float r) {
        long dt = Util.getMillis() - flipStart;
        if (dt > FLIP_MS || k == PageKind.BOOTS || flipDir == 0) return;
        float t = dt / (float) FLIP_MS;
        float e = t * t * (3 - 2 * t);

        double angle = flipDir > 0 ? e * Math.PI : (1 - e) * Math.PI;
        float spine = k.book() ? width / 2f : cx - r;
        float size = r * 2;
        String tex = k.round ? "page_round" : k == PageKind.BOOK || k.book() ? "page_book" : "page_square";
        var loc = ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/gui/" + tex + ".png");

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);

        float lift = (float) Math.sin(angle);
        int shadowA = (int) (70 * lift);
        if (shadowA > 0) {
            float reach = (float) (size * Math.abs(Math.cos(angle)) + size * 0.12f * lift);
            boolean over = Math.cos(angle) < 0;
            int x0 = Math.round(over ? spine - reach : spine), x1 = Math.round(over ? spine : spine + reach);
            g.fill(x0, Math.round(cy - r), x1, Math.round(cy + r), shadowA << 24 | 0x1A1008);
        }
        int strips = 28;
        double x = spine;
        double curl = 0.55 * lift;
        for (int i = 0; i < strips; i++) {
            double s0 = (double) i / strips, s1 = (double) (i + 1) / strips;
            double a = angle - curl * (s0 + s1) / 2 * (flipDir > 0 ? 1 : -1);
            double nx = x + Math.cos(a) * size / strips;
            float h = (float) (r * (1 + 0.07 * Math.sin(a) * s1));
            int xa = (int) Math.round(Math.min(x, nx)), xb = (int) Math.round(Math.max(x, nx));
            if (xb > xa) {
                float shade = (float) (0.62 + 0.38 * Math.abs(Math.cos(a)));
                RenderSystem.setShaderColor(shade, shade, shade * 0.96f, 1);
                boolean back = Math.cos(a) < 0;
                int u = (int) Math.round((back ? 1 - s1 : s0) * 256);
                int uw = Math.max(1, (int) Math.round(256.0 / strips));
                g.blit(loc, xa, Math.round(cy - h), xb - xa, Math.round(h * 2), u, 0, uw, 256, 256, 256);
            }
            x = nx;
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.pose().popPose();
    }

    private boolean isBasics() {
        return isEntry(GlyphLibrary.BASICS);
    }

    private boolean isEntry(String name) {
        List<String> names = stack().get(ModComponents.GRIMOIRE_ENTRIES.get());
        return names != null && page < names.size() && name.equals(names.get(page));
    }

    private void renderImaginedDiagram(GuiGraphics g, VertexConsumer vc, Matrix4f m, float cx, float cy, float r) {
        float w = (float) (PageService.PAGE_INK * r) * 0.8f;
        float rr = 0.55f, lift = 0.07f;
        double gap = Math.toRadians(26), a0 = Math.toRadians(60);
        int n = 96;
        float[] inked = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = a0 + (Math.PI * 2 - gap) * i / n;
            inked[i * 2] = (float) (Math.cos(a) * rr);
            inked[i * 2 + 1] = (float) (Math.sin(a) * rr - 0.04f);
        }
        line(vc, m, inked, n + 1, cx, cy, r, w, 0xFF2A1E3A);

        for (int lap = 0; lap < 2; lap++) {
            float lr = rr * (lap == 0 ? 1.04f : 0.97f), ly = lift * (lap == 0 ? 1.5f : 1f);
            for (int i = lap; i < 48; i += 2) {
                float[] dash = new float[8];
                for (int k = 0; k < 4; k++) {
                    double a = Math.PI * 2 * (i + k / 3.0) / 48;
                    dash[k * 2] = (float) (Math.cos(a) * lr);
                    dash[k * 2 + 1] = (float) (Math.sin(a) * lr + ly);
                }
                line(vc, m, dash, 4, cx, cy, r, w * 0.6f, lap == 0 ? 0x808A6AE8 : 0xB08A6AE8);
            }
        }
        g.flush();
        int ink = 0xFF5A3A2A;
        label(g, cx, cy, r, -rr * 0.74f, -rr * 0.74f + lift * 1.5f, -0.84f, -0.8f, "glyph.witchsatelier.part.imagined", ink);
        label(g, cx, cy, r, (float) Math.cos(a0 - gap / 2) * rr, (float) Math.sin(a0 - gap / 2) * rr - 0.04f, 0.84f, 0.8f, "glyph.witchsatelier.part.gap", ink);
        label(g, cx, cy, r, rr * 0.71f, -rr * 0.71f - 0.04f, 0.84f, -0.8f, "glyph.witchsatelier.part.inked", ink);
    }

    private static GlyphPages.Page anatomy;

    private void renderAnatomy(GuiGraphics g, VertexConsumer vc, Matrix4f m, float cx, float cy, float r) {
        if (anatomy == null) anatomy = GlyphLibrary.draw(GlyphLibrary.byName("pillar_of_flame"),
                RandomSource.create(3), true, InkType.CONJURING);
        float w = (float) (PageService.PAGE_INK * r) * 0.8f;
        float s = 0.62f;
        for (GlyphPages.PageStroke st : anatomy.strokes()) {
            float[] p = st.pts().clone();
            for (int i = 0; i < p.length; i++) p[i] *= s;
            line(vc, m, p, p.length / 2, cx, cy, r, w, 0xFF2A1E3A);
        }
        g.flush();
        int ink = 0xFF5A3A2A;

        label(g, cx, cy, r, -0.85f * s * 0.71f, 0.85f * s * 0.71f, -0.84f, 0.8f, "glyph.witchsatelier.part.ring", ink);
        label(g, cx, cy, r, 0.0f, -0.12f * s, -0.84f, -0.8f, "glyph.witchsatelier.part.sigil", ink);
        label(g, cx, cy, r, 0.53f * s * 0.71f, -0.53f * s * 0.71f, 0.84f, -0.8f, "glyph.witchsatelier.part.sign", ink);

        float[] ring = anatomy.strokes().get(0).pts();
        float gx = ring[0] + ring[ring.length - 2], gy = ring[1] + ring[ring.length - 1];
        float gl = (float) Math.hypot(gx, gy);
        if (gl > 1e-3) {
            gx = gx / gl * 0.85f * s;
            gy = gy / gl * 0.85f * s;
            label(g, cx, cy, r, gx, gy, gx < 0 ? -0.84f : 0.84f, gy < -0.2f ? -0.55f : 0.8f, "glyph.witchsatelier.part.gap", ink);
        }
    }

    private void label(GuiGraphics g, float cx, float cy, float r, float px, float py, float tx, float ty, String key, int color) {
        Component text = Component.translatable(key);
        int tw = font.width(text);
        int left = Math.round(cx - r * 0.9f), right = Math.round(cx + r * 0.9f);
        int ax = Math.round(cx + px * r), ay = Math.round(cy - py * r), bx = Math.round(cx + tx * r), by = Math.round(cy - ty * r);
        int textX = tx < 0 ? bx : bx - tw;
        textX = Math.max(left, Math.min(right - tw, textX));
        int textY = ty > 0 ? by - font.lineHeight - 1 : by + 2;

        bx = tx < 0 ? textX : textX + tw;
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (int i = 0; i <= steps; i += 2) {
            int x = ax + (bx - ax) * i / Math.max(1, steps), y = ay + (by - ay) * i / Math.max(1, steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
        g.fill(textX, by, textX + tw, by + 1, (color & 0x00FFFFFF) | 0x50000000);
        g.drawString(font, text, textX, textY, color, false);
    }

    private static final ResourceLocation COVER =
            ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/gui/grimoire_cover.png");
    private static final ResourceLocation BOOK_PAGE =
            ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/gui/page_book.png");

    private void renderBookBody(GuiGraphics g, float spine, float cy, float r) {
        RenderSystem.enableBlend();
        float pad = r * 0.075f;
        int x0 = Math.round(spine - 2 * r - pad), x1 = Math.round(spine + 2 * r + pad);
        int y0 = Math.round(cy - r - pad), y1 = Math.round(cy + r + pad * 1.4f);

        for (int i = 1; i <= 4; i++) g.fill(x0 - i + 2, y0 + i + 1, x1 + i - 2, y1 + i + 1, (0x16 << 24));
        g.blit(COVER, x0, y0, x1 - x0, y1 - y0, 0, 0, 512, 256, 512, 256);
        int count = Math.max(1, pageCount());
        int before = Math.min(4, (page + 2) / 3), after = Math.min(4, (count - page + 1) / 3);
        int top = Math.round(cy - r), bottom = Math.round(cy + r);
        for (int side = 0; side < 2; side++) {
            int layers = side == 0 ? before : after;
            for (int i = layers; i >= 1; i--) {
                int shade = i % 2 == 0 ? 0xFFD9CBA6 : 0xFFCDBE97;
                float o = i * 1.3f;
                if (side == 0) {
                    g.fill(Math.round(spine - 2 * r - o), top + Math.round(o * 0.6f), Math.round(spine), bottom + Math.round(o), shade);
                } else {
                    g.fill(Math.round(spine), top + Math.round(o * 0.6f), Math.round(spine + 2 * r + o), bottom + Math.round(o), shade);
                }
            }
        }
    }

    private void renderGutter(GuiGraphics g, float spine, float cy, float r) {
        int top = Math.round(cy - r), bottom = Math.round(cy + r);
        int reach = Math.max(6, Math.round(r * 0.16f));
        int sx = Math.round(spine);
        for (int i = 0; i < reach; i++) {
            float t = 1 - i / (float) reach;
            int a = Math.round(0x58 * t * t);
            g.fill(sx + i, top, sx + i + 1, bottom, a << 24 | 0x2A1A10);
            g.fill(sx - i - 1, top, sx - i, bottom, a << 24 | 0x2A1A10);
        }
        g.fill(sx - 1, top, sx + 1, bottom, 0x803A2418);
    }

    private void renderExplanation(GuiGraphics g, float cx, float cy, float r) {
        int size = Math.round(r * 2);
        RenderSystem.enableBlend();

        g.blit(BOOK_PAGE, Math.round(cx - r), Math.round(cy - r), size, size, 256, 0, -256, 256, 256, 256);
        renderContentsTab(g, cx, cy, r);
        if (kind() == PageKind.WITCH_GRIMOIRE) {
            renderNotesPage(g, cx, cy, r);
            return;
        }
        List<String> names = stack().get(ModComponents.GRIMOIRE_ENTRIES.get());
        if (contents && names != null) {
            renderContents(g, cx, cy, r, names.stream().map(n -> n.isEmpty() ? null
                    : (Component) Component.translatable("glyph.witchsatelier." + n)).toList());
            return;
        }
        String entry = names == null || page >= names.size() ? "" : names.get(page);
        if (entry.isEmpty()) return;
        int x0 = Math.round(cx - r * 0.8f), w = Math.round(r * 1.6f);
        int y = Math.round(cy - r * 0.8f);
        Component title = Component.translatable("glyph.witchsatelier." + entry);
        g.pose().pushPose();
        g.pose().translate(x0, y, 0);
        g.pose().scale(1.35f, 1.35f, 1);
        g.drawString(font, title, 0, 0, 0xFF4A2414, false);
        g.pose().popPose();
        y += Math.round(font.lineHeight * 1.35f) + 6;
        g.fill(x0, y - 3, x0 + w, y - 2, 0x553A2A1A);
        for (var line : font.split(Component.translatable("glyph.witchsatelier." + entry + ".desc"), w)) {
            if (y > cy + r * 0.86f) break;
            g.drawString(font, line, x0, y, 0xFF3B2A1E, false);
            y += font.lineHeight + 1;
        }
    }

    private boolean contents;

    private final List<int[]> contentRows = new ArrayList<>();
    private int[] tabRect = new int[4];
    private int mouseX, mouseY;

    private void renderContentsTab(GuiGraphics g, float cx, float cy, float r) {
        Component label = Component.translatable("screen.witchsatelier.contents");
        int w = font.width(label) + 10, h = 13;
        int x0 = Math.round(cx - r * 0.78f), y1 = Math.round(cy - r) + 2, y0 = y1 - h;
        boolean hover = mouseX >= x0 && mouseX < x0 + w && mouseY >= y0 && mouseY < y1;
        int cloth = contents ? 0xFF2F5A3A : hover ? 0xFF3E7A4C : 0xFF356A43;
        g.fill(x0, y0, x0 + w, y1, cloth);
        g.fill(x0, y0, x0 + w, y0 + 1, 0xFF5E9A6A);
        g.fill(x0, y0, x0 + 1, y1, 0xFF22432B);
        g.drawString(font, label, x0 + 5, y0 + 3, 0xFFEDE6C8, false);
        tabRect = new int[]{x0, y0, x0 + w, y1};
    }

    private void renderContents(GuiGraphics g, float cx, float cy, float r, List<Component> names) {
        contentRows.clear();
        int x0 = Math.round(cx - r * 0.8f), x1 = Math.round(cx + r * 0.8f);
        int y = Math.round(cy - r * 0.8f);
        g.pose().pushPose();
        g.pose().translate(x0, y, 0);
        g.pose().scale(1.35f, 1.35f, 1);
        g.drawString(font, Component.translatable("screen.witchsatelier.contents"), 0, 0, 0xFF4A2414, false);
        g.pose().popPose();
        y += Math.round(font.lineHeight * 1.35f) + 6;
        g.fill(x0, y - 3, x1, y - 2, 0x553A2A1A);
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i) == null) continue;
            if (y > cy + r * 0.86f) break;
            int rowH = font.lineHeight + 3;
            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y - 1 && mouseY < y - 1 + rowH;
            if (hover || i == page) g.fill(x0 - 2, y - 1, x1, y - 1 + rowH, hover ? 0x30A0602A : 0x18A0602A);
            String num = Integer.toString(i + 1);
            Component name = names.get(i);
            g.drawString(font, name, x0, y + 1, 0xFF3B2A1E, false);

            int nameEnd = x0 + font.width(name) + 3, numX = x1 - font.width(num);
            for (int d = nameEnd; d < numX - 3; d += 3) g.fill(d, y + font.lineHeight - 1, d + 1, y + font.lineHeight, 0x803A2A1A);
            g.drawString(font, num, numX, y + 1, 0xFF6A4A30, false);
            contentRows.add(new int[]{x0 - 2, y - 1, x1, y - 1 + rowH, i});
            y += rowH;
        }
    }

    private int pageCount() {
        GlyphPages gp = stack().get(ModComponents.GLYPH_PAGES.get());
        PageKind k = kind();
        return gp != null ? gp.pages().size() : k == null ? 0 : k.pages;
    }

    private void renderPageEdges(GuiGraphics g, float cx, float cy, float r, int count, boolean round) {
        int left = Math.max(0, count - 1 - page);
        int layers = Math.min(6, (left + 2) / 3);
        for (int i = layers; i >= 1; i--) {
            int shade = 0xFFE2D6B8 - i * 0x00080808;
            float o = i * 1.6f;
            if (round) {
                g.fill(Math.round(cx + r - 2), Math.round(cy - r * 0.55f + o), Math.round(cx + r + o), Math.round(cy + r * 0.55f + o), shade);
            } else {
                g.fill(Math.round(cx + r - 1), Math.round(cy - r + o), Math.round(cx + r + o), Math.round(cy + r + o), shade);
            }
        }
    }

    private void renderBookmark(GuiGraphics g, float cx, float cy, float r, int number) {
        int x0 = Math.round(cx + r * 0.42f), x1 = x0 + 12;
        int y0 = Math.round(cy - r * 0.92f) - 6, y1 = y0 + 34;
        int ribbon = 0xFF8E2A3C, dark = 0xFF5E1A28;
        g.fill(x0, y0, x1, y1, ribbon);
        g.fill(x0, y0, x0 + 1, y1, dark);

        for (int k = 0; k < 4; k++) {
            g.fill(x0, y1 + k, x0 + 5 - k, y1 + k + 1, ribbon);
            g.fill(x1 - 5 + k, y1 + k, x1, y1 + k + 1, ribbon);
        }
        String n = Integer.toString(number);
        g.drawString(font, n, x0 + 6 - font.width(n) / 2, y1 - 11, 0xFFF4E2B0, false);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (kind() == PageKind.WITCH_GRIMOIRE && !contents && titleBox != null) {
            if (titleBox.isMouseOver(x, y) || notesBox.isMouseOver(x, y)) return super.mouseClicked(x, y, button);
            setFocused(null);
            titleBox.setFocused(false);
            notesBox.setFocused(false);
        }
        if (button == 0 && kind() != null && kind().book()) {
            if (x >= tabRect[0] && x < tabRect[2] && y >= tabRect[1] && y < tabRect[3]) {
                contents = !contents;
                Minecraft.getInstance().player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6f, 1.4f);
                return true;
            }
            if (contents) {
                for (int[] row : contentRows) {
                    if (x >= row[0] && x < row[2] && y >= row[1] && y < row[3]) {
                        contents = false;
                        if (row[4] != page) turn(row[4] - page);
                        return true;
                    }
                }
            }
        }
        if (button == 0 && kind() != null && kind().readOnly()) return true;
        if (button == 0) {
            drawing = true;
            count = 0;
            laid = false;
            imagining = false;
            addPoint(x, y);
        } else if (button == 1 && kind() != null && kind().erasable) {
            erasing = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!drawing && getFocused() != null) return super.mouseDragged(x, y, button, dx, dy);
        if (button == 0 && drawing) addPoint(x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && drawing) {
            drawing = false;
            send();
        }
        if (button == 1) erasing = false;
        if (!drawing) super.mouseReleased(x, y, button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (sy != 0) turn(sy > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (writing()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
                titleBox.setFocused(false);
                notesBox.setFocused(false);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_T && kind() == PageKind.PALM_QUIRE && pageCount() > 0) {
            PacketDistributor.sendToServer(new Payloads.PageTear(hand, page));
            page = Math.max(0, Math.min(page, pageCount() - 2));
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) { turn(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT) { turn(1); return true; }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        if (drawing) send();
        flushNotes();
        setCursorHidden(false);
    }

    private void renderPenCursor(GuiGraphics g, int mouseX, int mouseY) {
        var player = Minecraft.getInstance().player;
        ItemStack pen = ClientDrawing.heldPen(player);
        if (pen.isEmpty()) return;
        float scale = 2.2f;
        float press = drawing ? 1.5f : 0f;
        g.pose().pushPose();
        g.pose().translate(mouseX - 0.5f * scale + press, mouseY - 15.5f * scale + press, 300);
        g.pose().scale(scale, scale, 1);
        g.renderItem(pen, 0, 0);
        g.pose().popPose();
    }

    @Override
    protected void init() {
        super.init();

        if (kind() == PageKind.GRIMOIRE) {
            PacketDistributor.sendToServer(new Payloads.PageTurn(hand, page));
        }
        if (kind() == PageKind.WITCH_GRIMOIRE) makeNoteBoxes();
        setCursorHidden(!ClientDrawing.heldPen(Minecraft.getInstance().player).isEmpty());
    }

    private static void setCursorHidden(boolean hidden) {
        long win = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
    }

    private long flipStart;
    private int flipDir;

    public void debugTurn(int d) {
        turn(d);
    }

    private void turn(int d) {
        int n = pageCount();
        if (n <= 1) return;
        flushNotes();
        page = Math.floorMod(page + d, n);
        flipStart = Util.getMillis();
        flipDir = d;
        var pl = Minecraft.getInstance().player;
        if (pl != null) pl.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5f, 0.9f + pl.getRandom().nextFloat() * 0.25f);
        PacketDistributor.sendToServer(new Payloads.PageTurn(hand, page));
        loadNotes();
    }

    private InkNotesField titleBox;
    private InkNotesField notesBox;

    private int notesPage = -1;
    private String storedTitle = "", shownTitle = "";
    private boolean notesDirty;

    private boolean writing() {
        return titleBox != null && (titleBox.isFocused() || notesBox.isFocused());
    }

    private static String shown(String stored) {
        return stored.startsWith("#") ? I18n.get(stored.substring(1)) : stored;
    }

    private static List<String> list(ItemStack st, DataComponentType<List<String>> type) {
        List<String> l = st.get(type);
        return l == null ? List.of() : l;
    }

    private void makeNoteBoxes() {
        boolean keep = titleBox != null && notesPage == page;
        String keepTitle = keep ? titleBox.getValue() : null, keepNotes = keep ? notesBox.getValue() : null;
        String keepStored = storedTitle, keepShown = shownTitle;
        flushNotes();
        float r = Math.min(radius(), width / 4.5f), lcx = width / 2f - r, cy = height / 2f;
        int x0 = Math.round(lcx - r * 0.8f), w = Math.round(r * 1.6f), y = Math.round(cy - r * 0.8f);
        titleBox = new InkNotesField(font, x0, y + 2, w, font.lineHeight + 2, Component.translatable("screen.witchsatelier.grimoire.title_hint")).singleLine(0xFF4A2414);
        titleBox.setCharacterLimit(PageService.TITLE_MAX);
        titleBox.setValueListener(t -> notesDirty = true);
        int ny = y + 24;
        notesBox = new InkNotesField(font, x0, ny, w, Math.round(cy + r * 0.82f) - ny, Component.translatable("screen.witchsatelier.grimoire.notes_hint"));
        notesBox.setCharacterLimit(PageService.NOTES_MAX);
        notesBox.setValueListener(t -> notesDirty = true);
        addWidget(titleBox);
        addWidget(notesBox);
        if (keep) {
            titleBox.setValue(keepTitle);
            notesBox.setValue(keepNotes);
            storedTitle = keepStored;
            shownTitle = keepShown;
            notesPage = page;
            notesDirty = false;
            return;
        }
        notesPage = -1;
        loadNotes();
    }

    private void loadNotes() {
        if (titleBox == null || notesPage == page) return;
        ItemStack st = stack();
        List<String> titles = list(st, ModComponents.PAGE_TITLES.get()), notes = list(st, ModComponents.PAGE_NOTES.get());
        storedTitle = page < titles.size() ? titles.get(page) : "";
        shownTitle = shown(storedTitle);
        titleBox.setValue(shownTitle);
        notesBox.setValue(page < notes.size() ? notes.get(page) : "");
        notesPage = page;
        notesDirty = false;
    }

    private void flushNotes() {
        if (titleBox == null || !notesDirty || notesPage < 0) return;
        String t = titleBox.getValue();

        String title = t.equals(shownTitle) ? storedTitle : t;
        PacketDistributor.sendToServer(new Payloads.PageNote(hand, notesPage, title, notesBox.getValue()));
        notesDirty = false;
    }

    private void renderNotesPage(GuiGraphics g, float cx, float cy, float r) {
        if (titleBox == null) return;
        loadNotes();
        if (contents) {
            ItemStack st = stack();
            List<String> titles = list(st, ModComponents.PAGE_TITLES.get());
            GlyphPages gp = st.get(ModComponents.GLYPH_PAGES.get());
            List<Component> names = new ArrayList<>();
            for (int i = 0; i < pageCount(); i++) {
                String t = i < titles.size() ? titles.get(i) : "";
                boolean drawn = gp != null && !gp.page(i).isEmpty();
                names.add(!t.isEmpty() ? Component.literal(shown(t)) : drawn ? Component.translatable("screen.witchsatelier.grimoire.untitled", i + 1) : null);
            }
            renderContents(g, cx, cy, r, names);
            return;
        }
        int x0 = Math.round(cx - r * 0.8f), w = Math.round(r * 1.6f);
        titleBox.render(g, mouseX, mouseY, 0);
        g.fill(x0, titleBox.getY() + 13, x0 + w, titleBox.getY() + 14, 0x553A2A1A);
        notesBox.render(g, mouseX, mouseY, 0);
    }

    private void addPoint(double x, double y) {
        if (laid) return;
        float[] p = toPage(x, y);
        if (!onPage(p)) {
            if (imagining) return;
            if (count > 0) send();
            return;
        }
        if (count > 0) {
            float du = p[0] - cur[(count - 1) * 2], dv = p[1] - cur[(count - 1) * 2 + 1];
            if (du * du + dv * dv < 0.004f * 0.004f) return;
        }
        if (imagining && count >= 4000) return;
        if (count * 2 + 2 > cur.length) cur = Arrays.copyOf(cur, cur.length * 2);
        cur[count * 2] = p[0];
        cur[count * 2 + 1] = p[1];
        count++;
        if (!imagining && ClientDrawing.imaginedRing && count % 4 == 0) {
            double[] xy = new double[count * 2];
            for (int i = 0; i < xy.length; i++) xy[i] = cur[i];
            imagining = ImaginedRing.noticed(xy, count, 0.12, 1.3);
        }
        if (imagining && count % 3 == 0) {
            double[] xy = new double[count * 2];
            for (int i = 0; i < xy.length; i++) xy[i] = cur[i];
            if (ImaginedRing.circledTwice(xy, count)) layImaginedRing();
        }

        if (Util.getMillis() - lastScratch > 140) {
            lastScratch = Util.getMillis();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.QUILL_SCRATCH.get(), 0.9f + (float) Math.random() * 0.2f, 0.35f));
        }
        if (count >= 2000 && !imagining) {
            float lu = cur[(count - 1) * 2], lv = cur[(count - 1) * 2 + 1];
            send();
            cur[0] = lu;
            cur[1] = lv;
            count = 1;
        }
    }

    private void send() {
        if (count > 0) {
            var player = Minecraft.getInstance().player;
            if (ClientDrawing.heldPen(player).isEmpty()) {
                player.displayClientMessage(Component.translatable("screen.witchsatelier.page.no_pen"), true);
            } else if (ClientDrawing.inkAvailable(player) <= 0) {
                player.displayClientMessage(Component.translatable("message.witchsatelier.pen_dry"), true);
            } else {
                PacketDistributor.sendToServer(new Payloads.PageStroke(hand, page, Arrays.copyOf(cur, count * 2)));
            }
        }
        count = 0;
        imagining = false;
    }

    private void layImaginedRing() {
        var player = Minecraft.getInstance().player;
        double[] xy = new double[count * 2];
        for (int i = 0; i < xy.length; i++) xy[i] = cur[i];
        double[] ring = ImaginedRing.trueRing(xy, count, 0.12, 1.3, 0.07, 0.02);
        count = 0;
        imagining = false;
        laid = true;
        if (ring == null || ClientDrawing.heldPen(player).isEmpty() || ClientDrawing.inkAvailable(player) <= 0) return;
        float[] pts = new float[ring.length];
        for (int i = 0; i < ring.length; i++) pts[i] = (float) ring[i];
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4f, 1.7f);
        PacketDistributor.sendToServer(new Payloads.PageStroke(hand, page, pts));
    }

    private boolean laid;

    private boolean imagining;

    private static void renderGuide(VertexConsumer vc, Matrix4f m, GlyphLibrary.Recipe guide, float cx, float cy, float r, boolean soles) {
        float w = (float) (PageService.PAGE_INK * r);
        double t = Util.getMillis() / 1000.0;
        int a = (int) (60 + 30 * Math.sin(t * 2.2));
        for (var s : GlyphLibrary.fresh(guide).strokes()) {
            for (float[] part : soles ? PageKind.splitForSoles(s.pts(), 0.82f) : List.of(s.pts())) {
                line(vc, m, part, part.length / 2, cx, cy, r, w * 3.2f, (a / 4) << 24 | 0xE8C46A);
                line(vc, m, part, part.length / 2, cx, cy, r, w * 0.9f, a << 24 | 0xB8862E);
            }
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, float[] pts, int n, float cx, float cy, float r, float w, int argb) {
        float hw = Math.max(1f, w / 2);

        for (int i = 0; i < n; i++) disc(vc, m, cx + pts[i * 2] * r, cy - pts[i * 2 + 1] * r, hw, argb, 10);
        if (n == 1) return;
        for (int i = 1; i < n; i++) {
            float x0 = cx + pts[(i - 1) * 2] * r, y0 = cy - pts[(i - 1) * 2 + 1] * r;
            float x1 = cx + pts[i * 2] * r, y1 = cy - pts[i * 2 + 1] * r;
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-3f) continue;
            dx /= len; dy /= len;
            x0 -= dx * hw; y0 -= dy * hw; x1 += dx * hw; y1 += dy * hw;
            float nx = -dy * hw, ny = dx * hw;

            quad(vc, m, x0 - nx, y0 - ny, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, argb);
        }
    }

    private static void disc(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, int argb) {
        disc(vc, m, cx, cy, r, argb, 64);
    }

    private static void disc(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, int argb, int seg) {
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            quad(vc, m, cx, cy, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r,
                    cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx, cy, argb);
        }
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        quad(vc, m, x0, y0, x0, y1, x1, y1, x1, y0, argb);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy,
                             float dx, float dy, int argb) {
        vc.addVertex(m, ax, ay, 0).setColor(argb);
        vc.addVertex(m, bx, by, 0).setColor(argb);
        vc.addVertex(m, cx, cy, 0).setColor(argb);
        vc.addVertex(m, dx, dy, 0).setColor(argb);
    }
}
