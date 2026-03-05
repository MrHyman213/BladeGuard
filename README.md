# Blade Guardian - Knife Certificates Bot

Telegram-бот для системы сертификатов на ножи с интеграцией Yandex Disk и современным UI.

## Технологии

- Java 21
- Spring Boot 3
- PostgreSQL
- Telegram Bot API
- Yandex Disk API
- Docker & Docker Compose
- Liquibase
- Maven

## Функционал

### Пользовательский бот (KnifeBotRedesigned)

**Навигация:**
- 🏠 Главное меню с брендами (сетка 10×3, циклическая пагинация)
- 🔪 Просмотр моделей по бренду
- 📜 Просмотр сертификатов с фото
- 🔄 Альтернативные сертификаты
- 🔍 Поиск по брендам и моделям

**Загрузка сертификата:**
- 📤 Интерактивная форма с inline-кнопками
- 📝 Редактирование полей (бренд, название, индекс)
- ➕ Добавление альтернативных моделей
- 📷 Поддержка шаблона в caption: `Бренд | Название | Индекс`
- ✅ Отправка на модерацию

### Админ-бот (AdminBot)

**Модерация заявок:**
- 📋 Просмотр ожидающих заявок с пагинацией
- ✏️ Редактирование полей перед одобрением
- ✅ Одобрение/❌ Отклонение заявок
- 🔔 Автоматические уведомления пользователей

**Pending Alternatives:**
- ⚠️ Отображение ожидающих альтернатив
- ✅ Автоматический поиск и создание связей
- 🗑️ Удаление отдельных или всех альтернатив

**Транзитивные альтернативы:**
- 🔄 Автоматическая проверка при одобрении
- 💡 Предложение добавить связанные альтернативы

**Управление фото:**
- 📷 Замена фото сертификата
- 📜 История замен с датами и модераторами
- ↩️ Откат замены фото
- 🗄️ Архивирование старых фото

**Настройки:**
- ⚙️ Изменение разделителя для альтернатив
- 🔧 Команда `/settings` для просмотра настроек

### Система заявок
- Многошаговый диалог для подачи заявки
- Фото загружаются в папку `app:/offers/` на Яндекс.Диске
- Одобренные сертификаты перемещаются в `app:/certificates/`
- Отклоненные заявки удаляются из системы
- Статусы заявок: PENDING, APPROVED, REJECTED
- Отслеживание модератора и времени модерации
- Обработка ошибок с логированием и уведомлениями

## Установка

### Быстрый старт
См. [docs/QUICKSTART.md](docs/QUICKSTART.md) для запуска за 5 минут.

### Подробная инструкция

1. Клонируйте репозиторий
2. Скопируйте `.env.example` в `.env` и заполните переменные:
   - `TELEGRAM_BOT_TOKEN` - токен бота от @BotFather
   - `TELEGRAM_BOT_USERNAME` - имя бота
   - `TELEGRAM_ADMIN_BOT_TOKEN` - токен админ-бота от @BotFather
   - `TELEGRAM_ADMIN_BOT_USERNAME` - имя админ-бота
   - `YANDEX_DISK_TOKEN` - OAuth токен Yandex Disk

3. Запустите через Docker Compose:
```bash
docker-compose up -d
```

Подробнее см. [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

## Локальная разработка

```bash
mvn spring-boot:run
```

## Структура БД

### Основные таблицы:
- `submissions` - заявки пользователей на добавление сертификатов
  - Поля: id, user_id, username, name, brand_id, index_code, photo_path, status, created_at, moderated_at, moderated_by
  - Статусы: PENDING, APPROVED, REJECTED
  - Индексы: status, user_id, created_at

- `brands` - бренды ножей
  - Поля: id, name
  - Уникальный индекс: name

- `submission_alternatives` - связи между альтернативными сертификатами
  - Поля: id, submission_id, alternative_id
  - Двусторонние связи (A↔B)

- `pending_alternatives` - временное хранилище альтернатив из заявок
  - Поля: id, submission_id, brand_name, knife_name
  - Обрабатываются модератором

- `photo_history` - история замен фото
  - Поля: id, submission_id, old_path, new_path, replaced_by, replaced_at, reason
  - Для отката изменений

- `bot_settings` - настройки бота
  - Поля: id, setting_key, setting_value
  - Хранит разделитель для альтернатив

Миграции базы данных управляются через Liquibase (см. `src/main/resources/db/changelog/`)

## Команды бота

### Пользовательский бот (KnifeBotRedesigned)
- `/start` - открыть главное меню с брендами
- Inline-кнопки для навигации:
  - Выбор бренда → список моделей
  - Выбор модели → просмотр сертификата
  - "Больше..." → полный список альтернатив
  - 🔍 Поиск по брендам/моделям
  - 📤 Загрузить сертификат

### Админ-бот (AdminBot)
- `/start` - главное меню
- `/pending` - список ожидающих заявок (с пагинацией)
- `/settings` - просмотр настроек
- `/setseparator <разделитель>` - изменить разделитель
- Inline-кнопки для модерации:
  - ✏️ Редактирование полей
  - ✅ Одобрить / ❌ Отклонить
  - 📷 Заменить фото
  - 📜 История замен
  - ↩️ Откатить замену
- Отправка фото - загрузка на Яндекс.Диск

## API Yandex Disk

Публичная папка: https://disk.yandex.ru/d/nZycyfROkwu9gg

## Документация

📁 **Вся документация находится в папке [docs/](docs/)** - см. [docs/README.md](docs/README.md) для навигации

- [docs/QUICKSTART.md](docs/QUICKSTART.md) - Быстрый старт за 5 минут
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) - Архитектура системы
- [docs/SUBMISSION_FEATURE.md](docs/SUBMISSION_FEATURE.md) - Подробное описание функционала заявок
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) - Инструкция по развертыванию
- [docs/EXAMPLES.md](docs/EXAMPLES.md) - Примеры использования с диалогами
- [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) - Схема базы данных
- [docs/CHANGELOG.md](docs/CHANGELOG.md) - История изменений
- [docs/TODO.md](docs/TODO.md) - Планы развития
- [sql/queries.sql](sql/queries.sql) - Полезные SQL запросы
- [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) - Структура проекта
