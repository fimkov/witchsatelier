package com.fimkov.witchsatelier.registry;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.item.GlyphArrowItem;
import com.fimkov.witchsatelier.item.GrimoireItem;
import com.fimkov.witchsatelier.item.InkBottleItem;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.InkwellItem;
import com.fimkov.witchsatelier.item.PalmQuireItem;
import com.fimkov.witchsatelier.item.RagItem;
import com.fimkov.witchsatelier.item.SearneedleItem;
import com.fimkov.witchsatelier.item.TornPageItem;
import com.fimkov.witchsatelier.item.WitchGrimoireItem;
import com.fimkov.witchsatelier.item.WoodcruorWandItem;
import com.fimkov.witchsatelier.item.artifact.BrimhatMaskItem;
import com.fimkov.witchsatelier.item.artifact.CapturePennantItem;
import com.fimkov.witchsatelier.item.artifact.LightTracerBangleItem;
import com.fimkov.witchsatelier.item.artifact.RaincleaverItem;
import com.fimkov.witchsatelier.item.artifact.SealchairItem;
import com.fimkov.witchsatelier.item.artifact.SnugstoneItem;
import com.fimkov.witchsatelier.item.artifact.TentStaffItem;
import com.fimkov.witchsatelier.item.artifact.VaporBubbleItem;
import com.fimkov.witchsatelier.item.attire.WitchAttireItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WitchsAtelier.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WitchsAtelier.MODID);

    public static final DeferredItem<InkPenItem> INK_PEN = ITEMS.registerItem("ink_pen", InkPenItem::new);

    public static final DeferredItem<WoodcruorWandItem> WOODCRUOR_WAND = ITEMS.registerItem("woodcruor_wand",
            WoodcruorWandItem::new, new Item.Properties().durability(512));

    public static final DeferredItem<SearneedleItem> SEARNEEDLE = ITEMS.registerItem("searneedle",
            SearneedleItem::new, new Item.Properties().durability(1024).rarity(Rarity.UNCOMMON).fireResistant());

    public static final DeferredItem<RagItem> RAG = ITEMS.registerItem("rag", RagItem::new);

    public static final DeferredItem<PalmQuireItem> PALM_QUIRE = ITEMS.registerItem("palm_quire", PalmQuireItem::new);

    public static final DeferredItem<InkwellItem> INKWELL = ITEMS.registerItem("inkwell",
            p -> new InkwellItem(ModBlocks.INKWELL.get(), p));
    public static final DeferredItem<BlockItem> SILVERWOOD_LOG = ITEMS.registerSimpleBlockItem(ModBlocks.SILVERWOOD_LOG);
    public static final DeferredItem<BlockItem> SILVERWOOD_LEAVES = ITEMS.registerSimpleBlockItem(ModBlocks.SILVERWOOD_LEAVES);
    public static final DeferredItem<BlockItem> SILVERWOOD_SAPLING = ITEMS.registerSimpleBlockItem(ModBlocks.SILVERWOOD_SAPLING);

    public static final DeferredItem<Item> BLACKENED_TWIG = ITEMS.registerSimpleItem("blackened_silverwood_twig");

    public static final DeferredItem<Item> WOODCRUOR = ITEMS.registerSimpleItem("woodcruor", new Item.Properties().stacksTo(16));

    public static final DeferredItem<WitchAttireItem> APPRENTICE_HAT = attire("apprentice_hat", WitchAttireItem.Style.APPRENTICE_HAT);
    public static final DeferredItem<WitchAttireItem> WITCH_HAT = attire("witch_hat", WitchAttireItem.Style.WITCH_HAT);
    public static final DeferredItem<WitchAttireItem> WITCH_ROBE = attire("witch_robe", WitchAttireItem.Style.ROBE);
    public static final DeferredItem<WitchAttireItem> WITCH_MANTLE = attire("witch_mantle", WitchAttireItem.Style.MANTLE);
    public static final DeferredItem<WitchAttireItem> WITCH_BOOTS = attire("witch_boots", WitchAttireItem.Style.BOOTS);

    private static DeferredItem<WitchAttireItem> attire(String name, WitchAttireItem.Style style) {
        return ITEMS.registerItem(name, p -> new WitchAttireItem(style, p));
    }

    public static final DeferredItem<GrimoireItem> GRIMOIRE = ITEMS.registerItem("grimoire", GrimoireItem::new,
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<GlyphArrowItem> GLYPH_ARROW = ITEMS.registerItem("glyph_arrow",
            GlyphArrowItem::new, new Item.Properties());

    public static final DeferredItem<WitchGrimoireItem> WITCH_GRIMOIRE = ITEMS.registerItem("witch_grimoire",
            WitchGrimoireItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<DeferredSpawnEggItem> STRANGER_SPAWN_EGG = ITEMS.registerItem("stranger_spawn_egg",
            p -> new DeferredSpawnEggItem(ModEntities.STRANGER, 0x1C1626, 0xC9A65A, p));

    public static final DeferredItem<SnugstoneItem> SNUGSTONE = ITEMS.registerItem("snugstone",
            SnugstoneItem::new);
    public static final DeferredItem<VaporBubbleItem> VAPOR_BUBBLE = ITEMS.registerItem("vapor_bubble",
            VaporBubbleItem::new, new Item.Properties().durability(128));
    public static final DeferredItem<LightTracerBangleItem> LIGHT_TRACER_BANGLE = ITEMS.registerItem("light_tracer_bangle",
            LightTracerBangleItem::new, new Item.Properties().stacksTo(8).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<RaincleaverItem> RAINCLEAVER = ITEMS.registerItem("raincleaver",
            p -> new RaincleaverItem(Tiers.IRON,
                    p.attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4f)).rarity(Rarity.RARE)));
    public static final DeferredItem<WitchAttireItem> WINGCLOAK = attire("wingcloak", WitchAttireItem.Style.WINGCLOAK);
    public static final DeferredItem<CapturePennantItem> CAPTURE_PENNANT = ITEMS.registerItem("capture_pennant",
            CapturePennantItem::new, new Item.Properties().durability(64).rarity(Rarity.RARE));
    public static final DeferredItem<TentStaffItem> TENT_STAFF = ITEMS.registerItem("tent_staff",
            TentStaffItem::new, new Item.Properties().durability(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<WitchAttireItem> BRIMHAT_MANTLE = attire("brimhat_mantle", WitchAttireItem.Style.BRIMHAT_MANTLE);
    public static final DeferredItem<WitchAttireItem> MIRROR_MANTLE = attire("mirror_mantle", WitchAttireItem.Style.MIRROR_MANTLE);
    public static final DeferredItem<BlockItem> BEAST_WARDING_TOWER = ITEMS.registerSimpleBlockItem(ModBlocks.WARDING_TOWER);
    public static final DeferredItem<BlockItem> WASHING_BARREL = ITEMS.registerSimpleBlockItem(ModBlocks.WASHING_BARREL);
    public static final DeferredItem<BlockItem> ARCANE_LENS = ITEMS.registerSimpleBlockItem(ModBlocks.ARCANE_LENS);
    public static final DeferredItem<BlockItem> VOID_BASIN = ITEMS.registerSimpleBlockItem(ModBlocks.VOID_BASIN);
    public static final DeferredItem<SealchairItem> SEALCHAIR = ITEMS.registerItem("sealchair",
            props -> new SealchairItem(ModBlocks.SEALCHAIR.get(), props), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> GLOWSTONE_PATH = ITEMS.registerSimpleBlockItem(ModBlocks.GLOWSTONE_PATH);
    public static final DeferredItem<BlockItem> DRAGON_TEACUP = ITEMS.registerSimpleBlockItem(ModBlocks.DRAGON_TEACUP);

    public static final DeferredItem<BrimhatMaskItem> BRIMHAT_MASK = ITEMS.registerItem("brimhat_mask",
            p -> new BrimhatMaskItem(p.durability(ArmorItem.Type.HELMET.getDurability(12)).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<DeferredSpawnEggItem> BRIMHAT_SPAWN_EGG = ITEMS.registerItem("brimhat_spawn_egg",
            p -> new DeferredSpawnEggItem(ModEntities.BRIMHAT, 0x16101A, 0xB02030, p));
    public static final DeferredItem<DeferredSpawnEggItem> KNIGHT_MORALIS_SPAWN_EGG = ITEMS.registerItem("knight_moralis_spawn_egg",
            p -> new DeferredSpawnEggItem(ModEntities.KNIGHT_MORALIS, 0xE8ECF2, 0x6A7A9A, p));

    public static final DeferredItem<TornPageItem> TORN_PAGE = ITEMS.registerItem("torn_page", TornPageItem::new);

    public static final DeferredItem<InkBottleItem> CONJURING_INK = ink(InkType.CONJURING, Rarity.COMMON);
    public static final DeferredItem<InkBottleItem> WATERPROOF_INK = ink(InkType.WATERPROOF, Rarity.UNCOMMON);
    public static final DeferredItem<InkBottleItem> INVISIBLE_INK = ink(InkType.INVISIBLE, Rarity.UNCOMMON);
    public static final DeferredItem<InkBottleItem> LASTING_INK = ink(InkType.LASTING, Rarity.UNCOMMON);
    public static final DeferredItem<InkBottleItem> BLOOD_INK = ink(InkType.BLOOD, Rarity.RARE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("witchsatelier", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.witchsatelier"))
            .icon(() -> new ItemStack(INK_PEN.get()))
            .displayItems((params, out) -> {
                out.accept(INK_PEN.get());
                out.accept(WOODCRUOR_WAND.get());
                out.accept(SEARNEEDLE.get());
                out.accept(PALM_QUIRE.get());
                var rnd = RandomSource.create(7);
                out.accept(GlyphLibrary.volumeBook(GlyphLibrary.PRIMER, rnd));
                for (var v : GlyphLibrary.VOLUMES) out.accept(GlyphLibrary.volumeBook(v, rnd));
                out.accept(GlyphLibrary.volumeBook(GlyphLibrary.RING_NOTES, rnd));
                out.accept(GlyphLibrary.volumeBook(GlyphLibrary.FORBIDDEN_BOOK, rnd));
                out.accept(WITCH_GRIMOIRE.get());
                out.accept(GLYPH_ARROW.get());
                out.accept(RAG.get());
                out.accept(GlyphLibrary.contraptionsBook());
                out.accept(STRANGER_SPAWN_EGG.get());
                out.accept(BRIMHAT_SPAWN_EGG.get());
                out.accept(KNIGHT_MORALIS_SPAWN_EGG.get());
                out.accept(BRIMHAT_MASK.get());
                out.accept(BRIMHAT_MANTLE.get());
                out.accept(SNUGSTONE.get());
                out.accept(VAPOR_BUBBLE.get());
                out.accept(LIGHT_TRACER_BANGLE.get());
                out.accept(RAINCLEAVER.get());
                out.accept(MIRROR_MANTLE.get());
                out.accept(WINGCLOAK.get());
                out.accept(CAPTURE_PENNANT.get());
                out.accept(TENT_STAFF.get());
                out.accept(BEAST_WARDING_TOWER.get());
                out.accept(WASHING_BARREL.get());
                out.accept(ARCANE_LENS.get());
                out.accept(VOID_BASIN.get());
                out.accept(GLOWSTONE_PATH.get());
                out.accept(SEALCHAIR.get());
                out.accept(DRAGON_TEACUP.get());
                out.accept(INKWELL.get());
                out.accept(APPRENTICE_HAT.get());
                out.accept(WITCH_HAT.get());
                out.accept(WITCH_ROBE.get());
                out.accept(WITCH_MANTLE.get());
                out.accept(WITCH_BOOTS.get());
                out.accept(SILVERWOOD_SAPLING.get());
                out.accept(SILVERWOOD_LOG.get());
                out.accept(SILVERWOOD_LEAVES.get());
                out.accept(BLACKENED_TWIG.get());
                out.accept(WOODCRUOR.get());
                out.accept(CONJURING_INK.get());
                out.accept(WATERPROOF_INK.get());
                out.accept(INVISIBLE_INK.get());
                out.accept(LASTING_INK.get());
                out.accept(BLOOD_INK.get());
            })
            .build());

    private static DeferredItem<InkBottleItem> ink(InkType type, Rarity rarity) {
        return ITEMS.registerItem(type.id + "_ink", p -> new InkBottleItem(type, p.rarity(rarity)));
    }

    public static InkBottleItem bottleFor(InkType type) {
        return switch (type) {
            case CONJURING -> CONJURING_INK.get();
            case WATERPROOF -> WATERPROOF_INK.get();
            case INVISIBLE -> INVISIBLE_INK.get();
            case LASTING -> LASTING_INK.get();
            case BLOOD -> BLOOD_INK.get();
            default -> CONJURING_INK.get();
        };
    }

    private ModItems() {}
}
