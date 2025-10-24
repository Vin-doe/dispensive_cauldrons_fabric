package com.example.dispensive_cauldrons.mixin;

import net.minecraft.block.*;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


//   protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
@Mixin(ItemDispenserBehavior.class)
public class ItemDispenserBehaviourMixin {
    public final Logger LOGGER = LoggerFactory.getLogger("meow");


    private static Item getBucketType(AbstractCauldronBlock cauldronBlock) {
        if(cauldronBlock instanceof CauldronBlock) {
            return Items.BUCKET;
        }
        else if (cauldronBlock instanceof LeveledCauldronBlock){
            return Items.WATER_BUCKET;
        }
        else{
            return Items.LAVA_BUCKET;
        }
    }

    private static Fluid getCauldronFluid(AbstractCauldronBlock cauldronBlock) {
        if(cauldronBlock instanceof CauldronBlock) {
            return Fluids.EMPTY;
        }
        else if (cauldronBlock instanceof LeveledCauldronBlock) {
            return Fluids.WATER;
        }
        else{
            return Fluids.LAVA;
        }
    }

    private static BlockState getCauldronBlockState(Fluid fluid) {
        if(fluid == Fluids.EMPTY) {
            return Blocks.CAULDRON.getDefaultState();
        }
        else if (fluid == Fluids.WATER) {
            return Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, LeveledCauldronBlock.MAX_LEVEL);
        }
        else{
            return Blocks.LAVA_CAULDRON.getDefaultState();
        }
    }

	@Inject(at = @At("HEAD"), method = "dispenseSilently", cancellable = true)
	public void DispenseSilentlyMixin(BlockPointer pointer, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        ServerWorld world = pointer.world();
        if (world.isClient()){
            return;
        }
        BlockPos pos = pointer.pos();

        BlockState dispenserState = world.getBlockState(pos);
        DispenserBlockEntity dispenser = (DispenserBlockEntity)world.getBlockEntity(pos);

        BlockPos targetPos = pos.offset(dispenserState.get(DispenserBlock.FACING), 1);
        BlockState target = world.getBlockState(targetPos);

        if (!(target.getBlock() instanceof AbstractCauldronBlock cauldron)) {
            return;
        }

        if(stack.getItem() instanceof BucketItem bucketItem){
            if(bucketItem.getFluid() == Fluids.EMPTY){
                if(cauldron.isFull(target)){
                    stack = new ItemStack(getBucketType(cauldron));
                    world.setBlockState(targetPos, Blocks.CAULDRON.getDefaultState(), 3);
                }
            }
            else{
                if(getCauldronFluid(cauldron) == bucketItem.getFluid() || getCauldronFluid(cauldron) == Fluids.EMPTY) {
                    stack = new ItemStack(Items.BUCKET);
                    BlockState state = getCauldronBlockState(bucketItem.getFluid());

                    world.setBlockState(targetPos, getCauldronBlockState(bucketItem.getFluid()), 3);
                }
            }
            cir.setReturnValue(stack);
            cir.cancel();
        }
        //now do water bottles
	}
}