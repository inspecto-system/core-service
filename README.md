# Inspecto · core-service

Основной сервис информационной системы Inspecto: аутентификация, объекты недвижимости, осмотры, чек-листы, дефекты, сравнение осмотров и статистика.

Архитектура, контракты API и стандарты — в репозитории [`platform`](https://github.com/inspecto-system/platform): контракт этого сервиса — `api/core.yaml`, правила качества — `docs/CODE_QUALITY.md`.

## Технологии

Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, PostgreSQL 16, Gradle (wrapper в репозитории).

## Запуск локально

1. Поднять инфраструктуру из `platform`: `docker compose -f deploy/compose.dev.yaml --env-file deploy/.env up -d --wait`.
2. Задать пароли ролей базы (из `platform/deploy/.env`):

   ```bash
   export CORE_DB_APP_PASSWORD=...        # пароль core_app
   export CORE_DB_MIGRATOR_PASSWORD=...   # пароль core_migrator
   ```

3. Запустить: `./gradlew bootRun`. Проверка: `curl http://localhost:8081/health`.

## Проверки качества

```bash
./gradlew spotlessApply   # форматирование (google-java-format)
./gradlew build           # Spotless, Checkstyle, PMD, SpotBugs, тесты (нужен Docker для Testcontainers), JaCoCo
```

Те же проверки выполняет CI (`.github/workflows/ci.yml` → общий workflow `platform/.github/workflows/java-gradle.yml`) плюс Gitleaks, Dependency Review и CodeQL. Отчёты: `build/reports/`.

## Структура

```text
config/            правила Checkstyle, PMD, SpotBugs
src/main/java      код сервиса (пакет dev.inspecto.core)
src/main/resources конфигурация и миграции Flyway (db/migration)
src/test/java      тесты; интеграционные — с настоящим PostgreSQL через Testcontainers
```
