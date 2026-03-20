package com.knifecerts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based тесты для логики автоматического удаления моделей-кандидатов.
 * Feature: blade-guardian-full-implementation
 *
 * Тесты симулируют логику DB-триггера trg_cleanup_orphan_models в чистом Java,
 * без реальной базы данных.
 *
 * Логика автоудаления: KnifeModel является кандидатом на удаление, если:
 *   - ни один нож этой модели не имеет photo_path != null
 *   - ни один нож этой модели не участвует ни в одной связи в alternatives
 */
class SubmissionServicePropertyTest {

    // ─── Внутренние модели для симуляции ────────────────────────────────────

    static class SimKnifeModel {
        final long id;
        final String name;

        SimKnifeModel(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class SimKnife {
        final long id;
        final long modelId;
        final String photoPath; // null означает отсутствие фото

        SimKnife(long id, long modelId, String photoPath) {
            this.id = id;
            this.modelId = modelId;
            this.photoPath = photoPath;
        }
    }

    /** Связь в таблице alternatives: (knifeId, alternativeKnifeId) */
    static class SimLink {
        final long knifeId;
        final long alternativeKnifeId;

        SimLink(long knifeId, long alternativeKnifeId) {
            this.knifeId = knifeId;
            this.alternativeKnifeId = alternativeKnifeId;
        }
    }

    /** Состояние «базы данных» */
    static class SimDb {
        final Map<Long, SimKnifeModel> models = new HashMap<>();
        final Map<Long, SimKnife> knives = new HashMap<>();
        final List<SimLink> links = new ArrayList<>();

        private final AtomicLong seq = new AtomicLong(1);

        long nextId() {
            return seq.getAndIncrement();
        }

        SimKnifeModel addModel(String name) {
            long id = nextId();
            SimKnifeModel m = new SimKnifeModel(id, name);
            models.put(id, m);
            return m;
        }

        SimKnife addKnife(long modelId, String photoPath) {
            long id = nextId();
            SimKnife k = new SimKnife(id, modelId, photoPath);
            knives.put(id, k);
            return k;
        }

        void addLink(long knifeId, long altKnifeId) {
            links.add(new SimLink(knifeId, altKnifeId));
        }

        /** Удалить конкретную связь и запустить логику триггера. */
        void deleteLink(SimLink link) {
            links.remove(link);
            // Триггер проверяет обе стороны удалённой связи
            triggerCleanup(link.knifeId, link.alternativeKnifeId);
        }

        /**
         * Симуляция функции cleanup_orphan_knife_models().
         * Удаляет knife_models, у которых нет ножей с photo_path != null
         * и нет ни одной связи в alternatives.
         */
        void triggerCleanup(long knifeId1, long knifeId2) {
            Set<Long> modelIdsToCheck = new HashSet<>();
            if (knives.containsKey(knifeId1)) {
                modelIdsToCheck.add(knives.get(knifeId1).modelId);
            }
            if (knives.containsKey(knifeId2)) {
                modelIdsToCheck.add(knives.get(knifeId2).modelId);
            }

            for (long modelId : modelIdsToCheck) {
                if (isOrphanModel(modelId)) {
                    // Удаляем модель и все её ножи (CASCADE)
                    Set<Long> knifeIdsOfModel = knives.values().stream()
                            .filter(k -> k.modelId == modelId)
                            .map(k -> k.id)
                            .collect(Collectors.toSet());
                    knifeIdsOfModel.forEach(knives::remove);
                    models.remove(modelId);
                }
            }
        }

        /**
         * Проверяет, является ли модель кандидатом на удаление:
         * нет ножей с photo_path != null И нет связей в alternatives.
         */
        boolean isOrphanModel(long modelId) {
            // Проверяем наличие ножей с фото
            boolean hasPhoto = knives.values().stream()
                    .anyMatch(k -> k.modelId == modelId && k.photoPath != null);
            if (hasPhoto) return false;

            // Проверяем наличие связей
            Set<Long> knifeIds = knives.values().stream()
                    .filter(k -> k.modelId == modelId)
                    .map(k -> k.id)
                    .collect(Collectors.toSet());

            boolean hasLinks = links.stream()
                    .anyMatch(l -> knifeIds.contains(l.knifeId) || knifeIds.contains(l.alternativeKnifeId));

            return !hasLinks;
        }

        /** Найти все модели-кандидаты на удаление (для проверки идемпотентности). */
        List<Long> findOrphanModels() {
            return models.keySet().stream()
                    .filter(this::isOrphanModel)
                    .collect(Collectors.toList());
        }

        /** Выполнить полную очистку всех кандидатов (для идемпотентности). */
        void runFullCleanup() {
            List<Long> orphans = findOrphanModels();
            for (long modelId : orphans) {
                Set<Long> knifeIdsOfModel = knives.values().stream()
                        .filter(k -> k.modelId == modelId)
                        .map(k -> k.id)
                        .collect(Collectors.toSet());
                knifeIdsOfModel.forEach(knives::remove);
                models.remove(modelId);
            }
        }
    }

    // ─── Генераторы ─────────────────────────────────────────────────────────

    /**
     * Генерирует «чистое» состояние БД: несколько моделей, каждая с одним или двумя ножами.
     * Каждая модель либо имеет фото, либо имеет хотя бы одну связь — т.е. нет pre-existing orphans.
     * Это соответствует инварианту: триггер поддерживает чистоту, поэтому начальное состояние чистое.
     */
    @Provide
    Arbitrary<SimDb> databases() {
        // Генерируем 2-5 моделей, каждая с одним ножом
        return Arbitraries.integers().between(2, 5).flatMap(modelCount ->
            Arbitraries.of(true, false).list().ofSize(modelCount).flatMap(hasPhotoFlags ->
                Arbitraries.integers().between(0, modelCount - 1).list().ofMinSize(modelCount).ofMaxSize(modelCount * 2)
                    .map(linkPairIndices -> {
                        SimDb db = new SimDb();
                        List<SimKnife> oneKnifePerModel = new ArrayList<>();

                        for (int i = 0; i < modelCount; i++) {
                            SimKnifeModel model = db.addModel("Model_" + (i + 1));
                            String photo = hasPhotoFlags.get(i) ? "app:/certificates/photo_" + model.id + ".jpg" : null;
                            oneKnifePerModel.add(db.addKnife(model.id, photo));
                        }

                        // Добавляем связи между ножами разных моделей
                        for (int idx = 0; idx + 1 < linkPairIndices.size(); idx += 2) {
                            int mi = linkPairIndices.get(idx) % modelCount;
                            int mj = linkPairIndices.get(idx + 1) % modelCount;
                            if (mi != mj) {
                                SimKnife k1 = oneKnifePerModel.get(mi);
                                SimKnife k2 = oneKnifePerModel.get(mj);
                                boolean alreadyLinked = db.links.stream()
                                        .anyMatch(l -> (l.knifeId == k1.id && l.alternativeKnifeId == k2.id)
                                                || (l.knifeId == k2.id && l.alternativeKnifeId == k1.id));
                                if (!alreadyLinked) {
                                    db.addLink(k1.id, k2.id);
                                    db.addLink(k2.id, k1.id);
                                }
                            }
                        }

                        // Убеждаемся, что каждая модель без фото имеет хотя бы одну связь
                        // (иначе это pre-existing orphan, что нарушает инвариант чистого состояния)
                        for (int i = 0; i < modelCount; i++) {
                            if (!hasPhotoFlags.get(i)) {
                                SimKnife knife = oneKnifePerModel.get(i);
                                boolean hasLink = db.links.stream()
                                        .anyMatch(l -> l.knifeId == knife.id || l.alternativeKnifeId == knife.id);
                                if (!hasLink) {
                                    // Связываем с первой другой моделью
                                    for (int j = 0; j < modelCount; j++) {
                                        if (j != i) {
                                            SimKnife other = oneKnifePerModel.get(j);
                                            db.addLink(knife.id, other.id);
                                            db.addLink(other.id, knife.id);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        return db;
                    })
            )
        );
    }

    // ─── Property 7: Постусловие чистоты БД после удаления связи ────────────

    /**
     * // Feature: blade-guardian-full-implementation, Property 7: Постусловие чистоты БД после удаления связи
     *
     * После удаления любой связи из alternatives в БД не должно существовать
     * записей в knife_models, у которых нет ни одного ножа с photo_path IS NOT NULL
     * и нет ни одной связи в alternatives.
     *
     * **Validates: Requirements 12.5, 18.1**
     */
    @Property(tries = 100)
    void autoDeletePostcondition(@ForAll("databases") SimDb db) {
        // Если нет связей — нечего удалять, постусловие тривиально выполнено
        if (db.links.isEmpty()) {
            assertThat(db.findOrphanModels()).isEmpty();
            return;
        }

        // Удаляем случайную связь (первую для детерминизма в рамках одного теста)
        SimLink linkToDelete = db.links.get(0);
        db.deleteLink(linkToDelete);

        // Постусловие: в БД не должно быть моделей-кандидатов на удаление
        List<Long> orphans = db.findOrphanModels();
        assertThat(orphans)
                .as("После удаления связи в БД не должно быть моделей без фото и без связей")
                .isEmpty();
    }

    // ─── Property 8: Автоудаление не затрагивает модели с фото ─────────────

    /**
     * // Feature: blade-guardian-full-implementation, Property 8: Автоудаление не затрагивает модели с фото
     *
     * После выполнения логики автоудаления ни одна модель, у которой есть хотя бы
     * один нож с photo_path IS NOT NULL, не должна быть удалена.
     *
     * **Validates: Requirements 12.3, 18.2**
     */
    @Property(tries = 100)
    void autoDeletePreservesPhotoModels(@ForAll("databases") SimDb db) {
        // Запоминаем модели с фото до очистки
        Set<Long> modelsWithPhoto = db.knives.values().stream()
                .filter(k -> k.photoPath != null)
                .map(k -> k.modelId)
                .collect(Collectors.toSet());

        // Запускаем полную очистку
        db.runFullCleanup();

        // Все модели с фото должны остаться
        for (long modelId : modelsWithPhoto) {
            assertThat(db.models).as("Модель с фото (id=%d) не должна быть удалена", modelId)
                    .containsKey(modelId);
        }
    }

    // ─── Property 9: Автоудаление не затрагивает связанные модели ───────────

    /**
     * // Feature: blade-guardian-full-implementation, Property 9: Автоудаление не затрагивает связанные модели
     *
     * После выполнения логики автоудаления ни одна модель, у которой есть хотя бы
     * одна связь в alternatives, не должна быть удалена.
     *
     * **Validates: Requirements 12.4, 18.3**
     */
    @Property(tries = 100)
    void autoDeletePreservesLinkedModels(@ForAll("databases") SimDb db) {
        // Запоминаем модели, у которых есть связи
        Set<Long> modelsWithLinks = new HashSet<>();
        for (SimLink link : db.links) {
            if (db.knives.containsKey(link.knifeId)) {
                modelsWithLinks.add(db.knives.get(link.knifeId).modelId);
            }
            if (db.knives.containsKey(link.alternativeKnifeId)) {
                modelsWithLinks.add(db.knives.get(link.alternativeKnifeId).modelId);
            }
        }

        // Запускаем полную очистку
        db.runFullCleanup();

        // Все модели со связями должны остаться
        for (long modelId : modelsWithLinks) {
            assertThat(db.models).as("Модель со связями (id=%d) не должна быть удалена", modelId)
                    .containsKey(modelId);
        }
    }

    // ─── Property 10: Идемпотентность автоудаления ──────────────────────────

    /**
     * // Feature: blade-guardian-full-implementation, Property 10: Идемпотентность автоудаления
     *
     * Повторная проверка кандидатов на удаление после уже выполненной очистки
     * должна возвращать пустой результат.
     *
     * **Validates: Requirements 12.6, 18.4**
     */
    @Property(tries = 100)
    void autoDeleteIdempotent(@ForAll("databases") SimDb db) {
        // Первая очистка
        db.runFullCleanup();

        // После первой очистки кандидатов быть не должно
        List<Long> orphansAfterFirst = db.findOrphanModels();
        assertThat(orphansAfterFirst)
                .as("После первой очистки кандидатов на удаление быть не должно")
                .isEmpty();

        // Вторая очистка — ничего не должно измениться
        int modelCountBefore = db.models.size();
        db.runFullCleanup();
        int modelCountAfter = db.models.size();

        assertThat(modelCountAfter)
                .as("Повторная очистка не должна удалять дополнительные модели")
                .isEqualTo(modelCountBefore);

        // Снова проверяем — кандидатов нет
        assertThat(db.findOrphanModels())
                .as("После второй очистки кандидатов на удаление быть не должно")
                .isEmpty();
    }

    // ─── Unit-тест: обе стороны связи имеют photo_path → ничего не удаляется

    /**
     * Граничный случай: удаление связи, когда оба ножа имеют photo_path.
     * Ни одна модель не должна быть удалена.
     *
     * **Validates: Requirements 12.3, 18.2**
     */
    @Property(tries = 1)
    void deleteLinkBothSidesHavePhoto() {
        SimDb db = new SimDb();

        SimKnifeModel modelA = db.addModel("ModelA");
        SimKnifeModel modelB = db.addModel("ModelB");

        SimKnife knifeA = db.addKnife(modelA.id, "app:/certificates/photo_a.jpg");
        SimKnife knifeB = db.addKnife(modelB.id, "app:/certificates/photo_b.jpg");

        db.addLink(knifeA.id, knifeB.id);
        db.addLink(knifeB.id, knifeA.id);

        // Удаляем одну из связей
        SimLink linkToDelete = db.links.get(0);
        db.deleteLink(linkToDelete);

        // Обе модели должны остаться — у них есть photo_path
        assertThat(db.models).containsKey(modelA.id);
        assertThat(db.models).containsKey(modelB.id);
    }
}
