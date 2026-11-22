package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.block.InkwellBlockEntity;
import com.fimkov.witchsatelier.glyph.ink.LoadedInk;
import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class InkwellItem extends BlockItem {
    public InkwellItem(Block block, Properties props) {
        super(block, props.stacksTo(1));
    }

    public static LoadedInk contents(ItemStack stack) {
        return stack.get(ModComponents.INKWELL_INK.get());
    }

    public static void setContents(ItemStack stack, LoadedInk ink) {
        if (ink == null || ink.amount() <= 0) stack.remove(ModComponents.INKWELL_INK.get());
        else stack.set(ModComponents.INKWELL_INK.get(), ink);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
        if (action != ClickAction.PRIMARY || !(other.getItem() instanceof InkBottleItem bottle)) return false;
        LoadedInk c = contents(self);
        if (c != null && c.amount() > 0 && c.type() != bottle.ink) return false;
        int now = c == null ? 0 : c.amount();
        if (now >= InkwellBlockEntity.CAPACITY) return false;
        setContents(self, new LoadedInk(bottle.ink, Math.min(InkwellBlockEntity.CAPACITY, now + InkBottleItem.UNITS)));
        other.shrink(1);
        ItemStack glass = new ItemStack(Items.GLASS_BOTTLE);
        if (!player.getInventory().add(glass)) player.drop(glass, false);
        player.playSound(SoundEvents.BOTTLE_EMPTY, 0.8f, 0.9f);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return contents(stack) != null;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        LoadedInk c = contents(stack);
        return c == null ? 0 : Math.round(13f * c.amount() / InkwellBlockEntity.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        LoadedInk c = contents(stack);
        return c == null ? 0 : InkPenItem.barColor(c.type());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        LoadedInk c = contents(stack);
        if (c == null) lines.add(Component.translatable("item.witchsatelier.inkwell.empty").withStyle(ChatFormatting.GRAY));
        else lines.add(Component.translatable("item.witchsatelier.inkwell.holds", Component.translatable("item.witchsatelier." + c.type().id + "_ink"),
                c.amount() / 4).withStyle(ChatFormatting.DARK_PURPLE));
        lines.add(Component.translatable("item.witchsatelier.inkwell.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
