package com.fimkov.witchsatelier.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class InkNotesField extends AbstractWidget {
    private static final int INK = 0xFF3B2A1E, FAINT = 0x803B2A1E, SELECT = 0x50406A90;
    private final Font font;
    private final MultilineTextField text;
    private final Component hint;

    private boolean singleLine;
    private int ink = INK;

    public InkNotesField(Font font, int x, int y, int w, int h, Component hint) {
        super(x, y, w, h, Component.empty());
        this.font = font;
        this.hint = hint;
        this.text = new MultilineTextField(font, w);
    }

    public InkNotesField singleLine(int color) {
        singleLine = true;
        ink = color;
        return this;
    }

    public void setValue(String s) {
        text.setValue(s);
    }

    public String getValue() {
        return text.value();
    }

    public void setCharacterLimit(int n) {
        text.setCharacterLimit(n);
    }

    public void setValueListener(Consumer<String> l) {
        text.setValueListener(l);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        String v = text.value();
        int x = getX(), y = getY();
        if (v.isEmpty() && !isFocused()) {
            g.drawString(font, hint, x, y, FAINT, false);
            return;
        }
        boolean blink = isFocused() && (Util.getMillis() / 400) % 2 == 0;
        int cursor = text.cursor();

        int s0 = cursor, s1 = cursor;
        if (text.hasSelection()) {
            String picked = text.getSelectedText();
            if (v.startsWith(picked, cursor)) s1 = cursor + picked.length();
            else s0 = Math.max(0, cursor - picked.length());
        }

        List<int[]> lines = new ArrayList<>();
        font.getSplitter().splitLines(v, width, Style.EMPTY, false, (style, b, e) -> lines.add(new int[]{b, e}));
        if (lines.isEmpty() || v.endsWith("\n")) lines.add(new int[]{v.length(), v.length()});
        boolean cursorShown = false;
        for (int[] line : lines) {
            if (y + font.lineHeight > getY() + height) break;
            int b = line[0], e = line[1];
            int a0 = Math.max(b, s0), a1 = Math.min(e, s1);
            if (a0 < a1) g.fill(x + font.width(v.substring(b, a0)), y - 1, x + font.width(v.substring(b, a1)), y + font.lineHeight - 1, SELECT);
            g.drawString(font, v.substring(b, e), x, y, ink, false);
            if (blink && !cursorShown && cursor >= b && cursor <= e) {
                int cx = x + font.width(v.substring(b, cursor));
                g.fill(cx, y - 1, cx + 1, y + font.lineHeight - 1, INK);
                cursorShown = true;
            }
            y += font.lineHeight;
        }
        if (text.hasCharacterLimit() && !singleLine) {
            String count = v.length() + "/" + text.characterLimit();
            g.drawString(font, count, getX() + width - font.width(count), getY() + height - font.lineHeight, FAINT, false);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!isFocused()) return false;
        if (singleLine && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) return true;
        text.setSelecting(Screen.hasShiftDown());
        return text.keyPressed(key);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!isFocused() || !StringUtil.isAllowedChatCharacter(c)) return false;
        text.insertText(Character.toString(c));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMouseOver(mx, my)) return false;
        setFocused(true);
        text.setSelecting(Screen.hasShiftDown());
        text.seekCursorToPoint(mx - getX(), my - getY());
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!isFocused()) return false;
        text.setSelecting(true);
        text.seekCursorToPoint(mx - getX(), my - getY());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, hint);
    }
}
