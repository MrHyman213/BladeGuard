# Автоматическая настройка базы данных

Приложение **автоматически** определяет и настраивает PostgreSQL при запуске.

## Как это работает

При запуске `mvn spring-boot:run` приложение:

1. **Пробует подключиться к Docker контейнеру** (`postgres:5432`)
   - Если успешно → использует Docker PostgreSQL
   
2. **Если Docker недоступен, пробует локальный PostgreSQL** (`localhost:5432`)
   - Если успешно → использует локальный PostgreSQL

3. **Проверяет существование БД `knife_certificates`**
   - Если нет → создаёт автоматически
   - Если есть → использует существующую

4. **Запускает Liquibase** для создания таблиц

## Варианты запуска

### Вариант 1: С Docker (рекомендуется)

```bash
# Запустить только PostgreSQL в Docker
docker-compose up -d postgres

# Запустить приложение локально
mvn spring-boot:run
```

Приложение автоматически найдёт Docker контейнер и создаст БД.

### Вариант 2: С локальным PostgreSQL

```bash
# Убедитесь, что PostgreSQL запущен локально на порту 5432
# Затем просто запустите:
mvn spring-boot:run
```

Приложение автоматически найдёт локальный PostgreSQL и создаст БД.

### Вариант 3: Всё в Docker

```bash
docker-compose up -d
```

И приложение, и PostgreSQL запустятся в контейнерах.

## Логи

При запуске вы увидите:

```
Проверка существования базы данных: knife_certificates
Попытка подключения к PostgreSQL на postgres:5432...
✅ Подключение к postgres:5432 успешно
✅ База данных 'knife_certificates' уже существует на postgres
Используется база данных: jdbc:postgresql://postgres:5432/knife_certificates
```

Или:

```
Попытка подключения к PostgreSQL на postgres:5432...
Не удалось подключиться к postgres:5432 - Connection refused
Попытка подключения к PostgreSQL на localhost:5432...
✅ Подключение к localhost:5432 успешно
База данных 'knife_certificates' не найдена. Создаю...
✅ База данных 'knife_certificates' успешно создана на localhost
```

## Требования

- PostgreSQL 12+ (Docker или локальный)
- Пользователь `postgres` с паролем `postgres` (настраивается в `.env`)
- Порт 5432 доступен

## Настройка

Параметры в `.env`:

```env
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
```

Если у вас другие учётные данные - измените их в `.env`.
