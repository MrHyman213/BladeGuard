# Инструкция по развертыванию

## Предварительные требования

1. Docker и Docker Compose
2. Токены Telegram ботов (получить у @BotFather)
3. OAuth токен Яндекс.Диска с правами `cloud_api:disk.write`

## Получение токена Яндекс.Диска

1. Перейдите на https://oauth.yandex.ru/
2. Создайте новое приложение
3. Выберите права доступа: `cloud_api:disk.write`
4. Получите OAuth токен

## Настройка

1. Скопируйте `.env.example` в `.env`:
```bash
copy .env.example .env
```

2. Заполните переменные в `.env`:
```env
# Пользовательский бот
TELEGRAM_BOT_TOKEN=your_bot_token_here
TELEGRAM_BOT_USERNAME=your_bot_username

# Админ-бот
TELEGRAM_ADMIN_BOT_TOKEN=your_admin_bot_token_here
TELEGRAM_ADMIN_BOT_USERNAME=your_admin_bot_username

# Яндекс.Диск
YANDEX_DISK_TOKEN=your_yandex_disk_token
YANDEX_DISK_FOLDER=/certificates

# База данных (можно оставить по умолчанию)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=knife_certs
DB_USER=postgres
DB_PASSWORD=postgres
```

## Запуск через Docker Compose

```bash
# Запуск всех сервисов
docker-compose up -d

# Просмотр логов
docker-compose logs -f app

# Остановка
docker-compose down

# Остановка с удалением данных БД
docker-compose down -v
```

## Локальная разработка

1. Запустите PostgreSQL:
```bash
docker-compose up -d postgres
```

2. Запустите приложение:
```bash
mvn spring-boot:run
```

## Проверка работы

1. Откройте пользовательского бота в Telegram
2. Отправьте команду `/start`
3. Отправьте фото для создания заявки
4. Откройте админ-бота
5. Используйте `/pending` для просмотра заявок

## Структура папок на Яндекс.Диске

```
/certificates/
  ├── offer/          # Заявки от пользователей
  └── [другие файлы]  # Загрузки админа
```

## Миграции БД

Миграции применяются автоматически при запуске приложения через Liquibase.

Файлы миграций находятся в:
- `src/main/resources/db/changelog/db.changelog-master.xml`
- `src/main/resources/db/changelog/changes/001-initial-schema.xml`

## Подключение к БД

```bash
# Через Docker
docker exec -it knife-certs-db psql -U postgres -d knife_certs

# Локально
psql -h localhost -U postgres -d knife_certs
```

## Полезные команды

```bash
# Пересборка образа
docker-compose build

# Просмотр логов конкретного сервиса
docker-compose logs -f postgres
docker-compose logs -f app

# Перезапуск сервиса
docker-compose restart app

# Проверка статуса
docker-compose ps
```

## Troubleshooting

### Ошибка подключения к БД
- Убедитесь, что PostgreSQL запущен: `docker-compose ps`
- Проверьте логи: `docker-compose logs postgres`

### Ошибка загрузки на Яндекс.Диск
- Проверьте права токена OAuth
- Убедитесь, что токен не истек
- Проверьте логи: `docker-compose logs app`

### Бот не отвечает
- Проверьте токены ботов в `.env`
- Убедитесь, что боты запущены у @BotFather
- Проверьте логи приложения

## Обновление

```bash
# Остановка
docker-compose down

# Получение изменений
git pull

# Пересборка и запуск
docker-compose up -d --build
```
