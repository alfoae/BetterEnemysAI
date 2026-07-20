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
 * порожньої більшості світу). Запис вважається чинним лише {@code staleAfterTicks} тіків без
 * оновлення — застарілі протухають ЛІНИВО (перевіряються й видаляються прямо під час читання,
 * окремого фонового прибирання не треба). {@code staleAfterTicks} свідомо НЕ захардкожений тут:
 * раніше було своє окреме число ("той самий час, що й SEARCH_DURATION_TICKS" — але тільки в
 * коментарі, не насправді), і коли SEARCH_DURATION_TICKS в PursuitEnemyBehavior зростав, ці два
 * числа розходились — позначки протухали ще ДО завершення того самого пошуку, який їх поставив,
 * і моб ходив по колу тими самими "вже прочеканими" клітинками. Тепер виклик сам передає, скільки
 * тіків триває його пошук — єдине джерело правди замість двох чисел, які треба синхронізувати
 * вручну.
 * <p>
 * Розділено по вимірах (Overworld/Nether/End) — координати з різних вимірів можуть числово
 * збігатися, але фізично це геть різні місця.
 * <p>
 * Позначати точку "прочеканою" має право лише код, який точно виконується для моба в стані
 * SEARCHING (або підходу до нього) — це забезпечується тим, що {@link #markChecked} викликається
 * виключно з {@link SearchGrid}, а той — виключно з відповідних гілок PursuitEnemyBehavior.
 */
public final class GlobalSearchGrid {

    private static final Map<ResourceKey<Level>, Map<Long, Long>> BY_DIMENSION = new ConcurrentHashMap<>();

    private GlobalSearchGrid() {
    }

    private static Map<Long, Long> forDimension(Level level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), d -> new ConcurrentHashMap<>());
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /**
     * @param staleAfterTicks скільки тіків запис лишається дійсним без оновлення. Має бути НЕ
     *                        менше за тривалість пошуку, що його читає — інакше позначки протухнуть
     *                        просто в процесі того самого пошуку.
     */
    public static boolean isChecked(Level level, int x, int z, long currentGameTime, long staleAfterTicks) {
        Map<Long, Long> map = forDimension(level);
        Long touched = map.get(key(x, z));
        if (touched == null) {
            return false;
        }
        if (currentGameTime - touched > staleAfterTicks) {
            map.remove(key(x, z)); // застаріло - вважаємо неперевіреним і забуваємо назавжди
            return false;
        }
        return true;
    }

    public static void markChecked(Level level, int x, int z, long currentGameTime) {
        forDimension(level).put(key(x, z), currentGameTime);
    }
}