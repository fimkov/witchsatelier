package com.fimkov.witchsatelier.glyph.ink;

import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.InkwellItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class InkSupply {
    public final InteractionHand penHand;

    public final InkType ink;

    private final InkType source;
    private final boolean self;
    private final ServerPlayer player;
    private final ItemStack pen;

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

    public static InkSupply of(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof InkPenItem) return new InkSupply(player, InteractionHand.MAIN_HAND);
        if (player.getOffhandItem().getItem() instanceof InkPenItem) return new InkSupply(player, InteractionHand.OFF_HAND);
        return null;
    }

    public int available() {
        return creative ? Integer.MAX_VALUE : onNib + inBottle;
    }

    public void consume(int cost) {
        if (creative) return;
        if (self) {
            pen.hurtAndBreak(Math.max(1, (cost + 3) / 4), player, penHand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            return;
        }
        if (ink != source && pen.isDamageableItem()) {
            pen.hurtAndBreak(Math.max(1, cost / 64), player, penHand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
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
