# Использование новой схемы базы данных

## Быстрый старт

### 1. Переключение на новую схему

Установите переменные окружения:
```bash
export BOT_VERSION=new
export ADMIN_BOT_VERSION=new
```

Или в application.properties:
```properties
bot.version=new
admin.bot.version=new
```

### 2. Запуск приложения

```bash
./mvnw spring-boot:run
```

Liquibase автоматически создаст новые таблицы.

## Основные компоненты

### Сервисы

1. **SubmissionBufferService** - управление заявками в буфере
   - `createSubmission()` - создание новой заявки
   - `approveSubmission()` - одобрение заявки (создает Knife)
   - `rejectSubmission()` - отклонение заявки

2. **KnifeService** - работа с одобренными сертификатами
   - `getAllBrandsWithCertificates()` - получение брендов с сертификатами
   - `getCertificatesByBrand()` - сертификаты по бренду
   - `getAlternatives()` - альтернативы для ножа

### Модели

1. **SubmissionBuffer** - заявка в буфере
   - Содержит данные заявки до одобрения
   - Связана с BufferAlternative через @ElementCollection

2. **Knife** - одобренный сертификат
   - Связан с Brand и KnifeModel
   - Имеет many-to-many связи с альтернативами

3. **BufferAlternative** - альтернатива в заявке
   - Embeddable класс для хранения в submissions_buffer

## Логика работы

### Пользовательский бот (KnifeBotNew)

1. **Главное меню**: показывает бренды с сертификатами
2. **Выбор бренда**: показывает модели ножей этого бренда
3. **Выбор модели**: 
   - Если есть сертификат → показывает фото + альтернативы
   - Если нет сертификата → показывает альтернативы с сертификатами
4. **Загрузка**: создает заявку в SubmissionBuffer

### Админ-бот (AdminBotNew)

1. **Ожидающие заявки**: показывает список из submissions_buffer
2. **Просмотр заявки**: показывает детали + кнопки одобрить/отклонить
3. **Одобрение**:
   - Если есть альтернативы → показывает меню подтверждения
   - Создает записи в knives, brands, knife_models
   - Создает связи в alternatives
   - Удаляет заявку из buffer
4. **Отклонение**: просто удаляет заявку из buffer

## Преимущества новой схемы

1. **Простота**: четкое разделение заявок и одобренных сертификатов
2. **Производительность**: нет необходимости фильтровать по статусу
3. **Гибкость**: легко добавлять новые поля и связи
4. **Надежность**: меньше состояний, меньше ошибок

## Примеры использования

### Создание заявки
```java
SubmissionBuffer submission = submissionBufferService.createSubmission(
    userId, username, modelName, brandName, index, photoPath
);

// Добавление альтернативы
submissionBufferService.addAlternativeToSubmission(
    submission.getId(), "AltModel", "AltBrand"
);
```

### Одобрение заявки
```java
Knife knife = submissionBufferService.approveSubmission(submissionId);
// Автоматически создаются Brand, KnifeModel, связи alternatives
```

### Поиск сертификатов
```java
List<Brand> brands = knifeService.getAllBrandsWithCertificates();
List<Knife> knives = knifeService.getCertificatesByBrand("Nike");
List<Knife> alternatives = knifeService.getAlternatives(knifeId);
```