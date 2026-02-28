# Структура проекта BladeGuard

```
BladeGuard/
│
├── docs/                           # 📚 Документация
│   ├── README.md                   # Навигация по документации
│   ├── QUICKSTART.md              # Быстрый старт за 5 минут
│   ├── ARCHITECTURE.md            # Архитектура системы
│   ├── SUBMISSION_FEATURE.md      # Функционал заявок
│   ├── DEPLOYMENT.md              # Инструкция по развертыванию
│   ├── EXAMPLES.md                # Примеры использования
│   ├── DATABASE_SCHEMA.md         # Схема базы данных
│   ├── CHANGELOG.md               # История изменений
│   ├── TODO.md                    # Планы развития
│   └── SUMMARY.md                 # Резюме проекта
│
├── sql/                           # 🗄️ SQL скрипты
│   └── queries.sql                # Полезные запросы
│
├── src/                           # 💻 Исходный код
│   ├── main/
│   │   ├── java/com/knifecerts/
│   │   │   ├── entity/           # Entity классы
│   │   │   │   ├── Tag.java
│   │   │   │   ├── Submission.java
│   │   │   │   └── Certificate.java
│   │   │   ├── repository/       # Репозитории
│   │   │   │   ├── TagRepository.java
│   │   │   │   ├── SubmissionRepository.java
│   │   │   │   └── CertificateRepository.java
│   │   │   ├── service/          # Сервисы
│   │   │   │   └── SubmissionService.java
│   │   │   ├── AdminBot.java     # Админ-бот
│   │   │   ├── KnifeBot.java     # Пользовательский бот
│   │   │   ├── YandexDiskService.java
│   │   │   ├── BotApplication.java
│   │   │   └── BotConfig.java
│   │   └── resources/
│   │       ├── db/changelog/     # Миграции БД
│   │       │   ├── db.changelog-master.xml
│   │       │   └── changes/
│   │       │       └── 001-initial-schema.xml
│   │       └── application.properties
│   └── test/                     # Тесты (пока пусто)
│
├── target/                        # 🔨 Скомпилированные файлы
│
├── .env                          # 🔐 Переменные окружения (не в git)
├── .env.example                  # Пример конфигурации
├── .gitignore                    # Игнорируемые файлы
├── docker-compose.yml            # 🐳 Docker Compose конфигурация
├── Dockerfile                    # Docker образ
├── pom.xml                       # Maven конфигурация
├── README.md                     # 📖 Главный README
└── PROJECT_STRUCTURE.md          # Этот файл
```

## Описание основных папок

### 📚 docs/
Вся документация проекта. Начните с `docs/README.md` для навигации.

### 💻 src/main/java/
Исходный код приложения:
- `entity/` - JPA сущности (модели данных)
- `repository/` - Репозитории для доступа к БД
- `service/` - Бизнес-логика
- Боты и сервисы в корне пакета

### 🗄️ src/main/resources/
Ресурсы приложения:
- `db/changelog/` - Liquibase миграции БД
- `application.properties` - Конфигурация Spring Boot

### 🐳 Docker файлы
- `docker-compose.yml` - Оркестрация контейнеров (PostgreSQL + App)
- `Dockerfile` - Образ приложения

### 📝 Конфигурация
- `.env` - Секретные переменные (токены, пароли)
- `.env.example` - Шаблон для `.env`
- `pom.xml` - Maven зависимости и плагины

## Быстрая навигация

| Что нужно | Куда идти |
|-----------|-----------|
| Запустить проект | [docs/QUICKSTART.md](docs/QUICKSTART.md) |
| Понять архитектуру | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Изучить БД | [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) |
| Примеры использования | [docs/EXAMPLES.md](docs/EXAMPLES.md) |
| Развернуть в продакшн | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| Посмотреть планы | [docs/TODO.md](docs/TODO.md) |

## Ключевые файлы для разработки

### Backend
- `src/main/java/com/knifecerts/KnifeBot.java` - Пользовательский бот
- `src/main/java/com/knifecerts/AdminBot.java` - Админ-бот
- `src/main/java/com/knifecerts/service/SubmissionService.java` - Логика заявок

### База данных
- `src/main/resources/db/changelog/changes/001-initial-schema.xml` - Схема БД
- `sql/queries.sql` - Полезные запросы

### Конфигурация
- `src/main/resources/application.properties` - Настройки Spring Boot
- `.env` - Переменные окружения
- `docker-compose.yml` - Docker конфигурация

## Размер проекта

- Исходный код: ~12 Java файлов
- Документация: 10 Markdown файлов
- Строк кода: ~1500 (без учета документации)
- Зависимости: Spring Boot, PostgreSQL, Telegram Bots API

---

**Обновлено:** 2026-02-26
