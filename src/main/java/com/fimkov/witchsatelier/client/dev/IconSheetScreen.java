package com.fimkov.witchsatelier.client.dev;

import com.fimkov.witchsatelier.WitchsAtelier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class IconSheetScreen extends Screen {
    static final int CELL = 128, COLS = 14;

    private final int key;

    IconSheetScreen(int key) {
        super(Component.empty());
        this.key = key;
        WitchsAtelier.LOGGER.info("[visualcheck] icons {} {} {}", CELL, COLS, String.join(",", items().stream().map(i -> BuiltInRegistries.ITEM.getKey(i).getPath()).toList()));
    }

    static List<Item> items() {
        return BuiltInRegistries.ITEM.stream().filter(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(WitchsAtelier.MODID)).toList();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, key);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        double gui = minecraft.getWindow().getGuiScale();
        float cell = (float) (CELL / gui), k = cell / 16f;
        List<Item> items = items();
        for (int i = 0; i < items.size(); i++) {
            g.pose().pushPose();

            g.pose().translate((i % COLS) * cell + cell * 0.1f, (i / COLS) * cell + cell * 0.1f, 0);
            g.pose().scale(k * 0.8f, k * 0.8f, 1);
            g.renderItem(new ItemStack(items.get(i)), 0, 0);
            g.pose().popPose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
