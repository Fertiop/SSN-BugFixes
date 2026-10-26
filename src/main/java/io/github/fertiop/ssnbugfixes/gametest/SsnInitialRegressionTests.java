package io.github.fertiop.ssnbugfixes.gametest;

import com.lothrazar.storagenetwork.api.DimPos;
import com.lothrazar.storagenetwork.block.request.TileRequest;
import com.lothrazar.storagenetwork.capability.CapabilityConnectable;
import com.lothrazar.storagenetwork.item.ItemBuilder;
import com.lothrazar.storagenetwork.registry.SsnRegistry;
import com.lothrazar.storagenetwork.registry.StorageNetworkCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Real SSN methods, real Minecraft NBT and registry, not mocks of our patches.
 * Requires the original Simple Storage Network 1.13.2 mod installed.
 *
 * Run first WITHOUT the Mixin patches to establish RED, then WITH the patches
 * to establish GREEN. No such runs have yet been performed.
 */
@GameTestHolder("ssnbugfixes")
@PrefixGameTestTemplate(false)
public final class SsnInitialRegressionTests {

    private SsnInitialRegressionTests() {}

    @GameTest(template = "empty")
    public static void requestTerminalSavesInheritedConnection(GameTestHelper helper) {
        TileRequest terminal = new TileRequest(
                BlockPos.ZERO, SsnRegistry.Blocks.REQUEST.get().defaultBlockState());

        // Assign the same stored connection that TileConnectable normally saves.
        CapabilityConnectable link = terminal.getCapability(
                StorageNetworkCapabilities.CONNECTABLE_CAPABILITY, null).orElseThrow();
        link.setMainPos(new DimPos(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));

        terminal.setAutoFocus(true);
        terminal.setDownwards(true);
        terminal.setJeiSearchSynced(true);

        // Calls actual virtual TileRequest.saveAdditional -> our tail injection.
        CompoundTag saved = terminal.saveWithFullMetadata();
        if (!saved.contains("connectable", 10)
                || !saved.getCompound("connectable").contains("master", 10)) {
            helper.fail("SSN-001: TileRequest failed to persist inherited connectable.master");
            return;
        }
        if (!saved.getBoolean("autoFocus")
                || !saved.getBoolean("storagenetworkdir")
                || !saved.getBoolean("storagenetworkjei")) {
            helper.fail("SSN-001: original terminal UI settings were not preserved");
            return;
        }

        // Round-trip from NBT into a new real SSN tile, not a substituted model.
        TileRequest restored = new TileRequest(BlockPos.ZERO, SsnRegistry.Blocks.REQUEST.get().defaultBlockState());
        restored.load(saved);
        CompoundTag afterLoad = restored.saveWithFullMetadata();
        if (!afterLoad.contains("connectable", 10)
                || !afterLoad.getCompound("connectable").contains("master", 10)) {
            helper.fail("SSN-001: master link did not survive NBT round-trip");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void builderReturnsNullWithoutWorldEvenWithSavedState(GameTestHelper helper) {
        ItemStack remote = new ItemStack(Items.STICK);
        ItemBuilder.setBlockState(remote, Blocks.STONE.defaultBlockState());
        if (ItemBuilder.getBlockState(null, remote) != null) {
            helper.fail("SSN-002: null Level should not decode a block state");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void builderReturnsNullWithoutWorldAndWithoutSavedState(GameTestHelper helper) {
        ItemStack remote = new ItemStack(Items.STICK);
        if (ItemBuilder.getBlockState(null, remote) != null) {
            helper.fail("SSN-002: absent stored blockstate must remain null");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void builderDecodesBlockStateWithRealWorld(GameTestHelper helper) {
        ItemStack remote = new ItemStack(Items.STICK);
        ItemBuilder.setBlockState(remote, Blocks.STONE.defaultBlockState());
        if (ItemBuilder.getBlockState(helper.getLevel(), remote) != Blocks.STONE.defaultBlockState()) {
            helper.fail("SSN-002: non-null Level behavior changed");
            return;
        }
        helper.succeed();
    }
}
