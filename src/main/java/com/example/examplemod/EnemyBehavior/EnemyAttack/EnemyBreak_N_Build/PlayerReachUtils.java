package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

import com.example.examplemod.Config;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Два "радіуси гравця" з опису користувача: радіус АТАКИ ({@code Attributes.ENTITY_INTERACTION_RANGE})
 * і радіус ВЗАЄМОДІЇ З БЛОКАМИ ({@code Attributes.BLOCK_INTERACTION_RANGE}). Обидва читаються
 * ЗАВЖДИ динамічно (щоразу заново з атрибута), НІКОЛИ не кешуються як константа: будь-який мод,
 * що додає предмет/зачарування, яке підвищує ОДИН з цих атрибутів, має підхопитись самим цим
 * читанням, без жодних змін тут.
 * <p>
 * Для розміру зони в хід іде НЕ радіус атаки сам по собі, а {@link #getCombinedRawReach}: обидва
 * радіуси порівнюються, і БІЛЬШИЙ з двох +1 — саме це число і є "reach", яким далі оперує
 * {@link #getReachCappedForZoneSizing} (капання не чіпає сам вибір більшого/додавання 1, лише
 * обмежує зверху вже готовий результат).
 * <p>
 * Ваніль: атака 3.0 у виживанні / 5.0 у креативі; блоки 4.5 у виживанні / 5.0 у креативі (обидва
 * max 64.0 — теоретично може підняти мод). Про креативні 5.0 можна не думати —
 * {@code PursuitEnemyBehavior.isValidTarget} вже виключає креативних гравців з усієї цієї
 * системи (не агряться на них узагалі).
 */
final class PlayerReachUtils {

    private PlayerReachUtils() {
    }

    /**
     * "Чесне" (некапнуте) значення радіуса АТАКИ — саме це має йти в будь-яку перевірку виду "чи
     * гравець зараз може мене дістати" (майбутні фази). НІКОЛИ не використовувати цей результат
     * САМ ПО СОБІ для розміру структур/буферів — для цього {@link #getReachCappedForZoneSizing}
     * (яка бере не його одного, а {@link #getCombinedRawReach}).
     */
    static double getRawEntityInteractionRange(Player player) {
        return player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    /**
     * Те саме, але радіус ВЗАЄМОДІЇ З БЛОКАМИ — читається так само (динамічно, з атрибута,
     * ніколи не кешується), з тих самих причин, що й {@link #getRawEntityInteractionRange}.
     */
    static double getRawBlockInteractionRange(Player player) {
        return player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
    }

    /**
     * "порівнюються, і той який більший за інший — до нього додається 1" — БІЛЬШИЙ з двох сирих
     * радіусів (атака / блоки), плюс 1. Це число, а НЕ голий радіус атаки, іде в розмір зони.
     */
    static double getCombinedRawReach(Player player) {
        double attackRange = getRawEntityInteractionRange(player);
        double blockRange = getRawBlockInteractionRange(player);
        return Math.max(attackRange, blockRange) + 1.0;
    }

    /**
     * Обмежене зверху {@link Config#TOWER_ZONE_REACH_CAP} значення {@link #getCombinedRawReach} —
     * ЛИШЕ для розміру буферної зони (наскільки далеко моб заздалегідь планує обходити), не про
     * безпеку. Якщо комбінований reach менший за кеп — повертається реальне значення без змін
     * (кеп ніколи не збільшує).
     */
    static double getReachCappedForZoneSizing(Player player) {
        double raw = getCombinedRawReach(player);
        double cap = Config.TOWER_ZONE_REACH_CAP.get();
        return Math.min(raw, cap);
    }
}