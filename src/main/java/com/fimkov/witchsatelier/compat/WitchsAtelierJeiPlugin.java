package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.WitchsAtelier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Only loaded when JEI is installed: an information page for every item whose use is not a crafting recipe
 * (inks, pens, the quire and the old books, the artifacts), built from the descriptions the items already carry.
 */
@JeiPlugin
public class WitchsAtelierJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "jei");
    }

    /** The running JEI, for the screenshot harness to open a page with. */
    public static mezz.jei.api.runtime.IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime rt) {
        runtime = rt;
    }

    @Override
    public void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration reg) {
        reg.addRecipeCategories(new AwakeningJeiCategory(reg.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        for (ItemInfo.Entry e : ItemInfo.entries()) reg.addItemStackInfo(new ItemStack(e.item()), e.lines());
        reg.addRecipes(AwakeningJeiCategory.TYPE, com.fimkov.witchsatelier.item.artifact.Awakening.ALL);
    }

    @Override
    public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(com.fimkov.witchsatelier.registry.ModItems.INK_PEN.get()), AwakeningJeiCategory.TYPE);
    }
}
