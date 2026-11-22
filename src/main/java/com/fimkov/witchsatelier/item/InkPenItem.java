package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.ink.LoadedInk;
import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The witch's ink wand: a dip pen. It carries only a little ink on the nib and dips itself into the ink bottle
 * held in the other hand. Use on a surface to start drawing.
 */
public class InkPenItem extends Item {
    /** Ink the nib holds: 64 blocks of line before it needs dipping again. */
    public static final int NIB_CAPACITY = 256;

    public InkPenItem(Properties props) {
        super(props.stacksTo(1));
    }

    /** Holding use on a surface "uses" the pen like a brush; the client draws while it is in use. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player != null) player.startUsingItem(ctx.getHand());
        return InteractionResult.CONSUME;
    }

    /** Ink on the nib running down or the wand wearing away is no reason to lower and raise the hand again. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** The ink a line comes out as, given the ink the pen takes up (the searneedle sears it, chalk is chalk). */
    public InkType lineInk(InkType source) {
        return source;
    }

    /** True for tools that carry their own drawing stuff instead of dipping into an inkwell. */
    public boolean selfInked() {
        return false;
    }

    public static LoadedInk loaded(ItemStack pen) {
        return pen.get(ModComponents.LOADED_INK.get());
    }

    public static void setLoaded(ItemStack pen, InkType type, int amount) {
        if (amount <= 0) pen.remove(ModComponents.LOADED_INK.get());
        else pen.set(ModComponents.LOADED_INK.get(), new LoadedInk(type, Math.min(amount, NIB_CAPACITY)));
    }

    /** The ink the next line will be drawn with: what is on the nib, else what the inkwell it would dip into holds. */
    public static InkType inkFor(Player player, ItemStack pen) {
        LoadedInk l = loaded(pen);
        if (l != null && l.amount() > 0) return l.type();
        ItemStack well = findInkwell(player, pen, null);
        LoadedInk c = well.isEmpty() ? null : InkwellItem.contents(well);
        return c != null ? c.type() : InkType.CONJURING;
    }

    /**
     * The inkwell the pen dips into: the one in the other hand, else the first non-empty one in the inventory.
     * With {@code ink} set, only an inkwell holding that ink counts.
     */
    public static ItemStack findInkwell(Player player, ItemStack pen, InkType ink) {
        ItemStack other = player.getMainHandItem() == pen ? player.getOffhandItem() : player.getMainHandItem();
        if (usable(other, ink)) return other;
        for (ItemStack st : player.getInventory().items) if (usable(st, ink)) return st;
        return ItemStack.EMPTY;
    }

    private static boolean usable(ItemStack st, InkType ink) {
        if (!(st.getItem() instanceof InkwellItem)) return false;
        LoadedInk c = InkwellItem.contents(st);
        return c != null && c.amount() > 0 && (ink == null || c.type() == ink);
    }

    public static int barColor(InkType t) {
        return switch (t) {
            case INVISIBLE -> 0xB7A8E8;
            case BLOOD -> 0xC0202A;
            case WATERPROOF -> 0x2FB8C8;
            case LASTING -> 0xD08A30;
            default -> 0x7A5CFF;
        };
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return loaded(stack) != null;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        LoadedInk l = loaded(stack);
        return l == null ? 0 : Mth.clamp(Math.round(13f * l.amount() / NIB_CAPACITY), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        LoadedInk l = loaded(stack);
        return l == null ? 0 : barColor(l.type());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        LoadedInk l = loaded(stack);
        if (l == null) {
            lines.add(Component.translatable("item.witchsatelier.ink_pen.dry").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.witchsatelier.ink_pen.loaded",
                    Component.translatable("item.witchsatelier." + l.type().id + "_ink"), l.amount() / 4).withStyle(ChatFormatting.DARK_PURPLE));
        }
        lines.add(Component.translatable("item.witchsatelier.ink_pen.hint_dip").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.witchsatelier.ink_pen.hint_draw").withStyle(ChatFormatting.GRAY));
    }
}
