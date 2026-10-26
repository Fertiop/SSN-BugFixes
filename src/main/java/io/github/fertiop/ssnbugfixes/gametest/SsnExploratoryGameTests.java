package io.github.fertiop.ssnbugfixes.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.UUID;
import com.lothrazar.storagenetwork.api.DimPos;
import com.lothrazar.storagenetwork.block.cable.link.TileCableLink;
import com.lothrazar.storagenetwork.block.main.TileMain;
import com.lothrazar.storagenetwork.capability.handler.ItemStackMatcher;
import com.lothrazar.storagenetwork.block.request.ContainerNetworkCraftingTable;
import com.lothrazar.storagenetwork.api.IConnectableLink;
import com.lothrazar.storagenetwork.item.ItemBuilder;
import com.lothrazar.storagenetwork.registry.SsnRegistry;
import com.lothrazar.storagenetwork.registry.StorageNetworkCapabilities;
import io.github.fertiop.ssnbugfixes.SsnBugFixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Behavioral regression tests for SSN's edge-case bugs. Every case is now
 * required in GREEN runs, because a specific original-SSN RED and
 * SSN-BugFixes GREEN have been demonstrated in Forge GameTests.
 * Original stock SSN must still be tested in a separate baseline run.
 */
@GameTestHolder(SsnBugFixes.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SsnExploratoryGameTests {
    private static final BlockPos MASTER = new BlockPos(1, 1, 1);
    private static final BlockPos LINK = new BlockPos(2, 1, 1);
    private static final BlockPos CHEST = new BlockPos(3, 1, 1);

    private SsnExploratoryGameTests() {}

    /**
     * SSN-003: two cables target one physical chest. The stock dedup guard
     * is intentional, but the chosen *settings* should belong to the
     * higher-priority cable, not depend on the cable's coordinates.
     * Optional until priority semantics are confirmed with the owner.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_003_higher_priority_duplicate_link_wins_filter(GameTestHelper helper) {
        TileMain main = basicNetwork(helper);
        ((ChestBlockEntity) helper.getBlockEntity(CHEST))
                .setItem(0, new ItemStack(Items.DIAMOND, 7));
        helper.setBlock(new BlockPos(2, 1, 2), SsnRegistry.Blocks.KABEL.get());
        BlockPos secondLinkPos = new BlockPos(3, 1, 2);
        helper.setBlock(secondLinkPos, SsnRegistry.Blocks.STORAGE_KABEL.get());

        TileCableLink first = (TileCableLink) helper.getBlockEntity(LINK);
        TileCableLink second = (TileCableLink) helper.getBlockEntity(secondLinkPos);
        second.setDirection(Direction.NORTH);
        second.setChanged();
        IConnectableLink firstCap = first.getCapability(
                StorageNetworkCapabilities.CONNECTABLE_ITEM_STORAGE_CAPABILITY, null).orElse(null);
        IConnectableLink secondCap = second.getCapability(
                StorageNetworkCapabilities.CONNECTABLE_ITEM_STORAGE_CAPABILITY, null).orElse(null);
        helper.assertTrue(firstCap != null && secondCap != null, "Both duplicate cables must expose SSN link capability");

        // Same chest: the coordinate-earlier link filters diamonds, while the
        // priority winner does not. Count must stay 7, never 14.
        firstCap.setPriority(100);
        firstCap.setFilter(0, new ItemStack(Items.DIAMOND));
        secondCap.setPriority(-100);
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));

        int visible = count(main.getNetwork().getSortedStacks(), Items.DIAMOND);
        helper.assertTrue(visible == 7,
                "SSN-003: high priority cable sees 7 diamonds; low priority cable filters them, got " + visible);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_004_request_must_not_report_simulation_as_real_debit(GameTestHelper helper) {
        TileMain main = basicNetwork(helper);
        BlockPos chestPos = helper.absolutePos(CHEST);
        helper.getLevel().removeBlockEntity(chestPos);
        helper.getLevel().setBlockEntity(
                new DivergentChestBlockEntity(chestPos, helper.getBlockState(CHEST)));
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));

        ItemStackMatcher matcher = new ItemStackMatcher(new ItemStack(Items.STONE), false, false);
        ItemStack simulation = main.request(matcher, 1, true);
        ItemStack paid = main.request(matcher, 1, false);
        helper.assertTrue(simulation.getCount() == 1,
                "Fixture must advertise one stone during simulation");
        helper.assertTrue(paid.isEmpty(),
                "SSN-004: real extraction returned a phantom material from simulation");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_004_no_free_block_on_failed_extraction(GameTestHelper helper) {
        TileMain main = basicNetwork(helper);
        BlockPos chestPos = helper.absolutePos(CHEST);
        helper.getLevel().removeBlockEntity(chestPos);
        helper.getLevel().setBlockEntity(
                new DivergentChestBlockEntity(chestPos, helper.getBlockState(CHEST)));
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));

        // The test provider advertises one STONE on simulation and refuses
        // the actual extraction. SSN must not place a free STONE block.
        ServerPlayer player = mockPlayerWithChannel(helper);
        BlockPos base = new BlockPos(1, 1, 3);
        helper.setBlock(base, Blocks.COBBLESTONE);
        BlockPos target = base.above();
        ItemStack remote = new ItemStack(SsnRegistry.Items.BUILDER_REMOTE.get());
        DimPos.putPos(remote, helper.absolutePos(MASTER), helper.getLevel());
        ItemBuilder.setBlockState(remote, Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);

        BlockPos abs = helper.absolutePos(base);
        player.teleportTo(abs.getX() + 0.5, abs.getY() + 1.5, abs.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        remote.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(helper.getBlockState(target).isAir(),
                "SSN-004: block appeared although real network extraction returned EMPTY");
        helper.succeed();
    }

    /**
     * Required positive control for SSN-004: normal honest handler must
     * spend exactly one stone to place exactly one stone.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_004_normal_builder_placement_debits_stone(GameTestHelper helper) {
        basicNetwork(helper);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.STONE, 2));
        chest.setChanged();

        ServerPlayer player = mockPlayerWithChannel(helper);
        BlockPos base = new BlockPos(1, 1, 3);
        helper.setBlock(base, Blocks.COBBLESTONE);
        ItemStack remote = new ItemStack(SsnRegistry.Items.BUILDER_REMOTE.get());
        DimPos.putPos(remote, helper.absolutePos(MASTER), helper.getLevel());
        ItemBuilder.setBlockState(remote, Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        BlockPos abs = helper.absolutePos(base);
        player.teleportTo(abs.getX() + 0.5, abs.getY() + 1.5, abs.getZ() + 0.5);
        remote.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));

        helper.assertTrue(helper.getBlockState(base.above()).is(Blocks.STONE),
                "Positive control: honest storage must allow builder remote to place one stone");
        helper.assertTrue(chest.getItem(0).is(Items.STONE) && chest.getItem(0).getCount() == 1,
                "Positive control: builder remote placement must debit exactly one stone");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_004_failed_placement_refunds_consumed_stone(GameTestHelper helper) {
        TileMain main = basicNetwork(helper);
        BlockPos base = new BlockPos(1, 1, 3);
        BlockPos target = base.above();
        helper.setBlock(base, Blocks.COBBLESTONE);
        BlockPos chestPosition = helper.absolutePos(CHEST);
        helper.getLevel().removeBlockEntity(chestPosition);
        PlacementRaceChestBlockEntity raced = new PlacementRaceChestBlockEntity(
                chestPosition, helper.getBlockState(CHEST), helper.absolutePos(target));
        helper.getLevel().setBlockEntity(raced);
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));

        ServerPlayer player = mockPlayerWithChannel(helper);
        ItemStack remote = new ItemStack(SsnRegistry.Items.BUILDER_REMOTE.get());
        DimPos.putPos(remote, helper.absolutePos(MASTER), helper.getLevel());
        ItemBuilder.setBlockState(remote, Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        BlockPos absoluteBase = helper.absolutePos(base);
        player.teleportTo(absoluteBase.getX() + 0.5,
                absoluteBase.getY() + 1.5, absoluteBase.getZ() + 0.5);
        remote.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absoluteBase),
                        Direction.UP, absoluteBase, false)));

        helper.assertTrue(raced.wasSpent(), "The simulated race must actually extract one stone");
        helper.assertTrue(helper.getBlockState(target).is(Blocks.COBBLESTONE),
                "The extraction race must have blocked the placement target");
        helper.assertTrue(count(player.getInventory().items, Items.STONE) == 1,
                "SSN-004: stone was debited but not returned to the player after failed placement");
        helper.succeed();
    }

    /**
     * Required positive control for SSN-005: non-stackable vanilla recipe
     * inputs (one honey bottle per crafting slot) should not disappear.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_005_single_honey_per_slot_preserves_glass(GameTestHelper helper) {
        basicNetwork(helper);
        BlockPos request = new BlockPos(1, 1, 2);
        helper.setBlock(request, SsnRegistry.Blocks.REQUEST.get());
        ((TileMain) helper.getBlockEntity(MASTER)).getNetwork().doRefresh(
                new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));

        ServerPlayer player = mockPlayerWithChannel(helper);
        ContainerNetworkCraftingTable menu = new ContainerNetworkCraftingTable(
                1, helper.getLevel(), helper.absolutePos(request), player.getInventory(), player);
        int[] recipeSlots = {0, 1, 3, 4};
        for (int slot : recipeSlots) {
            menu.getCraftMatrix().setItem(slot, new ItemStack(Items.HONEY_BOTTLE));
        }
        menu.slotsChanged(menu.getCraftMatrix());
        helper.assertTrue(count(menu.getCraftMatrix(), Items.HONEY_BOTTLE) == 4,
                "Positive control: expected four honey bottles");
        menu.quickMoveStack(player, 0);

        int made = count(player.getInventory().items, Items.HONEY_BLOCK);
        int honeyLeft = count(menu.getCraftMatrix(), Items.HONEY_BOTTLE)
                + count(player.getInventory().items, Items.HONEY_BOTTLE);
        int glass = count(menu.getCraftMatrix(), Items.GLASS_BOTTLE)
                + count(player.getInventory().items, Items.GLASS_BOTTLE);

        helper.assertTrue(made == 1 && honeyLeft == 0 && glass == 4,
                "Positive control: 4 honey bottles must craft 1 block + 4 glass; got "
                        + made + " blocks, " + honeyLeft + " honey, " + glass + " glass");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_005_stackable_honey_bottle_remainder_conservation(GameTestHelper helper) {
        // Vanilla HONEY_BOTTLE is stackable and returns GLASS_BOTTLE. This
        // reproduces the category without requiring Farmer's Delight.
        TileMain main = basicNetwork(helper);
        BlockPos request = new BlockPos(1, 1, 2);
        helper.setBlock(request, SsnRegistry.Blocks.REQUEST.get());
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));

        ServerPlayer player = mockPlayerWithChannel(helper);
        ContainerNetworkCraftingTable menu = new ContainerNetworkCraftingTable(
                1, helper.getLevel(), helper.absolutePos(request), player.getInventory(), player);

        int[] recipeSlots = {0, 1, 3, 4};
        for (int slot : recipeSlots) {
            menu.getCraftMatrix().setItem(slot, new ItemStack(Items.HONEY_BOTTLE, 2));
        }
        menu.slotsChanged(menu.getCraftMatrix());
        helper.assertTrue(count(menu.getCraftMatrix(), Items.HONEY_BOTTLE) == 8,
                "SSN-005 fixture: all eight input honey bottles must be in the real crafting grid");
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(
                RecipeType.CRAFTING, menu.getCraftMatrix(), helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "SSN-005 fixture: vanilla honey block recipe missing");
        var vanillaRemainders = recipe.orElseThrow().getRemainingItems(menu.getCraftMatrix());
        helper.assertTrue(count(vanillaRemainders, Items.GLASS_BOTTLE) == 4,
                "SSN-005 fixture: vanilla recipe must return 4 glass bottles per craft");
        menu.quickMoveStack(player, 0); // shift-click output

        // NetworkCraftingInventory overrides getItem() and stores its own
        // stackList. Its inherited getItems() accesses a different list:
        // never count through getItems() here, or the test reports false losses.
        List<ItemStack> ground = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(player.blockPosition()).inflate(32))
                .stream().map(ItemEntity::getItem).toList();
        int honeyGrid = count(menu.getCraftMatrix(), Items.HONEY_BOTTLE);
        int bottleGrid = count(menu.getCraftMatrix(), Items.GLASS_BOTTLE);
        int honey = honeyGrid
                + count(player.getInventory().items, Items.HONEY_BOTTLE)
                + count(ground, Items.HONEY_BOTTLE);
        int bottles = bottleGrid
                + count(player.getInventory().items, Items.GLASS_BOTTLE)
                + count(ground, Items.GLASS_BOTTLE);
        int blocks = count(player.getInventory().items, Items.HONEY_BLOCK)
                + count(ground, Items.HONEY_BLOCK);

        helper.assertTrue(blocks >= 1 && blocks <= 2,
                "SSN-005 setup: expected 1-2 honey block crafts from 8 bottles, got " + blocks);
        helper.assertTrue(honey + 4 * blocks == 8 && bottles == 4 * blocks,
                "SSN-005 conservation: 8 honey bottles -> " + blocks
                        + " blocks, " + honey + " unconsumed honey (" + honeyGrid
                        + " in grid), " + bottles + " glass (" + bottleGrid
                        + " in grid), " + ground.size()
                        + " dropped stacks. Expected honey+4*blocks=8; glass=4*blocks.");
        helper.succeed();
    }

    /**
     * SSN-005 exploratory conservation check when only a single output slot
     * is available. Container remainders cannot fit in player inventory,
     * so they must become ItemEntity drops rather than silently vanish.
     *
     * The output slot has 63 honey blocks and can accept exactly one craft.
     * The four input cells contain two honey bottles each (8 total).
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_005_full_player_inventory_preserves_crafting_remainders(GameTestHelper helper) {
        TileMain main = basicNetwork(helper);
        BlockPos request = new BlockPos(1, 1, 2);
        helper.setBlock(request, SsnRegistry.Blocks.REQUEST.get());
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));
        ServerPlayer player = mockPlayerWithChannel(helper);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.getInventory().items.set(0, new ItemStack(Items.HONEY_BLOCK, 63));

        ContainerNetworkCraftingTable menu = new ContainerNetworkCraftingTable(
                1, helper.getLevel(), helper.absolutePos(request), player.getInventory(), player);
        for (int slot : new int[]{0, 1, 3, 4}) {
            menu.getCraftMatrix().setItem(slot, new ItemStack(Items.HONEY_BOTTLE, 2));
        }
        menu.slotsChanged(menu.getCraftMatrix());
        helper.assertTrue(count(menu.getCraftMatrix(), Items.HONEY_BOTTLE) == 8,
                "Full-inventory test setup: expected exactly 8 honey bottles");
        menu.quickMoveStack(player, 0);

        List<ItemStack> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(player.blockPosition()).inflate(32))
                .stream().map(ItemEntity::getItem).toList();
        int honey = count(menu.getCraftMatrix(), Items.HONEY_BOTTLE)
                + count(player.getInventory().items, Items.HONEY_BOTTLE)
                + count(drops, Items.HONEY_BOTTLE);
        int glass = count(menu.getCraftMatrix(), Items.GLASS_BOTTLE)
                + count(player.getInventory().items, Items.GLASS_BOTTLE)
                + count(drops, Items.GLASS_BOTTLE);
        int produced = count(player.getInventory().items, Items.HONEY_BLOCK)
                + count(drops, Items.HONEY_BLOCK) - 63;

        helper.assertTrue(produced == 1 && honey == 4 && glass == 4,
                "SSN-005 full-inventory conservation: crafted=" + produced
                        + ", remaining honey=" + honey + ", returned glass=" + glass
                        + ", dropped stacks=" + drops.size());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_006_failure_in_one_provider_keeps_other_items_visible(GameTestHelper helper) {
        TileMain main = basicNetwork(helper);
        BlockPos chestPos = helper.absolutePos(CHEST);
        helper.getLevel().removeBlockEntity(chestPos);
        helper.getLevel().setBlockEntity(
                new ThrowingChestBlockEntity(chestPos, helper.getBlockState(CHEST)));

        helper.setBlock(new BlockPos(2, 1, 2), SsnRegistry.Blocks.KABEL.get());
        BlockPos secondLink = new BlockPos(3, 1, 2);
        BlockPos healthyChest = new BlockPos(4, 1, 2);
        helper.setBlock(secondLink, SsnRegistry.Blocks.STORAGE_KABEL.get());
        helper.setBlock(healthyChest, Blocks.CHEST);

        TileCableLink brokenCable = (TileCableLink) helper.getBlockEntity(LINK);
        TileCableLink healthyCable = (TileCableLink) helper.getBlockEntity(secondLink);
        healthyCable.setDirection(Direction.EAST);
        healthyCable.setChanged();
        IConnectableLink brokenLink = brokenCable.getCapability(
                StorageNetworkCapabilities.CONNECTABLE_ITEM_STORAGE_CAPABILITY, null).orElse(null);
        IConnectableLink healthyLink = healthyCable.getCapability(
                StorageNetworkCapabilities.CONNECTABLE_ITEM_STORAGE_CAPABILITY, null).orElse(null);
        helper.assertTrue(brokenLink != null && healthyLink != null, "Both storage links must exist");
        brokenLink.setPriority(-100);
        healthyLink.setPriority(100);
        ((ChestBlockEntity) helper.getBlockEntity(healthyChest))
                .setItem(0, new ItemStack(Items.DIAMOND, 7));
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));

        int count = count(main.getNetwork().getSortedStacks(), Items.DIAMOND);
        helper.assertTrue(count == 7,
                "SSN-006: a throwing provider hid 7 diamonds in an unrelated healthy chest");
        helper.succeed();
    }

    /**
     * Build a network-backed server player without GameTestHelper's built-in
     * makeMockServerPlayerInLevel(): that helper invokes PlayerList.placeNewPlayer
     * with an uninitialized connection, which throws before returning on
     * Forge 47.3.0. An EmbeddedChannel must exist *before* any login packets.
     * This isolated test player is not registered in PlayerList.
     */
    private static ServerPlayer mockPlayerWithChannel(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(
                server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "ssn-test-player"));
        Connection channelConnection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(channelConnection);
        player.connection = new ServerGamePacketListenerImpl(server, channelConnection, player);
        return player;
    }

    private static TileMain basicNetwork(GameTestHelper helper) {
        helper.setBlock(MASTER, SsnRegistry.Blocks.MASTER.get());
        helper.setBlock(LINK, SsnRegistry.Blocks.STORAGE_KABEL.get());
        helper.setBlock(CHEST, Blocks.CHEST);
        TileCableLink first = (TileCableLink) helper.getBlockEntity(LINK);
        first.setDirection(Direction.EAST);
        first.setChanged();
        TileMain main = (TileMain) helper.getBlockEntity(MASTER);
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));
        return main;
    }

    private static int count(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
