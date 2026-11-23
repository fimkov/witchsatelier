package com.fimkov.witchsatelier.block;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.ink.LoadedInk;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class InkwellBlockEntity extends BlockEntity {
    public static final int CAPACITY = 4096;
    private InkType ink = InkType.CONJURING;
    private int amount;

    public InkwellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.INKWELL_BE.get(), pos, state);
    }

    public InkType ink() { return ink; }
    public int amount() { return amount; }

    public boolean pour(InkType type, int units) {
        if (amount > 0 && type != ink) return false;
        if (amount >= CAPACITY) return false;
        ink = type;
        amount = Math.min(CAPACITY, amount + units);
        changed();
        return true;
    }

    public int take(int max) {
        int t = Math.min(max, amount);
        amount -= t;
        changed();
        return t;
    }

    public void refreshState() {
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            BlockState n = s.setValue(InkwellBlock.FILL, InkwellBlock.fillFor(amount)).setValue(InkwellBlock.INK, ink);
            if (n != s) level.setBlock(worldPosition, n, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ink", ink.id);
        tag.putInt("amount", amount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ink = InkType.byName(tag.getString("ink"));
        amount = tag.getInt("amount");
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        LoadedInk l = input.get(ModComponents.INKWELL_INK.get());
        if (l != null) {
            ink = l.type();
            amount = l.amount();
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        if (amount > 0) components.set(ModComponents.INKWELL_INK.get(), new LoadedInk(ink, amount));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("ink");
        tag.remove("amount");
    }
}
