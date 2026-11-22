package com.fimkov.witchsatelier.glyph.ink;

import com.fimkov.witchsatelier.item.InkwellItem;
import com.fimkov.witchsatelier.item.InkPenItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The ink a player can draw with right now: what is on the pen's nib, plus the inkwell of the same ink in the
 * other hand that the pen dips into. Creative players never run dry.
 */
public final class InkSupply {
    public final InteractionHand penHand;
    /** What the line is drawn in. */
    public final InkType ink;
    /** What is taken up from nib and inkwell (differs from {@link #ink} for a searneedle). */
    private final InkType source;
    private final boolean self;
    private final ServerPlayer player;
    private final ItemStack pen;
    /** The inkwell the pen dips into (in the other hand or anywhere in the inventory), or empty. */
    private final ItemStack well;
    private final int onNib;
    private final int inBottle;
    private final boolean creative;

    private InkSupply(ServerPlayer player, InteractionHand penHand) {
        this.player = player;
        this.penHand = penHand;
        this.pen = player.getItemInHand(penHand);
        this.creative = player.getAbilities().instabuild;
        InkPenItem tool = (InkPenItem) pen.getItem();
        this.self = tool.selfInked();
        if (self) {
            // a woodcruor wand is its own ink: each four units of line wear it down by one
            this.source = InkType.CHALK;
            this.ink = InkType.CHALK;
            this.onNib = (pen.getMaxDamage() - pen.getDamageValue()) * 4;
            this.well = ItemStack.EMPTY;
            this.inBottle = 0;
            return;
        }
        this.source = InkPenItem.inkFor(player, pen);
        this.ink = tool.lineInk(source);
        LoadedInk nib = InkPenItem.loaded(pen);
        this.onNib = nib != null && nib.type() == source ? nib.amount() : 0;
        this.well = InkPenItem.findInkwell(player, pen, source);
        LoadedInk c = well.isEmpty() ? null : InkwellItem.contents(well);
        this.inBottle = c != null && c.type() == source ? c.amount() : 0;
    }

    /** The supply of the pen the player holds, or null without a pen. */
    public static InkSupply of(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof InkPenItem) return new InkSupply(player, InteractionHand.MAIN_HAND);
        if (player.getOffhandItem().getItem() instanceof InkPenItem) return new InkSupply(player, InteractionHand.OFF_HAND);
        return null;
    }

    /** Ink units of line the player can still draw (the witch's outfit stretches the ink). */
    public int available() {
        return creative ? Integer.MAX_VALUE : onNib + inBottle;
    }

    /** Spends ink: first from the nib, then the pen dips (taking what was still needed plus a full nib). */
    public void consume(int cost) {
        if (creative) return;
        if (self) {
            pen.hurtAndBreak(Math.max(1, (cost + 3) / 4), player, penHand == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            return;
        }
        if (ink != source && pen.isDamageableItem()) {
            // searing costs the needle a little heat-wear
            pen.hurtAndBreak(Math.max(1, cost / 64), player, penHand == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        int fromNib = Math.min(cost, onNib);
        int need = cost - fromNib;
        int nibAfter = onNib - fromNib;
        if ((need > 0 || nibAfter == 0) && inBottle > 0) {
            int take = Math.min(inBottle, need + InkPenItem.NIB_CAPACITY);
            nibAfter = Math.min(InkPenItem.NIB_CAPACITY, Math.max(0, take - need));
            InkwellItem.setContents(well, new LoadedInk(source, inBottle - take));
        }
        InkPenItem.setLoaded(pen, source, nibAfter);
    }
}
