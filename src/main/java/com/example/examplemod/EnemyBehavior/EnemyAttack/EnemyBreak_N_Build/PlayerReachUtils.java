package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

import com.example.examplemod.Config;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * "Радіус атаки гравця" з опису користувача — читається ЗАВЖДИ динамічно (щоразу заново з
 * атрибута), НІКОЛИ не кешується як константа: будь-який мод, що додає предмет/зачарування,
 * яке підвищує {@code Attributes.ENTITY_INTERACTION_RANGE}, має підхопитись самим цим читанням,
 * без жодних змін тут.
 * <p>
 * Ваніль: 3.0 у виживанні, 5.0 у креативі (max 64.0 — теоретично може підняти мод). Про 5.0
 * можна не думати — {@code PursuitEnemyBehavior.isValidTarget} вже виключає креативних гравців
 * з усієї цієї системи (не агряться на них узагалі).
 */
final class PlayerReachUtils {

    private PlayerReachUtils() {
    }

    /**
     * "Чесне" (некапнуте) значення — саме це має йти в будь-яку перевірку виду "чи гравець зараз
     * може мене дістати" (майбутні фази). НІКОЛИ не використовувати цей результат для розміру
     * структур/буферів — для цього {@link #getReachCappedForZoneSizing}.
     */
    static double getRawEntityInteractionRange(Player player) {
        return player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    /**
     * Обмежене зверху {@link Config#TOWER_ZONE_REACH_CAP} значення — ЛИШЕ для розміру буферної
     * зони (наскільки далеко моб заздалегідь планує обходити), не про безпеку. Якщо реальний
     * reach менший за кеп — повертається реальне значення без змін (кеп ніколи не збільшує).
     */
    static double getReachCappedForZoneSizing(Player player) {
        double raw = getRawEntityInteractionRange(player);
        double cap = Config.TOWER_ZONE_REACH_CAP.get();
        return Math.min(raw, cap);
    }
}
