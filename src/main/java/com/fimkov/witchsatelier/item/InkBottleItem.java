package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A bottle of brewed ink. Pour it into an inkwell (use on a placed inkwell, or click it onto an inkwell in your
 * inventory). Sneak + use a bottle of plain conjuring ink to bleed into it: forbidden blood ink.
 */
public class InkBottleItem extends Item {
    /** Ink in one bottle: 256 blocks of line. */
    public static final int UNITS = 1024;
    public final InkType ink;

    public InkBottleItem(InkType ink, Properties props) {
        super(props.stacksTo(16));
        this.ink = ink;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ink != InkType.CONJURING || !player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            player.hurt(level.damageSources().magic(), 4);
            stack.shrink(1);
            ItemStack blood = new ItemStack(ModItems.BLOOD_INK.get());
            if (!player.getInventory().add(blood)) player.drop(blood, false);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1f, 0.5f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier." + ink.id + "_ink.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.witchsatelier.ink.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
