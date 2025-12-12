package com.example.dispensive_cauldrons.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.BottleItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


//   protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
@Mixin(DefaultDispenseItemBehavior.class)
public abstract class ItemDispenserBehaviourMixin {
    @Shadow
    protected abstract void addToInventoryOrDispense(BlockSource pointer, ItemStack stack);

    @Shadow
    protected abstract ItemStack consumeWithRemainder(BlockSource pointer, ItemStack stack, ItemStack remainder);

    public final Logger LOGGER = LoggerFactory.getLogger("meow");


    @Unique
    private static Item getBucketType(AbstractCauldronBlock cauldronBlock, BlockState state) {
        if(cauldronBlock instanceof CauldronBlock) {
            return Items.BUCKET;
        }
        else if (cauldronBlock instanceof LayeredCauldronBlock leveledCauldronBlock) {
            if(state.is(Blocks.WATER_CAULDRON)) {
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
        else if (cauldronBlock instanceof LayeredCauldronBlock) {
            return Fluids.WATER;
        }
        else{
            return Fluids.LAVA;
        }
    }

    @Unique
    private static BlockState getCauldronBlockState(Fluid fluid) {
        if(fluid == Fluids.EMPTY) {
            return Blocks.CAULDRON.defaultBlockState();
        }
        else if (fluid == Fluids.WATER) {
            return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        }
        else {
            return Blocks.LAVA_CAULDRON.defaultBlockState();
        }
    }

    @Unique
    private static int getSlot(DispenserBlockEntity dispenser, ItemStack stack) {
        for (int i = 0; i < dispenser.getContainerSize(); i++){
            if (ItemStack.isSameItemSameComponents(stack, dispenser.getItem(i))){
                return i;
            }
        }
        return -1;
    }

	@Inject(at = @At("HEAD"), method = "execute", cancellable = true)
	public void dispenseSilentlyMixin(BlockSource pointer, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        Level world = pointer.level();
        if (world.isClientSide()){
            return;
        }
        BlockPos pos = pointer.pos();
        BlockState dispenserState = world.getBlockState(pos);
        DispenserBlockEntity dispenser = (DispenserBlockEntity)world.getBlockEntity(pos);

        BlockPos targetPos = pos.relative(dispenserState.getValue(DispenserBlock.FACING), 1);
        BlockState target = world.getBlockState(targetPos);

        if (!(target.getBlock() instanceof AbstractCauldronBlock cauldron)) {
            return;
        }

        if(stack.getItem() instanceof BucketItem bucketItem){
            if(bucketItem.getContent() == Fluids.EMPTY){
                if(cauldron.isFull(target)){
                    ItemStack bucket = new ItemStack(getBucketType(cauldron, target), 1);
                    if(stack.getCount() == 1){
                        stack = bucket;
                    }
                    else{
                        stack.shrink(1);
                        this.addToInventoryOrDispense(pointer, bucket);
                    }

                    world.setBlock(targetPos, Blocks.CAULDRON.defaultBlockState(), 3);
                }
            }
            else{
                if(getCauldronFluid(cauldron) == bucketItem.getContent() || getCauldronFluid(cauldron) == Fluids.EMPTY) {
                    stack = new ItemStack(Items.BUCKET);

                    world.setBlock(targetPos, getCauldronBlockState(bucketItem.getContent()), 3);
                }
            }
            cir.setReturnValue(stack);
            cir.cancel();
        }
        else if(stack.getItem() instanceof SolidBucketItem powderSnowBucketItem){  //filling cauldron with powder snow bucket
            if(getCauldronFluid(cauldron) == Fluids.EMPTY) {
                stack = new ItemStack(Items.BUCKET);

                world.setBlock(targetPos, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL), 3);
            }

            cir.setReturnValue(stack);
            cir.cancel();
        }
        else if (stack.getItem() instanceof BottleItem glassBottleItem){   //emptying cauldron with glass bottle
            if(getCauldronFluid(cauldron) == Fluids.WATER) {
                ItemStack waterPotionItem = PotionContents.createItemStack(Items.POTION, Potions.WATER);

                if(stack.getCount() == 1){
                    stack = waterPotionItem;
                }
                else{
                    stack.shrink(1);
                    this.addToInventoryOrDispense(pointer, waterPotionItem);
                }

                int waterLevel = target.getValue(LayeredCauldronBlock.LEVEL) - 1;
                if(waterLevel <= 0){
                    world.setBlockAndUpdate(targetPos, Blocks.CAULDRON.defaultBlockState());
                }
                else{
                    world.setBlockAndUpdate(targetPos, target.setValue(LayeredCauldronBlock.LEVEL, waterLevel));
                }
            }
            cir.setReturnValue(stack);
            cir.cancel();
        }
        else if(stack.getItem() instanceof PotionItem potionItem){  //filling cauldron with water bottle
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if(contents == null){
                return;
            }
            if(contents.potion().orElse(Potions.AWKWARD) == Potions.WATER){
                if(getCauldronFluid(cauldron) == Fluids.EMPTY ||  getCauldronFluid(cauldron) == Fluids.WATER) {
                    ItemStack bottleItem = new ItemStack(Items.GLASS_BOTTLE, 1);
                    if(stack.getCount() == 1){
                        stack = bottleItem;
                    }
                    else{
                        stack.shrink(1);
                        this.addToInventoryOrDispense(pointer, bottleItem);
                    }

                    int waterLevel = 1;
                    if(cauldron instanceof LayeredCauldronBlock){
                        waterLevel = target.getValue(LayeredCauldronBlock.LEVEL) + 1;
                    }
                    //TODO: do some shit here with items
                    if(waterLevel > 3){
                        waterLevel = 3;
                    }
                    world.setBlockAndUpdate(targetPos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, waterLevel));
                }
            }
            cir.setReturnValue(stack);
            cir.cancel();
        }
        //now do water bottles
	}
}