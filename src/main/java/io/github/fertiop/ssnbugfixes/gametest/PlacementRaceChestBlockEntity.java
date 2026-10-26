package io.github.fertiop.ssnbugfixes.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Test-only race simulation for SSN-004. Real extraction succeeds, but the
 * provider modifies the intended placement cell at the moment of debit.
 * A safe builder must return the consumed material if placement then fails.
 */
public final class PlacementRaceChestBlockEntity extends ChestBlockEntity {
    private final BlockPos blockedPosition;
    private boolean spent;
    private final IItemHandler handler = new ItemStackHandler(1) {
        @Override
        public ItemStack getStackInSlot(int slot) {
            return spent || slot != 0 ? ItemStack.EMPTY : new ItemStack(Items.STONE);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || spent) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                spent = true;
                if (level != null) {
                    level.setBlock(blockedPosition, Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
            return new ItemStack(Items.STONE);
        }
    };
    private final LazyOptional<IItemHandler> exposed = LazyOptional.of(() -> handler);

    public PlacementRaceChestBlockEntity(BlockPos position, BlockState state, BlockPos blockedPosition) {
        super(position, state);
        this.blockedPosition = blockedPosition;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == ForgeCapabilities.ITEM_HANDLER
                ? exposed.cast()
                : super.getCapability(capability, side);
    }

    public boolean wasSpent() {
        return spent;
    }
}
