# Схема базы данных

## Диаграмма связей

```
┌─────────────────┐
│   submissions   │
├─────────────────┤
│ id (PK)         │
│ user_id         │
│ username        │
│ photo_path      │
│ user_description│
│ admin_description│
│ status          │
│ created_at      │
│ updated_at      │
└────────┬────────┘
         │
         │ 1:N
         │
┌────────▼────────────┐
│ submission_tags     │
├─────────────────────┤
│ submission_id (FK)  │
│ tag_id (FK)         │
└────────┬────────────┘
         │
         │ N:1
         │
┌────────▼────────┐
│      tags       │
├─────────────────┤
│ id (PK)         │
│ name (UNIQUE)   │
│ created_at      │
└─────────────────┘


┌─────────────────┐
│  certificates   │
├─────────────────┤
│ id (PK)         │
│ submission_id(FK)│
│ photo_path      │
│ description     │
│ created_at      │
└────────┬────────┘
         │
         │ 1:N
         │
┌────────▼────────────┐
│ certificate_tags    │
├─────────────────────┤
│ certificate_id (FK) │
│ tag_id (FK)         │
└────────┬────────────┘
         │
         │ N:1
         │
┌────────▼────────┐
│      tags       │
│  (shared table) │
└─────────────────┘
```

## Таблицы

### submissions
Хранит заявки пользователей на добавление сертификатов.

| Поле | Тип | Описание |
|------|-----|----------|
| id | BIGSERIAL | Уникальный идентификатор (PK) |
| user_id | BIGINT | ID пользователя Telegram |
| username | VARCHAR(255) | Имя пользователя Telegram (nullable) |
| photo_path | VARCHAR(500) | Путь к фото на Яндекс.Диске |
| user_description | TEXT | Описание от пользователя (nullable) |
| admin_description | TEXT | Описание от администратора (nullable) |
| status | VARCHAR(50) | Статус: PENDING, APPROVED, REJECTED |
| created_at | TIMESTAMP | Дата создания |
| updated_at | TIMESTAMP | Дата последнего обновления |

**Индексы:**
- `idx_submissions_status` на поле `status`
- `idx_submissions_user_id` на поле `user_id`

### tags
Хранит теги для категоризации заявок и сертификатов.

| Поле | Тип | Описание |
|------|-----|----------|
| id | BIGSERIAL | Уникальный идентификатор (PK) |
| name | VARCHAR(100) | Название тега (UNIQUE) |
| created_at | TIMESTAMP | Дата создания |

### submission_tags
Связь многие-ко-многим между заявками и тегами.

| Поле | Тип | Описание |
|------|-----|----------|
| submission_id | BIGINT | ID заявки (FK → submissions.id) |
| tag_id | BIGINT | ID тега (FK → tags.id) |

**Первичный ключ:** (submission_id, tag_id)

**Каскадное удаление:** При удалении заявки или тега удаляются связи.

### certificates
Хранит одобренные сертификаты.

| Поле | Тип | Описание |
|------|-----|----------|
| id | BIGSERIAL | Уникальный идентификатор (PK) |
| submission_id | BIGINT | ID заявки (FK → submissions.id, nullable) |
| photo_path | VARCHAR(500) | Путь к фото |
| description | TEXT | Описание сертификата |
| created_at | TIMESTAMP | Дата создания |

### certificate_tags
Связь многие-ко-многим между сертификатами и тегами.

| Поле | Тип | Описание |
|------|-----|----------|
| certificate_id | BIGINT | ID сертификата (FK → certificates.id) |
| tag_id | BIGINT | ID тега (FK → tags.id) |

**Первичный ключ:** (certificate_id, tag_id)

**Каскадное удаление:** При удалении сертификата или тега удаляются связи.

## Связи

1. **submissions ↔ tags** (Many-to-Many)
   - Через таблицу `submission_tags`
   - Одна заявка может иметь много тегов
   - Один тег может быть у многих заявок

2. **certificates ↔ tags** (Many-to-Many)
   - Через таблицу `certificate_tags`
   - Один сертификат может иметь много тегов
   - Один тег может быть у многих сертификатов

3. **certificates → submissions** (Many-to-One)
   - Один сертификат может быть создан из одной заявки
   - Одна заявка может породить несколько сертификатов
   - Связь опциональная (nullable)

## Статусы заявок

| Статус | Описание |
|--------|----------|
| PENDING | Заявка ожидает рассмотрения |
| APPROVED | Заявка одобрена администратором |
| REJECTED | Заявка отклонена администратором |

## Примеры запросов

### Получить все заявки с тегами
```sql
SELECT 
    s.*,
    STRING_AGG(t.name, ', ') as tags
FROM submissions s
LEFT JOIN submission_tags st ON s.id = st.submission_id
LEFT JOIN tags t ON st.tag_id = t.id
GROUP BY s.id;
```

### Получить заявки на рассмотрении
```sql
SELECT * FROM submissions 
WHERE status = 'PENDING' 
ORDER BY created_at DESC;
```

### Получить популярные теги
```sql
SELECT 
    t.name,
    COUNT(DISTINCT st.submission_id) as submission_count,
    COUNT(DISTINCT ct.certificate_id) as certificate_count
FROM tags t
LEFT JOIN submission_tags st ON t.id = st.tag_id
LEFT JOIN certificate_tags ct ON t.id = ct.tag_id
GROUP BY t.id, t.name
ORDER BY (submission_count + certificate_count) DESC;
```

### Получить сертификаты с их исходными заявками
```sql
SELECT 
    c.*,
    s.user_id,
    s.username,
    s.user_description as original_description
FROM certificates c
LEFT JOIN submissions s ON c.submission_id = s.id;
```

## Миграции

Миграции управляются через Liquibase:
- Главный файл: `src/main/resources/db/changelog/db.changelog-master.xml`
- Миграции: `src/main/resources/db/changelog/changes/`

### Применение миграций
Миграции применяются автоматически при запуске приложения.

### Откат миграций
```bash
mvn liquibase:rollback -Dliquibase.rollbackCount=1
```

## Резервное копирование

### Создание бэкапа
```bash
docker exec knife-certs-db pg_dump -U postgres knife_certs > backup.sql
```

### Восстановление из бэкапа
```bash
docker exec -i knife-certs-db psql -U postgres knife_certs < backup.sql
```
