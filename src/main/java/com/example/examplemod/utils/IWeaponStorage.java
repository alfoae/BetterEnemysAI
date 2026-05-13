package com.example.examplemod.utils;

import net.minecraft.world.item.ItemStack;

public interface IWeaponStorage {
    ItemStack getStoredMelee();

    void setStoredMelee(ItemStack stack);

    ItemStack getStoredRanged();

    void setStoredRanged(ItemStack stack);
}