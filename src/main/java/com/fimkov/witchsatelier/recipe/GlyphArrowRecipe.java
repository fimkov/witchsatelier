package com.fimkov.witchsatelier.recipe;

import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

public class GlyphArrowRecipe extends CustomRecipe {
    public GlyphArrowRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        GlyphPages.Page page = find(input);
        if (page == null) return ItemStack.EMPTY;
        ItemStack arrow = new ItemStack(ModItems.GLYPH_ARROW.get());
        arrow.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(page), 0));
        return arrow;
    }

    private static GlyphPages.Page find(CraftingInput input) {
        int arrows = 0;
        GlyphPages.Page page = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack st = input.getItem(i);
            if (st.isEmpty()) continue;
            if (st.is(Items.ARROW)) { arrows++; continue; }
            PageKind k = PageKind.of(st);
            GlyphPages gp = st.get(ModComponents.GLYPH_PAGES.get());
            if ((k == PageKind.PAPER || k == PageKind.TORN_PAGE) && gp != null && !gp.page(0).isEmpty() && page == null) { page = gp.page(0); continue; }
            return null;
        }
        return arrows == 1 ? page : null;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GLYPH_ARROW.get();
    }
}
