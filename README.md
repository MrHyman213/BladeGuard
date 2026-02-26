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

- Загрузка фотографий на Yandex Disk через Telegram
- Получение списка загруженных фотографий
- Скачивание фотографий из Yandex Disk в Telegram
- Автоматическое именование файлов с временной меткой

## Установка

1. Клонируйте репозиторий
2. Скопируйте `.env.example` в `.env` и заполните переменные:
   - `TELEGRAM_BOT_TOKEN` - токен бота от @BotFather
   - `TELEGRAM_BOT_USERNAME` - имя бота
   - `YANDEX_DISK_TOKEN` - OAuth токен Yandex Disk

3. Запустите через Docker Compose:
```bash
docker-compose up -d
```

## Локальная разработка

```bash
mvn spring-boot:run
```

## Структура БД

- `knife_models` - модели ножей
- `tags` - теги для категоризации
- `knife_tags` - связь ножей и тегов
- `knife_alternatives` - альтернативные модели
- `user_submissions` - пользовательские заявки

## Команды бота

- `/start` - главное меню и список команд
- `/list` - показать список всех фотографий на Яндекс.Диске
- `/get <имя_файла>` - получить конкретную фотографию с Яндекс.Диска
- Отправка фото - автоматическая загрузка на Яндекс.Диск

## API Yandex Disk

Публичная папка: https://disk.yandex.ru/d/nZycyfROkwu9gg
