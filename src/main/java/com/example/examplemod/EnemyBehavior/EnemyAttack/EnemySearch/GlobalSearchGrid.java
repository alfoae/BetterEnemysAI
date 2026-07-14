package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemySearch;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальний, спільний для ВСІХ мобів реєстр "прочеканих" (x,z)-колонок (крок 1x1 блок).
 * НЕ прив'язаний до жодного конкретного гравця чи епізоду переслідування: моб у SEARCHING шукає
 * БУДЬ-ЯКОГО ворога, а не когось конкретного, тому позначки спільні й для всіх мобів, і для всіх
 * гравців одночасно.
 * <p>
 * "Непрочекана" — це просто ВІДСУТНІСТЬ запису (лінива структура, не зберігаємо нічого для
 * порожньої більшості світу). Запис вважається чинним лише {@link #STALE_AFTER_TICKS} тіків без
 * оновлення — застарілі протухають ЛІНИВО (перевіряються й видаляються прямо під час читання,
 * окремого фонового прибирання не треба): "конкретна ділянка не оновлюється протягом часу режиму
 * пошуку — ділянка видаляється" виконується автоматично, без явного стеження за ділянками.
 * <p>
 * Розділено по вимірах (Overworld/Nether/End) — координати з різних вимірів можуть числово
 * збігатися, але фізично це геть різні місця.
 * <p>
 * Позначати точку "прочеканою" має право лише код, який точно виконується для моба в стані
 * SEARCHING (або підходу до нього) — це забезпечується тим, що {@link #markChecked} викликається
 * виключно з {@link SearchGrid}, а той — виключно з відповідних гілок PursuitEnemyBehavior.
 */
public final class GlobalSearchGrid {

    /**
     * Скільки тіків запис лишається дійсним без оновлення, перш ніж вважається застарілим.
     */
    private static final long STALE_AFTER_TICKS = 15 * 20; // той самий час, що й SEARCH_DURATION_TICKS

    private static final Map<ResourceKey<Level>, Map<Long, Long>> BY_DIMENSION = new ConcurrentHashMap<>();

    private GlobalSearchGrid() {
    }

    private static Map<Long, Long> forDimension(Level level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), d -> new ConcurrentHashMap<>());
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static boolean isChecked(Level level, int x, int z, long currentGameTime) {
        Map<Long, Long> map = forDimension(level);
        Long touched = map.get(key(x, z));
        if (touched == null) {
            return false;
        }
        if (currentGameTime - touched > STALE_AFTER_TICKS) {
            map.remove(key(x, z)); // застаріло - вважаємо неперевіреним і забуваємо назавжди
            return false;
        }
        return true;
    }

    public static void markChecked(Level level, int x, int z, long currentGameTime) {
        forDimension(level).put(key(x, z), currentGameTime);
    }
}
