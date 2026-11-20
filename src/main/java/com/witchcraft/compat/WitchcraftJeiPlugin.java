package com.witchcraft.compat;

import com.witchcraft.WitchCraft;
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
public class WitchcraftJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        for (ItemInfo.Entry e : ItemInfo.entries()) reg.addItemStackInfo(new ItemStack(e.item()), e.lines());
    }
}
