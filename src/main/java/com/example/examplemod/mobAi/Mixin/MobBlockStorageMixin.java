package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.utils.IMobBlockStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Mob.class)
public abstract class MobBlockStorageMixin implements IMobBlockStorage {

    @Unique
    private final List<ItemStack> dugBlocks = new ArrayList<>();

    @Override
    public List<ItemStack> getDugBlocks() {
        return this.dugBlocks;
    }

    @Override
    public void addDugBlock(ItemStack stack) {
        if (stack.isEmpty()) return;

        for (ItemStack existing : this.dugBlocks) {
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < existing.getMaxStackSize()) {
                int room = existing.getMaxStackSize() - existing.getCount();
                int toMove = Math.min(room, stack.getCount());
                existing.grow(toMove);
                stack.shrink(toMove);
                if (stack.isEmpty()) return;
            }
        }
        if (!stack.isEmpty()) {
            this.dugBlocks.add(stack.copy());
        }
    }

    @Override
    public void clearDugBlocks() {
        this.dugBlocks.clear();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void examplemod$saveDugBlocks(CompoundTag tag, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (this.dugBlocks.isEmpty()) return;

        ListTag list = new ListTag();
        for (ItemStack stack : this.dugBlocks) {
            if (!stack.isEmpty()) {
                list.add(stack.save(mob.registryAccess()));
            }
        }
        tag.put("DugBlocks", list);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void examplemod$loadDugBlocks(CompoundTag tag, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        this.dugBlocks.clear();
        if (tag.contains("DugBlocks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("DugBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(mob.registryAccess(), list.getCompound(i));
                if (!stack.isEmpty()) {
                    this.dugBlocks.add(stack);
                }
            }
        }
    }
}
