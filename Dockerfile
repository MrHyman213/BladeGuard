FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Устанавливаем Maven
RUN apk add --no-cache maven

# Копируем pom.xml для кеширования зависимостей
COPY pom.xml ./

# Загружаем зависимости (кешируется если pom.xml не изменился)
RUN mvn dependency:go-offline -B

# Копируем исходный код
COPY src ./src

# Собираем приложение
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем jar файл
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Настройки JVM для контейнера
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]
