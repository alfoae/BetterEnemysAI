package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBrake_N_Build;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Глобальний (СЕРВЕРНИЙ, не per-гравець і не per-вимір) тір швидкості копання мобів, прив'язаний
 * до досягнень. Один раз розблокований тір — назавжди для всього серверу: "На сервері де багато
 * людей моб всеодно прокачуватиметься даже якщо достижение получить хтось 1" — саме тому
 * прив'язано до {@link MinecraftServer#getDataStorage()}, а не до конкретного виміру чи гравця.
 * Монотонний — тір ніколи не зменшується.
 * <p>
 * Час на блок — ПРИБЛИЗНІ, навмисно "трохи повільніше" за відповідний ванільний кайл на
 * булижнику (hardness 2.0): wood≈30 тіків, stone≈15, iron≈10, diamond≈7.5, netherite≈6.7 —
 * див. коментарі нижче. Не точна симуляція ванільної формули (яка залежить від твердості
 * КОНКРЕТНОГО блоку) — свідоме спрощення, бо мобу однаково доводиться копати різні блоки.
 */
public class MiningTierData extends SavedData {

    // ID досягнень, які підвищують тір (перевіряються в ModAdvancementEvents).
    public static final ResourceLocation ADVANCEMENT_STONE = ResourceLocation.withDefaultNamespace("story/upgrade_tools");
    public static final ResourceLocation ADVANCEMENT_IRON = ResourceLocation.withDefaultNamespace("story/iron_tools");
    public static final ResourceLocation ADVANCEMENT_DIAMOND = ResourceLocation.withDefaultNamespace("story/mine_diamond");
    public static final ResourceLocation ADVANCEMENT_NETHERITE = ResourceLocation.withDefaultNamespace("nether/obtain_ancient_debris");
    // Тіки на розколювання ОДНОГО блоку на кожному тірі — "трохи повільніше" за відповідний
    // ванільний кайл на булижнику (тверд. 2.0). Тюнінгові значення, можна міняти сміливо.
    private static final int[] TICKS_PER_BLOCK_BY_TIER = {
            35, // 0: трохи повільніше дерев'яного (ванільний ~30 тіків)
            18, // 1: трохи повільніше кам'яного (ванільний ~15)
            12, // 2: трохи повільніше залізного (ванільний ~10)
            9,  // 3: трохи повільніше алмазного (ванільний ~7.5)
            7   // 4: трохи повільніше незеритового (ванільний ~6.7)
    };
    public static final SavedData.Factory<MiningTierData> FACTORY =
            new SavedData.Factory<>(MiningTierData::new, MiningTierData::load, null);
    private int tier = 0;

    public static MiningTierData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "betterenemysai_mining_tier");
    }

    private static MiningTierData load(CompoundTag tag, HolderLookup.Provider registries) {
        MiningTierData data = new MiningTierData();
        data.tier = Math.min(Math.max(tag.getInt("Tier"), 0), TICKS_PER_BLOCK_BY_TIER.length - 1);
        return data;
    }

    public int getTier() {
        return this.tier;
    }

    public int getTicksPerBlock() {
        return TICKS_PER_BLOCK_BY_TIER[this.tier];
    }

    /**
     * @return true, якщо тір реально піднявся (для лога/дебагу)
     */
    public boolean tryUpgrade(ResourceLocation advancementId) {
        int newTier = this.tier;
        if (advancementId.equals(ADVANCEMENT_STONE)) newTier = Math.max(newTier, 1);
        else if (advancementId.equals(ADVANCEMENT_IRON)) newTier = Math.max(newTier, 2);
        else if (advancementId.equals(ADVANCEMENT_DIAMOND)) newTier = Math.max(newTier, 3);
        else if (advancementId.equals(ADVANCEMENT_NETHERITE)) newTier = Math.max(newTier, 4);

        if (newTier > this.tier) {
            this.tier = newTier;
            this.setDirty();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Tier", this.tier);
        return tag;
    }
}
