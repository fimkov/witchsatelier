package com.witchcraft.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.witchcraft.glyph.page.GlyphPages;
import com.witchcraft.glyph.page.PageKind;
import com.witchcraft.glyph.page.PageService;
import com.witchcraft.network.Payloads;
import com.witchcraft.registry.ModComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Drawing on a page held in the hand. Left button draws, right button rubs out, the wheel or arrow keys turn
 * pages. A glyph whose ring closes here is cast straight away from the hand.
 */
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
        super(Component.translatable("screen.witchcraft.page"));
        this.hand = hand;
        ItemStack st = stack();
        GlyphPages gp = st.get(ModComponents.GLYPH_PAGES.get());
        this.page = gp == null ? 0 : gp.current();
    }

    /** Opened at a given page (the screenshot harness uses this). */
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
        float r = radius();
        return new float[]{(float) ((mx - width / 2.0) / r), (float) (-(my - height / 2.0) / r)};
    }

    private boolean onPage(float[] p) {
        PageKind k = kind();
        if (k == null || pageCount() == 0) return false;
        if (k == PageKind.BOOTS) return onSole(p[0], p[1]) || (Math.abs(p[0]) < 0.08f && Math.abs(p[1]) < 0.9f);
        return k.round ? p[0] * p[0] + p[1] * p[1] <= 1.0f : Math.abs(p[0]) <= 1 && Math.abs(p[1]) <= 1;
    }

    /**
     * Half-width of a sole at height {@code y} (page units, toes up): rounded toe, narrow waist, round heel.
     * The left sole spans x in [-1, 0], the right one [0, 1]; their inner edges meet at the seam in the middle,
     * the way the two soles line up when the heels are clicked together.
     */
    private static float soleHalfWidth(float y) {
        if (y > 0.98f || y < -0.98f) return 0;
        double t = (y + 0.98) / 1.96; // 0 heel .. 1 toe
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

    /** Two soles side by side, leather with a darker welt and the heel blocks, and a faint seam where they join. */
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
        // seam where the soles meet when the heels click together
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
        if (k == PageKind.GRIMOIRE) {
            // an open book: leather boards behind, the explanation on the left page, the glyph on the right
            r = Math.min(r, width / 4.5f);
            cx = width / 2f + r;
            renderBookBody(g, width / 2f, cy, r);
            renderExplanation(g, width / 2f - r, cy, r);
        }
        // the page itself: parchment with fibres and a darker, worn rim
        String tex = k.round ? "page_round" : k == PageKind.BOOK || k == PageKind.GRIMOIRE ? "page_book" : "page_square";
        int size = Math.round(r * 2);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        if (k == PageKind.BOOTS) {
            renderSoles(g, cx, cy, r);
        } else {
            g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.witchcraft.WitchCraft.MODID, "textures/gui/" + tex + ".png"),
                    Math.round(cx - r), Math.round(cy - r), size, size, 0, 0, 256, 256, 256, 256);
        }
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        if (k == PageKind.GRIMOIRE && isBasics()) renderAnatomy(g, vc, m, cx, cy, r);
        if (k == PageKind.GRIMOIRE && isEntry(com.witchcraft.glyph.page.GlyphLibrary.IMAGINED_RING)) renderImaginedDiagram(g, vc, m, cx, cy, r);

        float w = (float) (PageService.PAGE_INK * r);
        GlyphPages gp = stack().get(ModComponents.GLYPH_PAGES.get());
        if (gp != null) {
            for (GlyphPages.PageStroke s : gp.page(page).strokes()) {
                line(vc, m, s.pts(), s.pts().length / 2, cx, cy, r, w, s.ink().invisible ? 0x70503A90 : s.ink().color | 0xFF000000);
            }
        }
        if (count > 0 && imagining) {
            // only imagined yet: pale light, lifted a little off the paper (and its shadow under it)
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
        renderPenCursor(g, mouseX, mouseY);

        if (k == PageKind.GRIMOIRE) renderGutter(g, width / 2f, cy, r);
        int count = pageCount();
        if (count > 1) {
            if (k != PageKind.GRIMOIRE) renderPageEdges(g, cx, cy, r, count, k.round);
            renderBookmark(g, k == PageKind.GRIMOIRE ? cx - r * 1.6f : cx, cy, r, page + 1); // a grimoire's hangs by the spine, clear of text and labels
        }
        renderFlip(g, k, cx, cy, r);
    }

    private static final long FLIP_MS = 420;

    /**
     * The leaf being turned: it lifts off one side, curls as it swings over the spine and settles on the other,
     * forward from right to left and back from left to right, its shadow passing over the pages beneath. A loose
     * page or quire turns the same way about its left edge.
     */
    private void renderFlip(GuiGraphics g, PageKind k, float cx, float cy, float r) {
        long dt = Util.getMillis() - flipStart;
        if (dt > FLIP_MS || k == PageKind.BOOTS || flipDir == 0) return;
        float t = dt / (float) FLIP_MS;
        float e = t * t * (3 - 2 * t); // ease in and out
        // angle of the leaf about the spine: 0 lying on the right, pi lying on the left
        double angle = flipDir > 0 ? e * Math.PI : (1 - e) * Math.PI;
        float spine = k == PageKind.GRIMOIRE ? width / 2f : cx - r;
        float size = r * 2;
        String tex = k.round ? "page_round" : k == PageKind.BOOK || k == PageKind.GRIMOIRE ? "page_book" : "page_square";
        var loc = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.witchcraft.WitchCraft.MODID, "textures/gui/" + tex + ".png");

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        // the shadow it throws on the side it is coming down onto
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
        double curl = 0.55 * lift; // the free edge lags behind, so the leaf bends
        for (int i = 0; i < strips; i++) {
            double s0 = (double) i / strips, s1 = (double) (i + 1) / strips;
            double a = angle - curl * (s0 + s1) / 2 * (flipDir > 0 ? 1 : -1);
            double nx = x + Math.cos(a) * size / strips;
            float h = (float) (r * (1 + 0.07 * Math.sin(a) * s1)); // the raised edge comes nearer and looks taller
            int xa = (int) Math.round(Math.min(x, nx)), xb = (int) Math.round(Math.max(x, nx));
            if (xb > xa) {
                // lit face up, darker as it stands on edge, darkest in the curl
                float shade = (float) (0.62 + 0.38 * Math.abs(Math.cos(a)));
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(shade, shade, shade * 0.96f, 1);
                boolean back = Math.cos(a) < 0;
                int u = (int) Math.round((back ? 1 - s1 : s0) * 256);
                int uw = Math.max(1, (int) Math.round(256.0 / strips));
                g.blit(loc, xa, Math.round(cy - h), xb - xa, Math.round(h * 2), u, 0, uw, 256, 256, 256);
            }
            x = nx;
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        g.pose().popPose();
    }

    private boolean isBasics() {
        return isEntry(com.witchcraft.glyph.page.GlyphLibrary.BASICS);
    }

    private boolean isEntry(String name) {
        java.util.List<String> names = stack().get(ModComponents.GRIMOIRE_ENTRIES.get());
        return names != null && page < names.size() && name.equals(names.get(page));
    }

    /**
     * The technique page's picture: the imagined ring, faint and lifted a little off the page, and under it the
     * ring laid down true with its gap.
     */
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
        // the two laps in the air: dashes of pale violet light, a little above the page
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
        label(g, cx, cy, r, -rr * 0.74f, -rr * 0.74f + lift * 1.5f, -0.84f, -0.8f, "glyph.witchcraft.part.imagined", ink);
        label(g, cx, cy, r, (float) Math.cos(a0 - gap / 2) * rr, (float) Math.sin(a0 - gap / 2) * rr - 0.04f, 0.84f, 0.8f, "glyph.witchcraft.part.gap", ink);
        label(g, cx, cy, r, rr * 0.71f, -rr * 0.71f - 0.04f, 0.84f, -0.8f, "glyph.witchcraft.part.inked", ink);
    }

    private static GlyphPages.Page anatomy;

    /** The opening page's diagram: one glyph, its parts named with little leader lines. */
    private void renderAnatomy(GuiGraphics g, VertexConsumer vc, Matrix4f m, float cx, float cy, float r) {
        if (anatomy == null) anatomy = com.witchcraft.glyph.page.GlyphLibrary.draw(com.witchcraft.glyph.page.GlyphLibrary.byName("pillar_of_flame"),
                net.minecraft.util.RandomSource.create(3), true, com.witchcraft.glyph.ink.InkType.CONJURING);
        float w = (float) (PageService.PAGE_INK * r) * 0.8f;
        float s = 0.62f; // the diagram sits a little smaller, leaving room for the labels
        for (GlyphPages.PageStroke st : anatomy.strokes()) {
            float[] p = st.pts().clone();
            for (int i = 0; i < p.length; i++) p[i] *= s;
            line(vc, m, p, p.length / 2, cx, cy, r, w, 0xFF2A1E3A);
        }
        g.flush();
        int ink = 0xFF5A3A2A;
        // the words go in the page's corners, clear of the drawing, each joined to its part by a dotted line
        label(g, cx, cy, r, -0.85f * s * 0.71f, 0.85f * s * 0.71f, -0.84f, 0.8f, "glyph.witchcraft.part.ring", ink);
        label(g, cx, cy, r, 0.0f, -0.12f * s, -0.84f, -0.8f, "glyph.witchcraft.part.sigil", ink);
        label(g, cx, cy, r, 0.53f * s * 0.71f, -0.53f * s * 0.71f, 0.84f, -0.8f, "glyph.witchcraft.part.sign", ink);
        // the gap is between the two ends of the ring line
        float[] ring = anatomy.strokes().get(0).pts();
        float gx = ring[0] + ring[ring.length - 2], gy = ring[1] + ring[ring.length - 1];
        float gl = (float) Math.hypot(gx, gy);
        if (gl > 1e-3) {
            gx = gx / gl * 0.85f * s;
            gy = gy / gl * 0.85f * s;
            label(g, cx, cy, r, gx, gy, gx < 0 ? -0.84f : 0.84f, gy < -0.2f ? -0.55f : 0.8f, "glyph.witchcraft.part.gap", ink);
        }
    }

    /**
     * A leader line from a point of the drawing (page units) to a word written beside it. The word hangs off the
     * line's end on the side away from the drawing (left of it on the left, right of it on the right; above it in
     * the top half, below in the bottom half) and is always kept inside the page.
     */
    private void label(GuiGraphics g, float cx, float cy, float r, float px, float py, float tx, float ty, String key, int color) {
        net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.translatable(key);
        int tw = font.width(text);
        int left = Math.round(cx - r * 0.9f), right = Math.round(cx + r * 0.9f);
        int ax = Math.round(cx + px * r), ay = Math.round(cy - py * r), bx = Math.round(cx + tx * r), by = Math.round(cy - ty * r);
        int textX = tx < 0 ? bx : bx - tw;
        textX = Math.max(left, Math.min(right - tw, textX));
        int textY = ty > 0 ? by - font.lineHeight - 1 : by + 2;
        // the line ends at the corner of the word nearest the drawing
        bx = tx < 0 ? textX : textX + tw;
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (int i = 0; i <= steps; i += 2) {
            int x = ax + (bx - ax) * i / Math.max(1, steps), y = ay + (by - ay) * i / Math.max(1, steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
        g.fill(textX, by, textX + tw, by + 1, (color & 0x00FFFFFF) | 0x50000000);
        g.drawString(font, text, textX, textY, color, false);
    }

    private static final net.minecraft.resources.ResourceLocation COVER =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.witchcraft.WitchCraft.MODID, "textures/gui/grimoire_cover.png");
    private static final net.minecraft.resources.ResourceLocation BOOK_PAGE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.witchcraft.WitchCraft.MODID, "textures/gui/page_book.png");

    /**
     * The grimoire lying open: leather boards a little bigger than the pages, and under each page the edges of the
     * pages already read (left) and still to come (right), thicker the more there are.
     */
    private void renderBookBody(GuiGraphics g, float spine, float cy, float r) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        float pad = r * 0.075f;
        int x0 = Math.round(spine - 2 * r - pad), x1 = Math.round(spine + 2 * r + pad);
        int y0 = Math.round(cy - r - pad), y1 = Math.round(cy + r + pad * 1.4f);
        // a soft shadow under the book
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

    /** Where the pages bend into the spine they fall into shade. */
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

    /** The facing page of a grimoire: the glyph's name and what it does, in a careful hand. */
    private void renderExplanation(GuiGraphics g, float cx, float cy, float r) {
        int size = Math.round(r * 2);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        // the same sheet seen from its other side: the worn spine edge on the right
        // (mirrored through the texture coordinates, so the quad keeps its winding)
        g.blit(BOOK_PAGE, Math.round(cx - r), Math.round(cy - r), size, size, 256, 0, -256, 256, 256, 256);
        java.util.List<String> names = stack().get(ModComponents.GRIMOIRE_ENTRIES.get());
        renderContentsTab(g, cx, cy, r);
        if (contents && names != null) {
            renderContents(g, cx, cy, r, names);
            return;
        }
        String entry = names == null || page >= names.size() ? "" : names.get(page);
        if (entry.isEmpty()) return;
        int x0 = Math.round(cx - r * 0.8f), w = Math.round(r * 1.6f);
        int y = Math.round(cy - r * 0.8f);
        net.minecraft.network.chat.Component title = net.minecraft.network.chat.Component.translatable("glyph.witchcraft." + entry);
        g.pose().pushPose();
        g.pose().translate(x0, y, 0);
        g.pose().scale(1.35f, 1.35f, 1);
        g.drawString(font, title, 0, 0, 0xFF4A2414, false);
        g.pose().popPose();
        y += Math.round(font.lineHeight * 1.35f) + 6;
        g.fill(x0, y - 3, x0 + w, y - 2, 0x553A2A1A);
        for (var line : font.split(net.minecraft.network.chat.Component.translatable("glyph.witchcraft." + entry + ".desc"), w)) {
            if (y > cy + r * 0.86f) break;
            g.drawString(font, line, x0, y, 0xFF3B2A1E, false);
            y += font.lineHeight + 1;
        }
    }

    /** The contents list is open on the left page. */
    private boolean contents;
    /** Clickable rows of the contents: x0, y0, x1, y1, page. */
    private final java.util.List<int[]> contentRows = new java.util.ArrayList<>();
    private int[] tabRect = new int[4];
    private int mouseX, mouseY;

    /** A cloth tab sticking up from the top of the left page: click it for the contents. */
    private void renderContentsTab(GuiGraphics g, float cx, float cy, float r) {
        net.minecraft.network.chat.Component label = net.minecraft.network.chat.Component.translatable("screen.witchcraft.contents");
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

    /** Every glyph in the book, by name; click one to turn to it. */
    private void renderContents(GuiGraphics g, float cx, float cy, float r, java.util.List<String> names) {
        contentRows.clear();
        int x0 = Math.round(cx - r * 0.8f), x1 = Math.round(cx + r * 0.8f);
        int y = Math.round(cy - r * 0.8f);
        g.pose().pushPose();
        g.pose().translate(x0, y, 0);
        g.pose().scale(1.35f, 1.35f, 1);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("screen.witchcraft.contents"), 0, 0, 0xFF4A2414, false);
        g.pose().popPose();
        y += Math.round(font.lineHeight * 1.35f) + 6;
        g.fill(x0, y - 3, x1, y - 2, 0x553A2A1A);
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).isEmpty()) continue;
            if (y > cy + r * 0.86f) break;
            int rowH = font.lineHeight + 3;
            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y - 1 && mouseY < y - 1 + rowH;
            if (hover || i == page) g.fill(x0 - 2, y - 1, x1, y - 1 + rowH, hover ? 0x30A0602A : 0x18A0602A);
            String num = Integer.toString(i + 1);
            net.minecraft.network.chat.Component name = net.minecraft.network.chat.Component.translatable("glyph.witchcraft." + names.get(i));
            g.drawString(font, name, x0, y + 1, 0xFF3B2A1E, false);
            // a dotted leader to the page number, as in an old book
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

    /** The pages still to come, as thin edges peeking out beside the page (more pages, thicker stack). */
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

    /** A silk ribbon hanging from the top edge, with the page number stitched on it. */
    private void renderBookmark(GuiGraphics g, float cx, float cy, float r, int number) {
        int x0 = Math.round(cx + r * 0.42f), x1 = x0 + 12;
        int y0 = Math.round(cy - r * 0.92f) - 6, y1 = y0 + 34;
        int ribbon = 0xFF8E2A3C, dark = 0xFF5E1A28;
        g.fill(x0, y0, x1, y1, ribbon);
        g.fill(x0, y0, x0 + 1, y1, dark);
        // a swallow-tail notch at the end
        for (int k = 0; k < 4; k++) {
            g.fill(x0, y1 + k, x0 + 5 - k, y1 + k + 1, ribbon);
            g.fill(x1 - 5 + k, y1 + k, x1, y1 + k + 1, ribbon);
        }
        String n = Integer.toString(number);
        g.drawString(font, n, x0 + 6 - font.width(n) / 2, y1 - 11, 0xFFF4E2B0, false);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && kind() == PageKind.GRIMOIRE) {
            if (x >= tabRect[0] && x < tabRect[2] && y >= tabRect[1] && y < tabRect[3]) {
                contents = !contents;
                Minecraft.getInstance().player.playNotifySound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
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
        if (button == 0 && kind() != null && kind().readOnly()) return true; // an old book: read it, don't draw in it
        if (button == 0) {
            drawing = true;
            count = 0;
            laid = false;
            imagining = false;
            addPoint(x, y);
        } else if (button == 1 && kind() != null && kind().erasable) {
            erasing = true; // ink in a palm quire cannot be rubbed out
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
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
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (sy != 0) turn(sy > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_T && kind() == PageKind.PALM_QUIRE && pageCount() > 0) {
            // tear the page out: it falls crumpled at your feet
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
        setCursorHidden(false);
    }

    /** The pen itself follows the mouse, its nib exactly at the drawing point (the system cursor is hidden). */
    private void renderPenCursor(GuiGraphics g, int mouseX, int mouseY) {
        var player = Minecraft.getInstance().player;
        ItemStack pen = ClientDrawing.heldPen(player);
        if (pen.isEmpty()) return;
        float scale = 2.2f;
        float press = drawing ? 1.5f : 0f; // the pen dips a little onto the page while drawing
        g.pose().pushPose();
        g.pose().translate(mouseX - 0.5f * scale + press, mouseY - 15.5f * scale + press, 300);
        g.pose().scale(scale, scale, 1);
        g.renderItem(pen, 0, 0);
        g.pose().popPose();
    }

    @Override
    protected void init() {
        super.init();
        // a grimoire opened straight at a page counts as reading it
        if (kind() == PageKind.GRIMOIRE) {
            PacketDistributor.sendToServer(new Payloads.PageTurn(hand, page));
        }
        setCursorHidden(!ClientDrawing.heldPen(Minecraft.getInstance().player).isEmpty());
    }

    private static void setCursorHidden(boolean hidden) {
        long win = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
    }

    private long flipStart;
    private int flipDir;

    /** For the screenshot harness. */
    public void debugTurn(int d) {
        turn(d);
    }

    private void turn(int d) {
        int n = pageCount();
        if (n <= 1) return;
        page = Math.floorMod(page + d, n);
        flipStart = Util.getMillis();
        flipDir = d;
        var pl = Minecraft.getInstance().player;
        if (pl != null) pl.playNotifySound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.9f + pl.getRandom().nextFloat() * 0.25f);
        PacketDistributor.sendToServer(new Payloads.PageTurn(hand, page));
    }

    private void addPoint(double x, double y) {
        if (laid) return;
        float[] p = toPage(x, y);
        if (!onPage(p)) {
            if (imagining) return; // the imagined ring may swing past the page's edge
            if (count > 0) send();
            return;
        }
        if (count > 0) {
            float du = p[0] - cur[(count - 1) * 2], dv = p[1] - cur[(count - 1) * 2 + 1];
            if (du * du + dv * dv < 0.004f * 0.004f) return;
        }
        if (count * 2 + 2 > cur.length) cur = java.util.Arrays.copyOf(cur, cur.length * 2);
        cur[count * 2] = p[0];
        cur[count * 2 + 1] = p[1];
        count++;
        if (!imagining && ClientDrawing.imaginedRing && count % 4 == 0) {
            double[] xy = new double[count * 2];
            for (int i = 0; i < xy.length; i++) xy[i] = cur[i];
            imagining = com.witchcraft.glyph.recognize.ImaginedRing.noticed(xy, count, 0.12, 1.3);
        }
        if (imagining && count % 3 == 0) {
            double[] xy = new double[count * 2];
            for (int i = 0; i < xy.length; i++) xy[i] = cur[i];
            if (com.witchcraft.glyph.recognize.ImaginedRing.circledTwice(xy, count)) layImaginedRing();
        }
        // the faint scratch of a nib on paper
        if (Util.getMillis() - lastScratch > 140) {
            lastScratch = Util.getMillis();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    com.witchcraft.registry.ModSounds.QUILL_SCRATCH.get(), 0.9f + (float) Math.random() * 0.2f, 0.35f));
        }
        if (count >= 2000) {
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
                player.displayClientMessage(Component.translatable("screen.witchcraft.page.no_pen"), true);
            } else if (ClientDrawing.inkAvailable(player) <= 0) {
                player.displayClientMessage(Component.translatable("message.witchcraft.pen_dry"), true);
            } else {
                // (lifted before two laps in the air: what was imagined goes down as it was drawn)
                PacketDistributor.sendToServer(new Payloads.PageStroke(hand, page, java.util.Arrays.copyOf(cur, count * 2)));
            }
        }
        count = 0;
        imagining = false;
    }

    /** Twice round in the air over the page: the pen comes down and the true ring is laid in one stroke. */
    private void layImaginedRing() {
        var player = Minecraft.getInstance().player;
        double[] xy = new double[count * 2];
        for (int i = 0; i < xy.length; i++) xy[i] = cur[i];
        double[] ring = com.witchcraft.glyph.recognize.ImaginedRing.trueRing(xy, count, 0.12, 1.3, 0.07, 0.02);
        count = 0;
        imagining = false;
        laid = true;
        if (ring == null || ClientDrawing.heldPen(player).isEmpty() || ClientDrawing.inkAvailable(player) <= 0) return;
        float[] pts = new float[ring.length];
        for (int i = 0; i < ring.length; i++) pts[i] = (float) ring[i];
        player.playNotifySound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.7f);
        PacketDistributor.sendToServer(new Payloads.PageStroke(hand, page, pts));
    }

    /** This press already laid its ring down: the rest of it draws nothing. */
    private boolean laid;

    /** The line on the page has been taken for a ring being drawn (see ImaginedRing). */
    private boolean imagining;

    // ---------------------------------------------------------------- drawing helpers

    private static void line(VertexConsumer vc, Matrix4f m, float[] pts, int n, float cx, float cy, float r, float w, int argb) {
        float hw = Math.max(1f, w / 2);
        // round ink at every point so joints and ends look like a real nib
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
            // counter-clockwise on screen, or the GUI's back-face culling drops the quad
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
