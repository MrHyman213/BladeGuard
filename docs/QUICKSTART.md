# Быстрый старт

## За 5 минут до запуска

### 1. Получите токены (2 минуты)

#### Telegram боты
1. Откройте [@BotFather](https://t.me/BotFather) в Telegram
2. Создайте пользовательского бота:
   ```
   /newbot
   Имя: Knife Certificates Bot
   Username: your_knife_bot
   ```
3. Сохраните токен (например: `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`)
4. Создайте админ-бота:
   ```
   /newbot
   Имя: Knife Admin Bot
   Username: your_knife_admin_bot
   ```
5. Сохраните токен админ-бота

#### Яндекс.Диск токен
1. Перейдите на https://oauth.yandex.ru/
2. Нажмите "Зарегистрировать новое приложение"
3. Выберите права: `cloud_api:disk.write`
4. Получите OAuth токен

### 2. Настройте проект (1 минута)

```bash
# Клонируйте репозиторий (если еще не сделали)
git clone <repository-url>
cd BladeGuard

# Скопируйте пример конфигурации
copy .env.example .env

# Откройте .env в текстовом редакторе и заполните:
# - TELEGRAM_BOT_TOKEN
# - TELEGRAM_BOT_USERNAME
# - TELEGRAM_ADMIN_BOT_TOKEN
# - TELEGRAM_ADMIN_BOT_USERNAME
# - YANDEX_DISK_TOKEN
```

### 3. Запустите (2 минуты)

```bash
# Запустите через Docker Compose
docker-compose up -d

# Проверьте логи
docker-compose logs -f app
```

Готово! 🎉

## Первые шаги

### Тест пользовательского бота

1. Откройте вашего бота в Telegram
2. Отправьте `/start`
3. Отправьте любое фото
4. Добавьте описание или используйте `/skip`

### Тест админ-бота

1. Откройте админ-бота в Telegram
2. Отправьте `/start`
3. Используйте `/pending` для просмотра заявок
4. Используйте `/view 1` для просмотра первой заявки

## Пример использования

### Создание заявки (пользователь)
```
Вы: [отправляете фото ножа]
Бот: Загружаю фото на Яндекс.Диск...
Бот: ✅ Фото загружено!
     Теперь отправьте описание или /skip

Вы: Охотничий нож, сталь 95Х18
Бот: ✅ Заявка успешно создана с описанием!
```

### Обработка заявки (админ)
```
Вы: /pending
Бот: 📋 Заявки на рассмотрении:
     ID: 1
     От: @username
     Описание: Охотничий нож, сталь 95Х18

Вы: /view 1
Бот: [показывает фото с деталями]

Вы: /tags 1 охотничий,95х18
Бот: ✅ Теги добавлены к заявке #1

Вы: /approve 1
Бот: ✅ Заявка #1 одобрена
```

## Что дальше?

- 📖 Читайте [EXAMPLES.md](EXAMPLES.md) для больше примеров
- 🚀 Смотрите [DEPLOYMENT.md](DEPLOYMENT.md) для продакшн развертывания
- 📊 Изучите [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) для понимания структуры БД
- 💡 Проверьте [TODO.md](TODO.md) для идей улучшений

## Проблемы?

### Бот не отвечает
```bash
# Проверьте логи
docker-compose logs app

# Перезапустите
docker-compose restart app
```

### Ошибка подключения к БД
```bash
# Проверьте статус PostgreSQL
docker-compose ps postgres

# Перезапустите БД
docker-compose restart postgres
```

### Ошибка загрузки на Яндекс.Диск
- Проверьте права токена OAuth
- Убедитесь, что токен не истек
- Проверьте переменную `YANDEX_DISK_TOKEN` в `.env`

## Полезные команды

```bash
# Просмотр логов
docker-compose logs -f

# Остановка
docker-compose down

# Перезапуск
docker-compose restart

# Пересборка
docker-compose up -d --build

# Подключение к БД
docker exec -it knife-certs-db psql -U postgres -d knife_certs
```

## Контакты

Нужна помощь? Создайте Issue в репозитории!

---

**Время на запуск:** ~5 минут  
**Сложность:** Легко  
**Требования:** Docker, Telegram аккаунт, Яндекс.Диск аккаунт
