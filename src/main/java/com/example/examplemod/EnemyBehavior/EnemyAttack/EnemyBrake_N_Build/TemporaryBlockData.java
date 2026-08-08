package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBrake_N_Build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Відстежує блоки, які поставили моби (міст/вежа під час копання), разом з тіком, коли кожен з
 * них має зникнути сам. Одна ціль {@code TemporaryBlockData} на КОЖЕН вимір (SavedData
 * прив'язується до конкретного {@code ServerLevel}, тож окремі виміри автоматично не плутають
 * позиції між собою — не треба власного ключа з ResourceKey).
 * <p>
 * Періодичне видалення прострочених — {@code ModBlockEvents} (тік раз на секунду). "Гравець
 * ламає такий блок раніше" обробляється окремо через {@code BlockDropsEvent} — там достатньо
 * прибрати позицію звідси, самого видалення блоку вже не треба (гравець і так його зламав).
 */
public class TemporaryBlockData extends SavedData {

    public static final SavedData.Factory<TemporaryBlockData> FACTORY =
            new SavedData.Factory<>(TemporaryBlockData::new, TemporaryBlockData::load, null);

    private final Map<BlockPos, Long> expireAtTick = new HashMap<>();

    public static TemporaryBlockData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "betterenemysai_temp_blocks");
    }

    private static TemporaryBlockData load(CompoundTag tag, HolderLookup.Provider registries) {
        TemporaryBlockData data = new TemporaryBlockData();
        ListTag list = tag.getList("Entries", 10); // 10 = CompoundTag id
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            data.expireAtTick.put(pos, entry.getLong("expire"));
        }
        return data;
    }

    public void track(BlockPos pos, long expireAtGameTime) {
        this.expireAtTick.put(pos.immutable(), expireAtGameTime);
        this.setDirty();
    }

    public void untrack(BlockPos pos) {
        if (this.expireAtTick.remove(pos) != null) {
            this.setDirty();
        }
    }

    public boolean isTracked(BlockPos pos) {
        return this.expireAtTick.containsKey(pos);
    }

    /**
     * Знімок поточних записів для тік-обробника — копія, щоб можна було модифікувати мапу під час ітерації.
     */
    public Map<BlockPos, Long> snapshot() {
        return new HashMap<>(this.expireAtTick);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Long> entry : this.expireAtTick.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("x", entry.getKey().getX());
            entryTag.putInt("y", entry.getKey().getY());
            entryTag.putInt("z", entry.getKey().getZ());
            entryTag.putLong("expire", entry.getValue());
            list.add(entryTag);
        }
        tag.put("Entries", list);
        return tag;
    }
}
