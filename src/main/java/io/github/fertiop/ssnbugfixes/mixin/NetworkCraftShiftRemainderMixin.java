package io.github.fertiop.ssnbugfixes.mixin;

import com.lothrazar.storagenetwork.block.main.TileMain;
import com.lothrazar.storagenetwork.gui.ContainerNetwork;
import com.lothrazar.storagenetwork.gui.NetworkCraftingInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SSN-005: craftShift replaces an entire input stack with a crafting
 * remainder (e.g. two honey bottles become one glass bottle).
 *
 * If a recipe yields a different remainder, consume ONE input rather than
 * discarding the other stack members; return the crafting remainder to the
 * player. When a single input remains, retain upstream's normal behavior.
 *
 * Never use NetworkCraftingInventory.getItems(): it stores crafting inputs
 * in an independent list and implements getItem/setItem itself.
 */
@Mixin(value = ContainerNetwork.class, remap = false)
public abstract class NetworkCraftShiftRemainderMixin {

    @Redirect(
            method = "craftShift(Lnet/minecraft/world/entity/player/Player;Lcom/lothrazar/storagenetwork/block/main/TileMain;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/lothrazar/storagenetwork/gui/NetworkCraftingInventory;setItem(ILnet/minecraft/world/item/ItemStack;)V",
                    remap = false),
            remap = false)
    private void ssnbugfixes$preserveUnconsumedStack(
            NetworkCraftingInventory grid, int index, ItemStack replacement,
            Player player, TileMain main) {
        ItemStack input = grid.getItem(index);
        if (!input.isEmpty() && input.getCount() > 1 && !replacement.isEmpty()) {
            ItemStack craftingRemainder = input.getItem().getCraftingRemainingItem(input);
            if (!craftingRemainder.isEmpty()
                    && ItemStack.isSameItemSameTags(replacement, craftingRemainder)) {
                // Matrix retains all ingredients except the one consumed.
                input.shrink(1);
                grid.setItem(index, input);

                // Preserve the resulting container item even when the player's
                // inventory is full; Inventory.add mutates the argument to the
                // uninserted remainder.
                ItemStack toReturn = replacement.copy();
                player.getInventory().add(toReturn);
                if (!toReturn.isEmpty()) {
                    player.drop(toReturn, false);
                }
                return;
            }
        }
        grid.setItem(index, replacement);
    }
}
