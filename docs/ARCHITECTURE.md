# Архитектура системы

## Общая схема

```
┌─────────────────┐         ┌─────────────────┐
│  Пользователи   │         │ Администраторы  │
│   (Telegram)    │         │   (Telegram)    │
└────────┬────────┘         └────────┬────────┘
         │                           │
         │ Telegram API              │ Telegram API
         │                           │
         ▼                           ▼
┌─────────────────┐         ┌─────────────────┐
│   KnifeBot      │         │   AdminBot      │
│ (Spring Boot)   │         │ (Spring Boot)   │
└────────┬────────┘         └────────┬────────┘
         │                           │
         │                           │
         └───────────┬───────────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │  SubmissionService    │
         │  (Business Logic)     │
         └───────────┬───────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
┌─────────────────┐    ┌─────────────────┐
│  Repositories   │    │ YandexDiskService│
│  (Data Access)  │    │  (File Storage)  │
└────────┬────────┘    └────────┬────────┘
         │                      │
         ▼                      ▼
┌─────────────────┐    ┌─────────────────┐
│   PostgreSQL    │    │  Yandex.Disk    │
│   (Database)    │    │  (File Storage) │
└─────────────────┘    └─────────────────┘
```

## Компоненты системы

### 1. Presentation Layer (Боты)

#### KnifeBot (Пользовательский бот)
**Назначение:** Взаимодействие с обычными пользователями

**Функции:**
- Прием фотографий от пользователей
- Создание заявок на добавление сертификатов
- Просмотр существующих сертификатов
- Получение фотографий из хранилища

**Технологии:**
- TelegramLongPollingBot
- Spring Component

#### AdminBot (Админ-бот)
**Назначение:** Управление заявками администраторами

**Функции:**
- Просмотр списка заявок
- Детальный просмотр заявки с фото
- Редактирование описаний
- Управление тегами
- Одобрение/отклонение заявок
- Прямая загрузка файлов

**Технологии:**
- TelegramLongPollingBot
- Spring Component

### 2. Business Logic Layer (Сервисы)

#### SubmissionService
**Назначение:** Бизнес-логика работы с заявками

**Методы:**
- `createSubmission()` - создание заявки
- `getPendingSubmissions()` - получение заявок на рассмотрении
- `updateAdminDescription()` - обновление описания админа
- `addTagsToSubmission()` - добавление тегов
- `approveSubmission()` - одобрение заявки
- `rejectSubmission()` - отклонение заявки

**Технологии:**
- Spring Service
- Transactional management

#### YandexDiskService
**Назначение:** Работа с файловым хранилищем

**Методы:**
- `uploadPhoto()` - загрузка в основную папку
- `uploadPhotoToOffer()` - загрузка в папку offer
- `downloadPhoto()` - скачивание фото
- `listPhotos()` - список файлов
- `ensureFolderExists()` - создание папок

**Технологии:**
- Spring Service
- RestTemplate для HTTP запросов
- Yandex Disk REST API

### 3. Data Access Layer (Репозитории)

#### SubmissionRepository
**Назначение:** Доступ к данным заявок

**Методы:**
- `findByStatus()` - поиск по статусу
- `findByUserId()` - поиск по пользователю
- Стандартные CRUD операции

#### TagRepository
**Назначение:** Доступ к данным тегов

**Методы:**
- `findByName()` - поиск по имени
- Стандартные CRUD операции

#### CertificateRepository
**Назначение:** Доступ к данным сертификатов

**Методы:**
- Стандартные CRUD операции

**Технологии:**
- Spring Data JPA
- JpaRepository

### 4. Data Layer (Хранилище)

#### PostgreSQL
**Назначение:** Реляционная база данных

**Таблицы:**
- `submissions` - заявки
- `tags` - теги
- `submission_tags` - связь заявок и тегов
- `certificates` - сертификаты
- `certificate_tags` - связь сертификатов и тегов

**Технологии:**
- PostgreSQL 16
- Liquibase для миграций

#### Yandex.Disk
**Назначение:** Файловое хранилище

**Структура:**
```
/certificates/
  ├── offer/              # Заявки пользователей
  │   └── offer_*.jpg
  └── photo_*.jpg         # Загрузки админа
```

## Потоки данных

### Создание заявки пользователем

