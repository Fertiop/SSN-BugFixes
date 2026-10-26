package io.github.fertiop.ssnbugfixes.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Test-only in-world fixture using vanilla CHEST block/entity type.
 * It MUST NOT be used for players' inventories or production operations.
 *
 * It models an otherwise readable storage provider whose real extraction
 * fails after a successful simulation. This exposes non-atomic transfers.
 */
public final class DivergentChestBlockEntity extends ChestBlockEntity {
    private final IItemHandler itemHandler = new ItemStackHandler(1) {
        @Override
        public ItemStack getStackInSlot(int slot) {
            return new ItemStack(Items.STONE, 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return simulate && slot == 0 && amount > 0
                    ? new ItemStack(Items.STONE, 1)
                    : ItemStack.EMPTY;
        }
    };
    private final LazyOptional<IItemHandler> exposed = LazyOptional.of(() -> itemHandler);

    public DivergentChestBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == ForgeCapabilities.ITEM_HANDLER
                ? exposed.cast()
                : super.getCapability(capability, side);
    }
}
