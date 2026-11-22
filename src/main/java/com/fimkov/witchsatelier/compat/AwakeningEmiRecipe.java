package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.client.GlyphIcon;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.registry.ModItems;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** EMI: a dormant artifact, the glyph to draw on it, and the artifact awake. */
public class AwakeningEmiRecipe implements EmiRecipe {
    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "awakening"), EmiStack.of(ModItems.INK_PEN.get()));
    static final int GLYPH = 54;

    private final Awakening.Entry entry;
    private final EmiStack dormant, awake;

    public AwakeningEmiRecipe(Awakening.Entry entry) {
        this.entry = entry;
        this.dormant = EmiStack.of(Awakening.dormantStack(entry));
        this.awake = EmiStack.of(entry.artifact().get());
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "/awakening/" + entry.glyph().name());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(dormant);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(awake);
    }

    @Override
    public int getDisplayWidth() {
        return 146;
    }

    @Override
    public int getDisplayHeight() {
        return GLYPH + 4;
    }

    @Override
    public void addWidgets(WidgetHolder w) {
        int mid = (GLYPH + 4) / 2 - 9;
        w.addSlot(dormant, 0, mid);
        w.addTexture(EmiTexture.EMPTY_ARROW, 20, mid + 1);
        int gx = 46;
        w.addDrawable(gx, 2, GLYPH, GLYPH, (g, mx, my, delta) -> {
            g.fill(0, 0, GLYPH, GLYPH, 0xFFEFE4CC);
            GlyphIcon.draw(g, entry.glyph(), 1, 1, GLYPH - 2, 0xFF2A1A14);
        });
        w.addTooltipText(List.of(Component.translatable("jei.witchsatelier.awakening.how")), gx, 2, GLYPH, GLYPH);
        w.addTexture(EmiTexture.EMPTY_ARROW, gx + GLYPH + 2, mid + 1);
        w.addSlot(awake, 128, mid).recipeContext(this);
    }
}
