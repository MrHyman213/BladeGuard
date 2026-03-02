# Система поиска по моделям ножей

## Архитектура

Система использует отношение "многие-ко-многим" для связи сертификатов и моделей ножей.

### Таблицы базы данных

1. **submissions** - таблица сертификатов
   - Содержит: название, бренд, индекс, фото, статус
   - Одна запись = один сертификат

2. **knife_models** - таблица уникальных названий ножей
   - Содержит: название, нормализованное название
   - Одна запись = одно уникальное название ножа
   - Нормализация: lowercase, удаление лишних пробелов

3. **submission_models** - связующая таблица (многие-ко-многим)
   - Связывает сертификаты с моделями ножей
   - Поля: submission_id, knife_model_id, is_primary
   - `is_primary=true` - основное название сертификата
   - `is_primary=false` - альтернативная модель

## Логика работы

### Добавление сертификата

1. Пользователь заполняет заявку:
   - Название: "Mora Companion"
   - Бренд: "Morakniv"
   - Индекс: "12141"
   - Альтернативные модели: "Morakniv Companion, Mora Classic"

2. Модератор редактирует и одобряет заявку

3. Система автоматически:
   - Создает/находит `knife_model` для "Mora Companion" (основная модель)
   - Создает связь в `submission_models` с `is_primary=true`
   - Для каждой альтернативы:
     - Создает/находит `knife_model` для "Morakniv Companion"
     - Создает/находит `knife_model` для "Mora Classic"
     - Создает связи в `submission_models` с `is_primary=false`

### Поиск сертификата

Пользователь вводит название ножа, например "Morakniv Companion".

**Сценарий 1: Точное совпадение (is_primary=true)**
- В системе есть сертификат с основным названием "Morakniv Companion"
- Результат: ✅ "Найден сертификат на эту модель!"
- Показывается этот сертификат

**Сценарий 2: Альтернативное совпадение (is_primary=false)**
- В системе нет сертификата с основным названием "Morakniv Companion"
- Но есть сертификаты, где "Morakniv Companion" указан как альтернатива
- Результат: ⚠️ "К сожалению, сертификата именно на модель 'Morakniv Companion' нет в системе. Но мы можем предложить альтернативные варианты:"
- Показываются все сертификаты, где это название в альтернативах

**Сценарий 3: Ничего не найдено**
- Название ножа вообще отсутствует в системе
- Результат: "По запросу ничего не найдено"

## Преимущества подхода

1. **Нормализация данных**: Каждое название ножа хранится один раз
2. **Гибкость**: Один сертификат может подходить к нескольким моделям
3. **Переиспользование**: Если название уже есть в системе, создается только связь
4. **Понятность для пользователя**: Система объясняет, точный это результат или альтернатива

## Примеры использования

### Пример 1: Добавление первого сертификата

```
Сертификат #1:
- Название: "Mora Companion"
- Альтернативы: "Morakniv Companion"

Результат в БД:
knife_models:
  - id=1, name="Mora Companion", normalized="mora companion"
  - id=2, name="Morakniv Companion", normalized="morakniv companion"

submission_models:
  - submission_id=1, knife_model_id=1, is_primary=true
  - submission_id=1, knife_model_id=2, is_primary=false
```

### Пример 2: Добавление второго сертификата с пересечением

```
Сертификат #2:
- Название: "Morakniv Companion"
- Альтернативы: "Mora Companion, Mora Classic"

Результат в БД:
knife_models:
  - id=1, name="Mora Companion", normalized="mora companion" (уже существует)
  - id=2, name="Morakniv Companion", normalized="morakniv companion" (уже существует)
  - id=3, name="Mora Classic", normalized="mora classic" (новая запись)

submission_models:
  - submission_id=1, knife_model_id=1, is_primary=true
  - submission_id=1, knife_model_id=2, is_primary=false
  - submission_id=2, knife_model_id=2, is_primary=true (новая связь)
  - submission_id=2, knife_model_id=1, is_primary=false (новая связь)
  - submission_id=2, knife_model_id=3, is_primary=false (новая связь)
```

### Пример 3: Поиск

**Поиск "Mora Companion":**
- Найден сертификат #1 (is_primary=true)
- Результат: ✅ "Найден сертификат на эту модель!"
- Показывается сертификат #1

**Поиск "Mora Classic":**
- Нет сертификата с is_primary=true
- Найден сертификат #2 (is_primary=false)
- Результат: ⚠️ "К сожалению, сертификата именно на модель 'Mora Classic' нет в системе. Но мы можем предложить альтернативные варианты:"
- Показывается сертификат #2

## Технические детали

### Нормализация названий

Метод `KnifeModel.normalizeName()`:
- Приводит к нижнему регистру
- Убирает лишние пробелы
- Заменяет множественные пробелы на один

Примеры:
- "Mora  Companion" → "mora companion"
- "MORAKNIV Companion" → "morakniv companion"
- "  Mora Classic  " → "mora classic"

### Методы поиска

1. `searchCertificatesByKnifeNameDetailed(String query)` - возвращает `SearchResult` с информацией о типе совпадения
2. `searchCertificatesByKnifeName(String query)` - возвращает только список сертификатов

### Миграция данных

При применении миграции `006-create-knife-models-tables.yaml`:
1. Создаются новые таблицы
2. Автоматически мигрируются существующие данные:
   - Из поля `name` в `submissions` создаются записи в `knife_models`
   - Из поля `alternative_models` парсятся и создаются дополнительные записи
   - Создаются связи в `submission_models`
