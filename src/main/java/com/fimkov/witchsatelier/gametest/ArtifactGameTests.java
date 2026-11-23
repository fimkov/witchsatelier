package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.entity.DoorknobEntity;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.item.artifact.DoorknobItem;
import com.fimkov.witchsatelier.item.artifact.DoorknobLinks;
import com.fimkov.witchsatelier.item.artifact.GuidanceOrbItem;
import com.fimkov.witchsatelier.item.artifact.Mailroom;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class ArtifactGameTests {
    private ArtifactGameTests() {}

    private static List<Item> newArtifacts() {
        return List.of(ModItems.DOORKNOB.get(), ModItems.GUIDANCE_ORB.get(), ModItems.FLUTTERGRAM.get());
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static String tag(GameTestHelper h) {
        return String.valueOf(10000 + h.getLevel().random.nextInt(90000));
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h, String name, Vec3 rel) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() { return false; }

            @Override
            public boolean isCreative() { return false; }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        move(h, p, rel);
        return p;
    }

    private static void move(GameTestHelper h, ServerPlayer p, Vec3 rel) {
        Vec3 at = h.absoluteVec(rel);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0, 0);
    }

    private static Awakening.Entry entry(Item item) {
        return Awakening.ALL.stream().filter(e -> e.artifact().get().asItem() == item).findFirst().orElseThrow();
    }

    @GameTest(template = "empty")
    public static void eachNewArtifactWakesOnlyFromItsOwnGlyph(GameTestHelper h) {
        for (Item item : newArtifacts()) {
            Awakening.Entry e = entry(item);
            if (Awakening.glyphOf(Awakening.dormantStack(e)) != e.glyph()) h.fail(e.glyph().name() + " should be looked up by its item");
            for (int seed = 0; seed < 6; seed++) {
                var page = GlyphLibrary.draw(e.glyph(), RandomSource.create(seed), false, InkType.CONJURING);
                var r = GlyphAnalyzer.analyze(PageService.pointsOf(page), PageService.PAGE_INK);
                if (!r.closed() || !GlyphLibrary.readsAs(e.glyph(), r)) h.fail(e.glyph().name() + " should read as itself: " + r.describe());
                for (Awakening.Entry other : Awakening.ALL)
                    if (other != e && GlyphLibrary.readsAs(other.glyph(), r)) h.fail(e.glyph().name() + " also wakes " + other.glyph().name());
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oneDrawingWakesAPairOfKnobsAndAStackOfBirds(GameTestHelper h) {
        @SuppressWarnings("removal")
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        ItemStack pen = new ItemStack(ModItems.INK_PEN.get());
        InkPenItem.setLoaded(pen, InkType.CONJURING, 256);
        player.setItemInHand(InteractionHand.MAIN_HAND, pen);
        for (Item item : List.of(ModItems.DOORKNOB.get(), ModItems.FLUTTERGRAM.get())) {
            Awakening.Entry e = entry(item);
            ItemStack stack = Awakening.dormantStack(e);
            stack.setCount(item == ModItems.DOORKNOB.get() ? 2 : 4);
            int count = stack.getCount();
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
            for (var s : GlyphLibrary.draw(e.glyph(), RandomSource.create(5), false, InkType.CONJURING).strokes())
                PageService.handleStroke(player, InteractionHand.OFF_HAND, 0, s.pts());
            ItemStack after = player.getOffhandItem();
            if (!after.is(item) || after.getCount() != count) h.fail("the whole stack should stay together, got " + after);
            if (Awakening.dormant(after)) h.fail(e.glyph().name() + " should wake from its glyph");
            if (after.has(ModComponents.GLYPH_PAGES.get())) h.fail("the waking glyph should be gone from " + e.glyph().name());
        }
        h.succeed();
    }

    private static void door(GameTestHelper h, BlockPos rel, Direction facing) {
        BlockState st = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.OPEN, true);
        h.setBlock(rel, st.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(rel.above(), st.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void use(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    private static void near(GameTestHelper h, Entity e, Vec3 rel, String what) {
        Vec3 want = h.absoluteVec(rel);
        if (e.position().distanceTo(want) > 0.3) h.fail(what + ": expected near " + rel + ", at " + h.relativeVec(e.position()));
    }

    private static Pig stray(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(Pig.class, new AABB(h.absolutePos(new BlockPos(8, 1, 5))).inflate(4),
                e -> e.getTags().contains("wa_other_side")).stream().findFirst().orElseThrow();
    }

    private static int knobsNear(GameTestHelper h, BlockPos rel) {
        return h.getLevel().getEntitiesOfClass(DoorknobEntity.class, new AABB(h.absolutePos(rel)).inflate(2)).size();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aKnobbedDoorLeadsOutOfItsPartner(GameTestHelper h) {
        floor(h);
        BlockPos a = new BlockPos(2, 1, 5), b = new BlockPos(8, 1, 5);
        door(h, a, Direction.EAST);
        door(h, b, Direction.WEST);
        ServerPlayer keeper = player(h, "wa_door" + tag(h), new Vec3(1.5, 1, 5.5));
        keeper.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DOORKNOB.get(), 2));
        keeper.getMainHandItem().use(h.getLevel(), keeper, InteractionHand.MAIN_HAND);
        if (DoorknobItem.bond(keeper.getMainHandItem()) == null) h.fail("using the pair should bind it");
        use(h, keeper, a);
        move(h, keeper, new Vec3(9.5, 1, 5.5));
        use(h, keeper, b.above());
        move(h, keeper, new Vec3(5.5, 1, 9.5));
        if (!keeper.getMainHandItem().isEmpty()) h.fail("both knobs should be on the doors, left " + keeper.getMainHandItem());
        if (knobsNear(h, a) != 1 || knobsNear(h, b) != 1) h.fail("each door should show its knob");
        if (!h.getBlockState(a).getValue(DoorBlock.OPEN)) h.fail("clicking a knob on must not shut the door");

        Pig pig = h.spawn(EntityType.PIG, new Vec3(2.2, 1, 5.5));
        pig.setNoAi(true);
        h.startSequence()
                .thenExecuteAfter(2, () -> pig.setPos(h.absoluteVec(new Vec3(2.7, 1, 5.5))))
                .thenExecuteAfter(2, () -> {
                    near(h, pig, new Vec3(9.5, 1, 5.5), "the pig should come out of the partner door, on its knob side");
                    pig.discard();
                    move(h, keeper, new Vec3(2.2, 1, 5.5));
                })
                .thenExecuteAfter(2, () -> move(h, keeper, new Vec3(2.7, 1, 5.5)))
                .thenExecuteAfter(2, () -> {
                    near(h, keeper, new Vec3(9.35, 1, 5.5), "the player should come out of the partner door");
                    move(h, keeper, new Vec3(5.5, 1, 9.5));
                })
                .thenExecuteAfter(1, () -> {
                    Pig other = h.spawn(EntityType.PIG, new Vec3(8.2, 1, 3.5));
                    other.setNoAi(true);
                    other.addTag("wa_other_side");
                })
                .thenExecuteAfter(2, () -> stray(h).setPos(h.absoluteVec(new Vec3(8.2, 1, 5.3))))
                .thenExecuteAfter(2, () -> stray(h).setPos(h.absoluteVec(new Vec3(8.8, 1, 5.3))))
                .thenExecuteAfter(2, () -> near(h, stray(h), new Vec3(8.8, 1, 5.3), "walking through a door from its bare side is just walking"))
                .thenExecuteAfter(1, () -> {
                    keeper.setShiftKeyDown(true);
                    use(h, keeper, a.above());
                    keeper.setShiftKeyDown(false);
                    if (!keeper.getMainHandItem().is(ModItems.DOORKNOB.get())) h.fail("sneak-using the door should give its knob back");
                    if (DoorknobLinks.get(h.getLevel().getServer()).at(GlobalPos.of(h.getLevel().dimension(), h.absolutePos(a))) != null)
                        h.fail("the knob should be off the door");
                })
                .thenExecuteAfter(21, () -> {
                    if (knobsNear(h, a) != 0) h.fail("the taken knob should vanish from the door");
                    h.setBlock(b.above(), Blocks.AIR);
                    h.setBlock(b, Blocks.AIR);
                })
                .thenExecuteAfter(3, () -> {
                    if (DoorknobLinks.get(h.getLevel().getServer()).at(GlobalPos.of(h.getLevel().dimension(), h.absolutePos(b))) != null)
                        h.fail("a broken door should lose its knob");
                    var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(b)).inflate(2),
                            e -> e.getItem().is(ModItems.DOORKNOB.get()));
                    if (dropped.size() != 1) h.fail("the knob of a broken door should drop, found " + dropped.size());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theGuidanceOrbPointsWhereAFragmentCameFrom(GameTestHelper h) {
        floor(h);
        BlockPos lode = new BlockPos(8, 1, 8), ore = new BlockPos(2, 1, 8);
        h.setBlock(lode, Blocks.LODESTONE);
        h.setBlock(ore, Blocks.DEEPSLATE_IRON_ORE);
        ServerPlayer p = player(h, "wa_orb" + tag(h), new Vec3(2.5, 1, 2.5));
        ItemStack orb = new ItemStack(ModItems.GUIDANCE_ORB.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, orb);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.LODESTONE, 3));
        p.setShiftKeyDown(true);
        orb.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        if (p.getOffhandItem().getCount() != 2) h.fail("one lodestone should go into the orb");
        var held = orb.get(ModComponents.FRAGMENT.get());
        if (held == null || !held.stack().is(Items.LODESTONE) || held.stack().getCount() != 1) h.fail("the orb should hold one lodestone");

        Vec3 to = GuidanceOrbItem.target(h.getLevel(), p, held.stack());
        if (to == null || to.distanceTo(Vec3.atCenterOf(h.absolutePos(lode))) > 0.01) h.fail("the thread should lead to the lodestone, got " + to);
        if (!h.absolutePos(ore).equals(GuidanceOrbItem.seekBlock(h.getLevel(), p.blockPosition(), new ItemStack(Items.RAW_IRON))))
            h.fail("raw iron should lead to iron ore");
        if (GuidanceOrbItem.seekBlock(h.getLevel(), p.blockPosition(), new ItemStack(Items.SPONGE)) != null)
            h.fail("with nothing like it in range, the orb should only spin");

        Cow cow = h.spawn(EntityType.COW, new Vec3(4.5, 1, 2.5));
        cow.setNoAi(true);
        if (GuidanceOrbItem.seekCreature(h.getLevel(), p.position(), new ItemStack(Items.LEATHER), p) != cow)
            h.fail("leather should lead to the cow");
        if (GuidanceOrbItem.seekCreature(h.getLevel(), p.position(), new ItemStack(Items.STONE), p) != null)
            h.fail("a stone is no creature's");

        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        orb.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        if (orb.has(ModComponents.FRAGMENT.get()) || !p.getOffhandItem().is(Items.LODESTONE)) h.fail("sneak-using it again should shake the lodestone out");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aFluttergramCarriesTheLetterToItsAddressee(GameTestHelper h) {
        floor(h);
        String t = tag(h);
        ServerPlayer from = player(h, "wa_from" + t, new Vec3(2.5, 1, 2.5));
        ServerPlayer to = player(h, "wa_to" + t, new Vec3(8.5, 1, 8.5));
        ItemStack birds = new ItemStack(ModItems.FLUTTERGRAM.get(), 3);
        birds.set(DataComponents.CUSTOM_NAME, Component.literal("WA_TO" + t));
        ItemStack letter = new ItemStack(Items.WRITABLE_BOOK);
        letter.set(DataComponents.CUSTOM_NAME, Component.literal("letter " + t));
        from.setItemInHand(InteractionHand.MAIN_HAND, birds);
        from.setItemInHand(InteractionHand.OFF_HAND, letter);
        int before = Mailroom.inFlight(h.getLevel().getServer());
        birds.use(h.getLevel(), from, InteractionHand.MAIN_HAND);
        if (from.getMainHandItem().getCount() != 2) h.fail("one bird should fly off");
        if (!from.getOffhandItem().isEmpty()) h.fail("the letter should go with it");
        if (Mailroom.inFlight(h.getLevel().getServer()) != before + 1) h.fail("the letter should be in flight");
        h.startSequence()
                .thenWaitUntil(() -> {
                    boolean got = false;
                    for (ItemStack s : to.getInventory().items)
                        if (s.is(Items.WRITABLE_BOOK) && ("letter " + t).equals(s.getHoverName().getString())) got = true;
                    if (!got) h.fail("waiting for the fluttergram to land");
                })
                .thenExecute(() -> {
                    for (ItemStack s : from.getInventory().items) if (s.is(Items.WRITABLE_BOOK)) h.fail("the sender should not keep a copy");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void aFluttergramWithNoOneToFindStaysInHand(GameTestHelper h) {
        String t = tag(h);
        ServerPlayer from = player(h, "wa_lone" + t, new Vec3(2.5, 1, 2.5));
        ItemStack birds = new ItemStack(ModItems.FLUTTERGRAM.get(), 2);
        birds.set(DataComponents.CUSTOM_NAME, Component.literal("nobody" + t));
        from.setItemInHand(InteractionHand.MAIN_HAND, birds);
        from.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.PAPER));
        int before = Mailroom.inFlight(h.getLevel().getServer());
        birds.use(h.getLevel(), from, InteractionHand.MAIN_HAND);
        if (birds.getCount() != 2 || from.getOffhandItem().getCount() != 1) h.fail("a bird with no one to find should not fly");

        birds.set(DataComponents.CUSTOM_NAME, Component.literal("wa_lone" + t));
        from.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND));
        birds.use(h.getLevel(), from, InteractionHand.MAIN_HAND);
        if (birds.getCount() != 2 || from.getOffhandItem().getCount() != 1) h.fail("a fluttergram carries letters, not goods");
        if (Mailroom.inFlight(h.getLevel().getServer()) != before) h.fail("nothing should be in flight");
        h.succeed();
    }
}
