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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Optional ("known red") tests of real unresolved SSN behaviors.
 * A failure here must be investigated; it does not block the verified
 * SSN-001/002 patch tests. Never report these cases as fixed by this PR.
 */
@GameTestHolder(SsnBugFixes.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SsnExploratoryGameTests {
    private static final BlockPos MASTER = new BlockPos(1, 1, 1);
    private static final BlockPos LINK = new BlockPos(2, 1, 1);
    private static final BlockPos CHEST = new BlockPos(3, 1, 1);

    private SsnExploratoryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80, required = false)
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

    @GameTest(template = "empty", timeoutTicks = 80, required = false)
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
        menu.quickMoveStack(player, 0); // shift-click output

        int honey = count(menu.getCraftMatrix().getItems(), Items.HONEY_BOTTLE)
                + count(player.getInventory().items, Items.HONEY_BOTTLE);
        int bottles = count(menu.getCraftMatrix().getItems(), Items.GLASS_BOTTLE)
                + count(player.getInventory().items, Items.GLASS_BOTTLE);
        int blocks = count(player.getInventory().items, Items.HONEY_BLOCK);

        helper.assertTrue(blocks >= 1 && blocks <= 2,
                "SSN-005 setup: expected 1-2 honey block crafts from 8 bottles, got " + blocks);
        helper.assertTrue(honey + 4 * blocks == 8 && bottles == 4 * blocks,
                "SSN-005 conservation: input 8 honey bottles; made " + blocks
                        + " blocks, remaining " + honey + " honey and " + bottles
                        + " glass. Expected honey+4*blocks=8 and glass=4*blocks.");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, required = false)
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

    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
