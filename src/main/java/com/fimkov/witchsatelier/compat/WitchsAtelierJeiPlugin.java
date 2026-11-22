package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class WitchsAtelierJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "jei");
    }

    public static IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(IJeiRuntime rt) {
        runtime = rt;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        reg.addRecipeCategories(new AwakeningJeiCategory(reg.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        for (ItemInfo.Entry e : ItemInfo.entries()) reg.addItemStackInfo(new ItemStack(e.item()), e.lines());
        reg.addRecipes(AwakeningJeiCategory.TYPE, Awakening.ALL);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(ModItems.INK_PEN.get()), AwakeningJeiCategory.TYPE);
    }
}
