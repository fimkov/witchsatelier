package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.client.GlyphIcon;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** JEI: a dormant artifact, the glyph to draw on it, and the artifact awake. */
public class AwakeningJeiCategory implements IRecipeCategory<Awakening.Entry> {
    public static final RecipeType<Awakening.Entry> TYPE = RecipeType.create(WitchsAtelier.MODID, "awakening", Awakening.Entry.class);
    private static final int GLYPH = 54, GX = 46;

    private final IDrawable icon, arrow;

    public AwakeningJeiCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(ModItems.INK_PEN.get()));
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<Awakening.Entry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("emi.category.witchsatelier.awakening");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 146;
    }

    @Override
    public int getHeight() {
        return GLYPH + 4;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Awakening.Entry e, IFocusGroup focuses) {
        int mid = (GLYPH + 4) / 2 - 8;
        b.addSlot(RecipeIngredientRole.INPUT, 1, mid).addItemStack(Awakening.dormantStack(e));
        b.addSlot(RecipeIngredientRole.OUTPUT, 129, mid).addItemStack(new ItemStack(e.artifact().get()));
    }

    @Override
    public void draw(Awakening.Entry e, IRecipeSlotsView slots, GuiGraphics g, double mx, double my) {
        int mid = (GLYPH + 4) / 2;
        arrow.draw(g, 20, mid - 8);
        arrow.draw(g, GX + GLYPH + 2, mid - 8);
        g.fill(GX, 2, GX + GLYPH, 2 + GLYPH, 0xFFEFE4CC);
        GlyphIcon.draw(g, e.glyph(), GX + 1, 3, GLYPH - 2, 0xFF2A1A14);
    }

    @Override
    public void getTooltip(ITooltipBuilder tip, Awakening.Entry e, IRecipeSlotsView slots, double mx, double my) {
        if (mx >= GX && mx < GX + GLYPH && my >= 2 && my < 2 + GLYPH) tip.add(Component.translatable("jei.witchsatelier.awakening.how"));
    }
}
