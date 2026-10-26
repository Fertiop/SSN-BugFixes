package io.github.fertiop.ssnbugfixes.mixin;

import com.lothrazar.storagenetwork.api.DimPos;
import com.lothrazar.storagenetwork.api.EnumStorageDirection;
import com.lothrazar.storagenetwork.api.IConnectable;
import com.lothrazar.storagenetwork.api.IItemStackMatcher;
import com.lothrazar.storagenetwork.capability.CapabilityConnectableLink;
import com.lothrazar.storagenetwork.capability.handler.FilterItemStackHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

/**
 * SSN-004 root-cause correction. Stock SSN creates firstMatchedStack from a
 * simulated extraction. If the REAL handler extraction returns EMPTY, the
 * original method's extractCount is 0, yet it still returns the simulated
 * firstMatchedStack without resetting its count.
 *
 * This replacement preserves the original selection/filtering/aggregation
 * algorithm, with two safeguards: never report a simulated-only extraction
 * as committed, and never dereference a null third-party extraction result.
 */
@Mixin(value = CapabilityConnectableLink.class, remap = false)
public abstract class CapabilityLinkCommittedExtractionMixin {

    @Shadow @Final public IConnectable connectable;
    @Shadow private EnumStorageDirection filterDirection;
    @Shadow private Direction inventoryFace;
    @Shadow private FilterItemStackHandler filters;

    /**
     * @author Fertiop / SSN-BugFixes
     * @reason Return only the quantity actually extracted by real handlers
     * instead of falsely reporting the preliminary simulation result.
     */
    @Overwrite
    public ItemStack extractStack(IItemStackMatcher matcher, int size, boolean simulate) {
        if (size <= 0 || matcher == null || filterDirection == EnumStorageDirection.IN
                || inventoryFace == null || connectable == null || connectable.getPos() == null) {
            return ItemStack.EMPTY;
        }
        DimPos inventory = connectable.getPos().offset(inventoryFace);
        IItemHandler handler = inventory.getCapability(
                ForgeCapabilities.ITEM_HANDLER, inventoryFace.getOpposite());
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        ItemStack firstMatchedStack = ItemStack.EMPTY;
        int remaining = size;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack visible = handler.extractItem(slot, remaining, true);
            if (visible == null || visible.isEmpty() || filters.isStackFiltered(visible)) {
                continue;
            }
            if (firstMatchedStack.isEmpty()) {
                if (!matcher.match(visible)) {
                    continue;
                }
                firstMatchedStack = visible.copy();
            }
            else if (!ItemHandlerHelper.canItemStacksStack(firstMatchedStack, visible)) {
                continue;
            }
            int toExtract = Math.min(visible.getCount(), remaining);
            ItemStack actuallyExtracted = handler.extractItem(slot, toExtract, simulate);
            if (actuallyExtracted != null && !actuallyExtracted.isEmpty()) {
                remaining -= Math.min(actuallyExtracted.getCount(), remaining);
            }
        }
        int count = size - remaining;
        // Critical difference from SSN 1.13.2: original returns the simulated
        // stack here even if not a single item was extracted.
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        firstMatchedStack.setCount(count);
        return firstMatchedStack;
    }
}
