package com.example.dispensive_cauldrons.mixin;

import net.minecraft.block.*;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.potion.Potions;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


//   protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
@Mixin(ItemDispenserBehavior.class)
public abstract class ItemDispenserBehaviourMixin {
    @Shadow
    protected abstract void addStackOrSpawn(BlockPointer pointer, ItemStack stack);

    @Shadow
    protected abstract ItemStack decrementStackWithRemainder(BlockPointer pointer, ItemStack stack, ItemStack remainder);

    public final Logger LOGGER = LoggerFactory.getLogger("meow");


    @Unique
    private static Item getBucketType(AbstractCauldronBlock cauldronBlock, BlockState state) {
        if(cauldronBlock instanceof CauldronBlock) {
            return Items.BUCKET;
        }
        else if (cauldronBlock instanceof LeveledCauldronBlock leveledCauldronBlock) {
            if(state.isOf(Blocks.WATER_CAULDRON)) {
                return Items.WATER_BUCKET;
            }
            else { //Blocks.POWDER_SNOW_CAULDRON
                return Items.POWDER_SNOW_BUCKET;
            }
        }
        else{
            return Items.LAVA_BUCKET;
        }
    }

    @Unique
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

    @Unique
    private static BlockState getCauldronBlockState(Fluid fluid) {
        if(fluid == Fluids.EMPTY) {
            return Blocks.CAULDRON.getDefaultState();
        }
        else if (fluid == Fluids.WATER) {
            return Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, LeveledCauldronBlock.MAX_LEVEL);
        }
        else {
            return Blocks.LAVA_CAULDRON.getDefaultState();
        }
    }

    @Unique
    private static int getSlot(DispenserBlockEntity dispenser, ItemStack stack) {
        for (int i = 0; i < dispenser.size(); i++){
            if (ItemStack.areItemsAndComponentsEqual(stack, dispenser.getStack(i))){
                return i;
            }
        }
        return -1;
    }

	@Inject(at = @At("HEAD"), method = "dispenseSilently", cancellable = true)
	public void dispenseSilentlyMixin(BlockPointer pointer, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        World world = pointer.world();
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
                    stack.decrement(1);
                    ItemStack bucket = new ItemStack(getBucketType(cauldron, target), 1);
                    if(stack.isEmpty()){
                        stack = bucket.copy(); //fuckin idk, it would just delete the bucket if the stack becomes empty
                    }

                    this.addStackOrSpawn(pointer, bucket);

                    world.setBlockState(targetPos, Blocks.CAULDRON.getDefaultState(), 3);
                }
            }
            else{
                if(getCauldronFluid(cauldron) == bucketItem.getFluid() || getCauldronFluid(cauldron) == Fluids.EMPTY) {
                    stack = new ItemStack(Items.BUCKET);

                    world.setBlockState(targetPos, getCauldronBlockState(bucketItem.getFluid()), 3);
                }
            }
            cir.setReturnValue(stack);
            cir.cancel();
        }
        else if(stack.getItem() instanceof PowderSnowBucketItem powderSnowBucketItem){  //filling cauldron with powder snow bucket
            if(getCauldronFluid(cauldron) == Fluids.EMPTY) {
                stack = new ItemStack(Items.BUCKET);

                world.setBlockState(targetPos, Blocks.POWDER_SNOW_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, LeveledCauldronBlock.MAX_LEVEL), 3);
            }

            cir.setReturnValue(stack);
            cir.cancel();
        }
        else if (stack.getItem() instanceof GlassBottleItem glassBottleItem){   //emptying cauldron with glass bottle
            if(getCauldronFluid(cauldron) == Fluids.WATER) {
                stack.decrement(1);
                ItemStack waterPotionItem = PotionContentsComponent.createStack(Items.POTION, Potions.WATER);
                if(stack.isEmpty()){
                    stack = waterPotionItem.copy();    //fuckin idk, it would just delete the water bottle if the stack becomes empty
                }

                this.addStackOrSpawn(pointer, waterPotionItem);

                int waterLevel = target.get(LeveledCauldronBlock.LEVEL) - 1;
                if(waterLevel <= 0){
                    world.setBlockState(targetPos, Blocks.CAULDRON.getDefaultState());
                }
                else{
                    world.setBlockState(targetPos, target.with(LeveledCauldronBlock.LEVEL, waterLevel));
                }
            }
            cir.setReturnValue(stack);
            cir.cancel();
        }
        else if(stack.getItem() instanceof PotionItem potionItem){  //filling cauldron with water bottle
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if(contents == null){
                return;
            }
            if(contents.potion().orElse(Potions.AWKWARD) == Potions.WATER){
                if(getCauldronFluid(cauldron) == Fluids.EMPTY ||  getCauldronFluid(cauldron) == Fluids.WATER) {
                    int waterLevel = 1;
                    if(cauldron instanceof LeveledCauldronBlock){
                        waterLevel = target.get(LeveledCauldronBlock.LEVEL) + 1;
                    }
                    //TODO: do some shit here with items
                    if(waterLevel > 3){
                        waterLevel = 3;
                    }
                    world.setBlockState(targetPos, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, waterLevel));
                }
            }
            cir.setReturnValue(stack);
            cir.cancel();
        }
        //now do water bottles
	}
}