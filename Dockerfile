# Используем официальный образ OpenJDK 21 как базовый
FROM openjdk:21-jdk-slim

# Устанавливаем рабочую директорию внутри контейнера
WORKDIR /app

# Копируем файл сборки (например, Maven или Gradle) и зависимости
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Если используешь Gradle, раскомментируй следующие строки:
# COPY gradlew ./
# COPY gradle gradle

# Делаем mvnw исполняемым
RUN chmod +x ./mvnw

# Устанавливаем зависимости (опционально, для ускорения кэширования)
# Для Maven:
RUN ./mvnw dependency:go-offline -B
# Для Gradle:
# RUN ./gradlew dependencies --no-daemon

# Копируем исходный код
COPY src ./src

# Собираем приложение
# Для Maven:
RUN ./mvnw package -DskipTests -B
# Для Gradle:
# RUN ./gradlew build --no-daemon

# Указываем JAR-файл, который был собран
# Предполагается, что JAR называется app.jar и лежит в target/
RUN mv target/*.jar app.jar

# Порт, на котором работает приложение
EXPOSE 8080

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]