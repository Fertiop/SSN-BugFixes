package io.github.fertiop.ssnbugfixes.gametest;

import com.lothrazar.storagenetwork.api.DimPos;
import com.lothrazar.storagenetwork.api.IConnectable;
import com.lothrazar.storagenetwork.block.cable.link.TileCableLink;
import com.lothrazar.storagenetwork.block.main.TileMain;
import com.lothrazar.storagenetwork.block.request.TileRequest;
import com.lothrazar.storagenetwork.capability.handler.ItemStackMatcher;
import com.lothrazar.storagenetwork.item.ItemBuilder;
import com.lothrazar.storagenetwork.registry.SsnRegistry;
import com.lothrazar.storagenetwork.registry.StorageNetworkCapabilities;
import io.github.fertiop.ssnbugfixes.SsnBugFixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Behavioral, not merely structural, checks against real SSN block entities.
 * Vanilla SSN is a runtime mod dependency for these tests.
 *
 * SSN-005 (UI shift-crafting) and SSN-006 (malicious inventory capability)
 * additionally need isolated test fixtures; see docs/TEST_MATRIX.md.
 */
@GameTestHolder(SsnBugFixes.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SsnRegressionGameTests {
    private static final BlockPos MASTER = new BlockPos(1, 1, 1);
    private static final BlockPos LINK = new BlockPos(2, 1, 1);
    private static final BlockPos CHEST = new BlockPos(3, 1, 1);
    private static final BlockPos CONNECTOR = new BlockPos(2, 1, 2);
    private static final BlockPos SECOND_LINK = new BlockPos(3, 1, 2);

    private SsnRegressionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ssn_001_request_saves_connectable_and_ui_fields(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, SsnRegistry.Blocks.REQUEST.get());
        TileRequest terminal = (TileRequest) helper.getBlockEntity(pos);
        IConnectable link = terminal.getCapability(
                StorageNetworkCapabilities.CONNECTABLE_CAPABILITY, null).orElse(null);
        helper.assertTrue(link != null, "SSN terminal must expose its connectable capability");

        BlockPos controller = helper.absolutePos(pos.offset(0, 0, 1));
        link.setPos(new DimPos(helper.getLevel(), helper.absolutePos(pos)));
        link.setMainPos(new DimPos(helper.getLevel(), controller));
        terminal.setAutoFocus(false);
        terminal.setDownwards(true);
        terminal.setJeiSearchSynced(true);

        CompoundTag saved = terminal.saveWithoutMetadata();
        helper.assertTrue(saved.contains("connectable", Tag.TAG_COMPOUND),
                "SSN-001: connectable NBT missing after save");
        helper.assertTrue(saved.getBoolean("storagenetworkdir"), "Direction setting lost");
        helper.assertTrue(saved.getBoolean("storagenetworkjei"), "JEI setting lost");
        helper.assertTrue(!saved.getBoolean("autoFocus"), "Auto-focus setting lost");

        TileRequest loaded = new TileRequest(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.load(saved);
        helper.assertTrue(loaded.getMain() != null, "Main position lost on NBT load");
        helper.assertTrue(controller.equals(loaded.getMain().getBlockPos()),
                "Loaded network controller position changed");
        helper.assertTrue(loaded.isDownwards(), "Direction changed on NBT reload");
        helper.assertTrue(loaded.isJeiSearchSynced(), "JEI changed on NBT reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ssn_002_null_level_with_saved_blockstate(GameTestHelper helper) {
        ItemStack remote = new ItemStack(SsnRegistry.Items.BUILDER_REMOTE.get());
        ItemBuilder.setBlockState(remote, Blocks.STONE.defaultBlockState());
        helper.assertTrue(ItemBuilder.getBlockState(null, remote) == null,
                "SSN-002: null Level must not throw or resolve a state");
        helper.assertTrue(ItemBuilder.getBlockState(helper.getLevel(), remote).is(Blocks.STONE),
                "Non-null Level must still decode the saved block state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ssn_002_null_level_without_saved_blockstate(GameTestHelper helper) {
        ItemStack remote = new ItemStack(SsnRegistry.Items.BUILDER_REMOTE.get());
        helper.assertTrue(ItemBuilder.getBlockState(null, remote) == null,
                "No saved block state must still resolve to null");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_003_two_links_one_inventory_not_double_counted(GameTestHelper helper) {
        NetworkFixture fixture = network(helper, true);
        int amount = fixture.main.getNetwork().getAmount(
                new ItemStackMatcher(new ItemStack(Items.DIAMOND), false, false));
        helper.assertTrue(amount == 17,
                "SSN 1.13.2 must dedupe same physical chest: expected 17, got " + amount);
        helper.assertTrue(fixture.chest.getItem(0).getCount() == 17,
                "Querying stacks must not mutate the backing chest");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_004_simulate_then_extract_conserves_items(GameTestHelper helper) {
        NetworkFixture fixture = network(helper, false);
        ItemStackMatcher matcher = new ItemStackMatcher(new ItemStack(Items.DIAMOND), false, false);
        ItemStack simulated = fixture.main.request(matcher, 1, true);
        helper.assertTrue(simulated.getCount() == 1, "Simulation must see one diamond");
        helper.assertTrue(fixture.chest.getItem(0).getCount() == 17,
                "Simulation unexpectedly consumed a diamond");
        ItemStack extracted = fixture.main.request(matcher, 1, false);
        helper.assertTrue(extracted.getCount() == 1 && extracted.is(Items.DIAMOND),
                "Real request must return exactly one diamond");
        helper.assertTrue(fixture.chest.getItem(0).getCount() == 16,
                "Network/chest conservation failed after one request");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ssn_004_nbt_variants_do_not_cross_extract(GameTestHelper helper) {
        NetworkFixture fixture = network(helper, false);
        ItemStack red = new ItemStack(Items.DIAMOND, 5);
        red.getOrCreateTag().putString("variant", "red");
        ItemStack blue = new ItemStack(Items.DIAMOND, 3);
        blue.getOrCreateTag().putString("variant", "blue");

        fixture.chest.setItem(0, red.copy());
        fixture.chest.setItem(1, blue.copy());
        fixture.chest.setChanged();
        ItemStackMatcher matcher = new ItemStackMatcher(red.copy(), false, true);

        ItemStack simulated = fixture.main.request(matcher, 2, true);
        helper.assertTrue(simulated.getCount() == 2
                        && simulated.getOrCreateTag().getString("variant").equals("red"),
                "NBT-aware simulation must return two red diamonds");
        helper.assertTrue(fixture.chest.getItem(0).getCount() == 5,
                "NBT simulation must not change physical inventory");

        ItemStack extracted = fixture.main.request(matcher, 2, false);
        helper.assertTrue(extracted.getCount() == 2
                        && extracted.getOrCreateTag().getString("variant").equals("red"),
                "NBT-aware extraction returned the wrong stack");
        helper.assertTrue(fixture.chest.getItem(0).getCount() == 3,
                "Wrong remaining amount for red variant");
        helper.assertTrue(fixture.chest.getItem(1).getCount() == 3
                        && fixture.chest.getItem(1).getOrCreateTag().getString("variant").equals("blue"),
                "NBT-aware request must not touch blue variant");
        helper.succeed();
    }

    private static NetworkFixture network(GameTestHelper helper, boolean duplicateLink) {
        helper.setBlock(MASTER, SsnRegistry.Blocks.MASTER.get());
        helper.setBlock(LINK, SsnRegistry.Blocks.STORAGE_KABEL.get());
        helper.setBlock(CHEST, Blocks.CHEST);

        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
        chest.setChanged();

        TileCableLink first = (TileCableLink) helper.getBlockEntity(LINK);
        first.setDirection(Direction.EAST);
        first.setChanged();

        if (duplicateLink) {
            helper.setBlock(CONNECTOR, SsnRegistry.Blocks.KABEL.get());
            helper.setBlock(SECOND_LINK, SsnRegistry.Blocks.STORAGE_KABEL.get());
            TileCableLink second = (TileCableLink) helper.getBlockEntity(SECOND_LINK);
            second.setDirection(Direction.NORTH);
            second.setChanged();
        }

        TileMain main = (TileMain) helper.getBlockEntity(MASTER);
        main.getNetwork().doRefresh(new DimPos(helper.getLevel(), helper.absolutePos(MASTER)));
        return new NetworkFixture(main, chest);
    }

    private record NetworkFixture(TileMain main, ChestBlockEntity chest) {}
}