```
Пользователь → Telegram API → KnifeBot
                                  ↓
                          YandexDiskService
                                  ↓
                            Yandex.Disk
                                  ↓
                          SubmissionService
                                  ↓
                        SubmissionRepository
                                  ↓
                            PostgreSQL
```

### Обработка заявки администратором

```
Админ → Telegram API → AdminBot
                          ↓
                  SubmissionService
                          ↓
              ┌───────────┴───────────┐
              ▼                       ▼
    SubmissionRepository    YandexDiskService
              ▼                       ▼
        PostgreSQL              Yandex.Disk
```

## Паттерны проектирования

### 1. Repository Pattern
Абстракция доступа к данным через интерфейсы репозиториев.

### 2. Service Layer Pattern
Бизнес-логика вынесена в отдельный слой сервисов.

### 3. Dependency Injection
Все зависимости внедряются через Spring DI.

### 4. Strategy Pattern
Разные стратегии загрузки файлов (основная папка / offer).

### 5. State Pattern
Управление состоянием пользователя при создании заявки.

## Технологический стек

### Backend
- **Framework:** Spring Boot 3.2.2
- **Language:** Java 21
- **Build Tool:** Maven
- **ORM:** Spring Data JPA / Hibernate
- **Database:** PostgreSQL 16
- **Migrations:** Liquibase
- **Bot API:** TelegramBots 6.9.7.1

### Infrastructure
- **Containerization:** Docker
- **Orchestration:** Docker Compose
- **File Storage:** Yandex.Disk API

### Development
- **Version Control:** Git
- **IDE:** IntelliJ IDEA / VS Code
- **Testing:** JUnit (planned)

## Масштабируемость

### Текущая архитектура
- Монолитное приложение
- Один экземпляр бота
- Одна база данных

### Возможности масштабирования

#### Горизонтальное масштабирование
```
┌─────────┐     ┌─────────┐     ┌─────────┐
│ Bot #1  │     │ Bot #2  │     │ Bot #3  │
└────┬────┘     └────┬────┘     └────┬────┘
     │               │               │
     └───────────────┼───────────────┘
                     │
              ┌──────▼──────┐
              │ Load Balancer│
              └──────┬──────┘
                     │
              ┌──────▼──────┐
              │  Database   │
              └─────────────┘
```

#### Микросервисная архитектура
```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Bot Service │  │Submission Svc│  │ Storage Svc  │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                 │                 │
       └─────────────────┼─────────────────┘
                         │
                  ┌──────▼──────┐
                  │ Message Bus │
                  │   (Kafka)   │
                  └─────────────┘
```

## Безопасность

### Текущие меры
- Переменные окружения для секретов
- OAuth токены для API
- Валидация входных данных

### Планируемые улучшения
- Whitelist администраторов
- Rate limiting
- Шифрование чувствительных данных
- Аудит логирование
- HTTPS для всех соединений

## Мониторинг и логирование

### Текущее состояние
- Java Logging Framework
- Docker logs
- PostgreSQL logs

### Планируемые улучшения
- Centralized logging (ELK Stack)
- Metrics (Prometheus + Grafana)
- Health checks
- Alerting (PagerDuty / Slack)

## Производительность

### Текущие характеристики
- Синхронная обработка запросов
- Блокирующий I/O
- Без кэширования

### Оптимизации
- Асинхронная обработка файлов
- Кэширование часто запрашиваемых данных (Redis)
- Connection pooling для БД
- CDN для статических файлов

## Развертывание

### Development
```bash
mvn spring-boot:run
```

### Production
```bash
docker-compose up -d
```

### CI/CD (Planned)
```
GitHub → GitHub Actions → Docker Hub → Production Server
```

## Резервное копирование

### База данных
```bash
# Ежедневный бэкап
docker exec knife-certs-db pg_dump -U postgres knife_certs > backup_$(date +%Y%m%d).sql
```

### Файлы
- Автоматическое резервное копирование через Yandex.Disk
- Версионирование файлов

## Disaster Recovery

### RTO (Recovery Time Objective)
- Целевое время восстановления: 1 час

### RPO (Recovery Point Objective)
- Целевая точка восстановления: 24 часа

### План восстановления
1. Восстановление БД из бэкапа
2. Перезапуск контейнеров
3. Проверка работоспособности
4. Уведомление пользователей

---

**Версия архитектуры:** 1.0  
**Дата:** 2026-02-26  
**Статус:** Актуально
