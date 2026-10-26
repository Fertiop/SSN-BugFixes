package io.github.fertiop.ssnbugfixes.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** A purposely broken inventory capability for SSN-006 isolation testing. */
public final class ThrowingChestBlockEntity extends ChestBlockEntity {
    private final IItemHandler broken = new ItemStackHandler(1) {
        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            throw new IllegalStateException("Intentional SSN-006 provider fault");
        }
    };
    private final LazyOptional<IItemHandler> exposed = LazyOptional.of(() -> broken);

    public ThrowingChestBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == ForgeCapabilities.ITEM_HANDLER
                ? exposed.cast()
                : super.getCapability(capability, side);
    }
}
