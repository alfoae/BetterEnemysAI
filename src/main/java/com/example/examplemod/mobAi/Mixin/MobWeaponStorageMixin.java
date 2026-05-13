package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.utils.IWeaponStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobWeaponStorageMixin implements IWeaponStorage {
    @Unique
    private ItemStack meleeWeapon = ItemStack.EMPTY;
    @Unique
    private ItemStack rangedWeapon = ItemStack.EMPTY;

    @Override
    public ItemStack getStoredMelee() {
        return meleeWeapon;
    }

    @Override
    public void setStoredMelee(ItemStack stack) {
        this.meleeWeapon = stack;
    }

    @Override
    public ItemStack getStoredRanged() {
        return rangedWeapon;
    }

    @Override
    public void setStoredRanged(ItemStack stack) {
        this.rangedWeapon = stack;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveCustomWeapons(CompoundTag tag, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (!this.meleeWeapon.isEmpty()) tag.put("StoredMelee", this.meleeWeapon.save(mob.registryAccess()));
        if (!this.rangedWeapon.isEmpty()) tag.put("StoredRanged", this.rangedWeapon.save(mob.registryAccess()));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void loadCustomWeapons(CompoundTag tag, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (tag.contains("StoredMelee", Tag.TAG_COMPOUND))
            this.meleeWeapon = ItemStack.parseOptional(mob.registryAccess(), tag.getCompound("StoredMelee"));
        if (tag.contains("StoredRanged", Tag.TAG_COMPOUND))
            this.rangedWeapon = ItemStack.parseOptional(mob.registryAccess(), tag.getCompound("StoredRanged"));
    }
}