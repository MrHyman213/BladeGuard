# Руководство по миграции на новую схему базы данных

## Обзор изменений

Новая схема базы данных упрощает структуру и разделяет логику:

### Старая схема
- `submissions` - все заявки (pending, approved, rejected)
- Сложная логика статусов
- Альтернативы хранились как JSON строки

### Новая схема
- `submissions_buffer` - только ожидающие заявки
- `knives` - одобренные сертификаты
- `alternatives` - связи между ножами (many-to-many)
- `buffer_alternatives` - альтернативы в заявках

## Пошаговая миграция

### 1. Подготовка

Убедитесь, что у вас есть резервная копия базы данных:
```bash
pg_dump your_database > backup_before_migration.sql
```

### 2. Применение новой схемы

Liquibase автоматически применит новую схему при запуске приложения.
Файл миграции: `src/main/resources/db/changelog/changes/004-current-schema.yaml`

### 3. Переключение на новые боты

В переменных окружения или application.properties установите:
```properties
BOT_VERSION=new
ADMIN_BOT_VERSION=new
```

### 4. Миграция данных (если необходимо)

Если у вас есть существующие данные в старой схеме, создайте скрипт миграции:

```sql
-- Пример миграции одобренных заявок в таблицу knives
INSERT INTO knives (model_id, brand_id, idx, photo_path)
SELECT 
    km.id,
    b.id,
    s.index_code,
    s.photo_path
FROM submissions s
JOIN brands b ON b.name = s.brand
JOIN knife_models km ON km.name = s.name
WHERE s.status = 'APPROVED';

-- Пример миграции ожидающих заявок в submissions_buffer
INSERT INTO submissions_buffer (user_id, username, model_name, brand_name, idx, photo_path, created_at)
SELECT 
    s.user_id,
    s.username,
    s.name,
    b.name,
    s.index_code,
    s.photo_path,
    s.created_at
FROM submissions s
LEFT JOIN brands b ON s.brand_id = b.id
WHERE s.status = 'PENDING';
```

## Основные различия в работе

### Пользовательский бот (KnifeBotNew)
- Использует `KnifeService` для работы с одобренными сертификатами
- Создает заявки в `SubmissionBuffer`
- Упрощенная логика поиска и отображения

### Админ-бот (AdminBotNew)
- Работает с `SubmissionBufferService`
- Показывает меню подтверждения альтернатив
- При одобрении создает записи в `knives` и связи в `alternatives`

## Откат на старую схему

Если нужно вернуться к старой схеме:

1. Установите переменные окружения:
```properties
BOT_VERSION=old
ADMIN_BOT_VERSION=old
```

2. Перезапустите приложение

## Тестирование

1. Запустите приложение с новыми настройками
2. Проверьте работу пользовательского бота:
   - Отправка заявки
   - Просмотр брендов и моделей
   - Поиск сертификатов
3. Проверьте работу админ-бота:
   - Просмотр ожидающих заявок
   - Одобрение с альтернативами
   - Отклонение заявок

## Структура новых таблиц

### submissions_buffer
- Временное хранение заявок до модерации
- Содержит все данные заявки включая альтернативы

### knives
- Основная таблица одобренных сертификатов
- Связана с brands и knife_models

### alternatives
- Many-to-many связи между ножами
- Позволяет создавать сложные связи альтернатив

### buffer_alternatives
- Альтернативы в заявках (embedded в submissions_buffer)
- Используется только до одобрения заявки