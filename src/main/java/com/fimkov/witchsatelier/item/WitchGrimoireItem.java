package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class WitchGrimoireItem extends Item {
    public WitchGrimoireItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null || !p.isShiftKeyDown()) return InteractionResult.PASS;
        if (p instanceof ServerPlayer sp)
            PageService.handleCopy(sp, ctx.getHand(), new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), ctx.isInside()));
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) PageService.handleCopy(sp, hand, null);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        if (level.isClientSide) PalmQuireItem.clientOpener.accept(hand);
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8f, 1.0f);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        String keeper = stack.get(ModComponents.KEEPER.get());
        if (keeper != null) lines.add(Component.translatable("item.witchsatelier.witch_grimoire.keeper", keeper).withStyle(ChatFormatting.GRAY));
        var gp = stack.get(ModComponents.GLYPH_PAGES.get());
        long filled = gp == null ? 0 : gp.pages().stream().filter(pg -> !pg.isEmpty()).count();
        if (filled > 0) lines.add(Component.translatable("item.witchsatelier.witch_grimoire.filled", filled).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.witchsatelier.witch_grimoire.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean written(ItemStack stack) {
        var gp = stack.get(ModComponents.GLYPH_PAGES.get());
        return stack.has(ModComponents.KEEPER.get()) || (gp != null && gp.pages().stream().anyMatch(pg -> !pg.isEmpty()));
    }
}
