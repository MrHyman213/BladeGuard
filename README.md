# Knife Certificates Bot

Telegram-бот для системы сертификатов на ножи с интеграцией Yandex Disk.

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

### Пользовательский бот (KnifeBot)
- Просмотр списка сертификатов
- Получение фотографий сертификатов
- Подача заявок на добавление новых сертификатов:
  - Отправка фото сертификата
  - Добавление названия модели (опционально)
  - Добавление описания (опционально)
  - Отмена заявки командой /cancel
  - Получение подтверждения с ID заявки

### Админ-бот (AdminBot)
- Загрузка фотографий на Yandex Disk
- Просмотр списка ожидающих заявок (/pending)
- Просмотр деталей заявки (/review <ID>)
- Одобрение заявок (/approve <ID>) - перемещает фото в папку certificates
- Отклонение заявок (/reject <ID>) - удаляет фото из системы
- Автоматическое уведомление пользователей о решении

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

- `submissions` - заявки пользователей на добавление сертификатов
  - Поля: id, user_id, username, model_name, description, photo_path, status, created_at, moderated_at, moderated_by
  - Статусы: PENDING, APPROVED, REJECTED
  - Индексы: status, user_id, created_at

Миграции базы данных управляются через Liquibase (см. `src/main/resources/db/changelog/`)

## Команды бота

### Пользовательский бот (KnifeBot)
- `/start` - главное меню и список команд
- `/list` - показать список всех фотографий на Яндекс.Диске
- `/get <имя_файла>` - получить конкретную фотографию с Яндекс.Диска
- `/cancel` - отменить текущую заявку
- Отправка фото - начать процесс подачи заявки:
  1. Отправить фото сертификата
  2. Ввести название модели или /skip
  3. Ввести описание или /skip
  4. Получить подтверждение с ID заявки

### Админ-бот (AdminBot)
- `/start` - главное меню и список команд
- `/pending` - показать список ожидающих заявок (с пагинацией)
- `/review <ID>` - просмотреть детали заявки с фото
- `/approve <ID>` - одобрить заявку (перемещает фото в certificates/)
- `/reject <ID>` - отклонить заявку (удаляет фото)
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
