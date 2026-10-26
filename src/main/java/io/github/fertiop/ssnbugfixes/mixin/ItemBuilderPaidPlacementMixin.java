package io.github.fertiop.ssnbugfixes.mixin;

import com.lothrazar.storagenetwork.api.DimPos;
import com.lothrazar.storagenetwork.block.main.TileMain;
import com.lothrazar.storagenetwork.capability.handler.ItemStackMatcher;
import com.lothrazar.storagenetwork.item.ItemBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SSN-004: the original builder places a block after checking simulated
 * availability, then ignores the result of real extraction. A dynamic
 * inventory can allow simulation but refuse extraction, creating free blocks.
 *
 * The changed order is: simulate (SSN code) -> actual debit -> place ->
 * compensate on failed placement. Debited materials are returned to the
 * player on placement failure (inventory first, then dropped nearby), rather
 * than retried into a possibly unreliable third-party network.
 */
@Mixin(value = ItemBuilder.class, remap = false)
public abstract class ItemBuilderPaidPlacementMixin {

    @Invoker(value = "placeStateSafe", remap = false)
    protected abstract boolean ssnbugfixes$invokeOriginalPlacement(
            Level world, Player player, BlockPos position, BlockState state);

    @Redirect(
            method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE",
                    target = "Lcom/lothrazar/storagenetwork/item/ItemBuilder;placeStateSafe(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
                    remap = false),
            remap = false)
    private boolean ssnbugfixes$debitBeforePlacement(
            ItemBuilder builder, Level world, Player player, BlockPos position,
            BlockState block) {
        if (player == null || world.isClientSide || world.getServer() == null) {
            return false;
        }
        ItemStack remote = player.getItemInHand(InteractionHand.MAIN_HAND);
        DimPos stored = DimPos.getPosStored(remote);
        if (stored == null) {
            return false;
        }
        ServerLevel sourceWorld = DimPos.stringDimensionLookup(stored.getDimension(), world.getServer());
        if (sourceWorld == null) {
            return false;
        }
        BlockEntity source = sourceWorld.getBlockEntity(stored.getBlockPos());
        if (!(source instanceof TileMain network)) {
            return false;
        }
        // Extraction must be committed BEFORE the world changes; one material
        // is the only acceptable amount to pay for one block.
        ItemStack material = network.request(
                new ItemStackMatcher(new ItemStack(block.getBlock()), false, false), 1, false);
        if (material.isEmpty()) {
            return false;
        }
        if (material.getCount() != 1 || !material.is(block.getBlock().asItem())) {
            ssnbugfixes$returnToPlayer(player, material);
            return false;
        }
        boolean placed;
        try {
            placed = ssnbugfixes$invokeOriginalPlacement(world, player, position, block);
        }
        catch (RuntimeException exception) {
            ssnbugfixes$returnToPlayer(player, material);
            throw exception;
        }
        if (!placed) {
            ssnbugfixes$returnToPlayer(player, material);
        }
        return placed;
    }

    @Redirect(
            method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE",
                    target = "Lcom/lothrazar/storagenetwork/block/main/TileMain;request(Lcom/lothrazar/storagenetwork/capability/handler/ItemStackMatcher;IZ)Lnet/minecraft/world/item/ItemStack;",
                    ordinal = 1, remap = false),
            remap = false)
    private ItemStack ssnbugfixes$avoidDoubleDebit(
            TileMain main, ItemStackMatcher matcher, int count, boolean simulate) {
        // The successful call above already committed exactly one material.
        return ItemStack.EMPTY;
    }

    private static void ssnbugfixes$returnToPlayer(Player player, ItemStack material) {
        ItemStack remainder = material.copy();
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }
}
