# Library API

Учебный микросервис (лабораторная 15, 8k33-27-chernov): REST API библиотеки — авторы и книги.

- **Java 21, Spring Boot 3.5** (Web MVC, Data JPA, Validation, Actuator)
- **PostgreSQL 17**, схема и начальные данные — миграции **Flyway**
- **Docker** (multi-stage) + **docker compose**
- Ошибки — в формате **RFC 7807** (`application/problem+json`), журнал запросов с `requestId`
- Документация API — **Swagger UI** (springdoc-openapi)
- Проверка — **Postman**-коллекция с автотестами (`postman/`)
- Развёртывание в **Kubernetes** (`k8s/`), мониторинг **Prometheus + Grafana**, **CI/CD** в GitHub Actions

## Запуск

```bash
git clone https://github.com/cnn13/library-api.git
cd library-api
cp .env.example .env        # задайте DB_PASSWORD
docker compose up -d --build
docker compose ps           # оба сервиса должны быть healthy
```

| Что | Адрес |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |

Порт на хосте меняется переменной `API_PORT` в `.env`. Порт PostgreSQL наружу не публикуется.

Остановка: `docker compose down` (данные остаются в томе `db-data`), полная очистка: `docker compose down -v`.

## API

| Метод | Путь | Описание | Успех |
|---|---|---|---|
| GET | `/api/v1/authors` | список авторов (с числом книг) | 200 |
| GET | `/api/v1/authors/{id}` | автор по id | 200 |
| POST | `/api/v1/authors` | создать автора | 201 + `Location` |
| DELETE | `/api/v1/authors/{id}` | удалить автора (409, если есть книги) | 204 |
| GET | `/api/v1/books?authorId=&year=&page=&size=` | список книг с фильтрами и страницами | 200 |
| GET | `/api/v1/books/{id}` | книга по id | 200 |
| POST | `/api/v1/books` | добавить книгу | 201 + `Location` |
| PUT | `/api/v1/books/{id}` | заменить книгу целиком | 200 |
| PATCH | `/api/v1/books/{id}` | изменить отдельные поля | 200 |
| DELETE | `/api/v1/books/{id}` | удалить книгу | 204 |

Пример:

```bash
curl -s -X POST localhost:8080/api/v1/books -H 'Content-Type: application/json' \
  -d '{"title":"Идиот","authorId":2,"year":1869,"isbn":"978-5-04-116618-5","pages":640}'
```

## Обработка ошибок

Сервис не падает на некорректных данных: каждая ошибка — HTTP-код и JSON-описание:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 422,
  "detail": "Request contains invalid fields",
  "instance": "/api/v1/books",
  "errors": { "year": "must be greater than or equal to 1450" },
  "timestamp": "2026-11-02T18:40:00.123+03:00",
  "requestId": "3f2a9c1e"
}
```

| Ситуация | Код |
|---|---|
| битый JSON, неизвестное поле, неверный тип поля, пустой PATCH, неверный параметр запроса (`?size=1000`, `/books/abc`) | 400 |
| запись не найдена, неизвестный путь | 404 |
| метод не поддерживается (`DELETE /api/v1/books`) | 405 |
| дубликат ISBN, удаление автора с книгами | 409 |
| неподдерживаемый `Content-Type` | 415 |
| нарушены правила полей (пустое название, год вне диапазона/в будущем, неверный ISBN, несуществующий автор) | 422 |
| непредвиденная ошибка (стек — только в логе) | 500 |
| СУБД недоступна | 503 |

## Логирование

Каждый запрос получает `requestId` (из заголовка `X-Request-Id` или сгенерированный); он возвращается в заголовке ответа, в теле ошибок и попадает во все строки лога:

```
2026-11-02T18:40:00.130+03:00 INFO  [3f2a9c1e] http : POST /api/v1/books -> 201 (35 ms)
2026-11-02T18:40:01.410+03:00 WARN  [7b1d0e42] r.l.l.api.ErrorHandler : request rejected: status=409 path=/api/v1/books detail='Book with ISBN 9785170903352 already exists'
```

Логи: `docker compose logs -f api`.

## Docker

`Dockerfile` — три стадии:

1. **deps** — `maven:3.9-eclipse-temurin-21`, скачивание зависимостей (слой кэшируется, пока не меняется `pom.xml`);
2. **build** — компиляция, **unit-тесты** (`mvn package`; упавший тест — образ не собирается), распаковка jar на слои Spring Boot;
3. **runtime** — `eclipse-temurin:21-jre-alpine`: только JRE и приложение, запуск от непривилегированного пользователя, `HEALTHCHECK`.

`compose.yaml`: сервис `db` (PostgreSQL с healthcheck и томом) и `api`, который стартует только после того, как база стала healthy; общая сеть `backend`, ограничения памяти.

## Kubernetes (лабораторная 16)

Манифесты — в `k8s/`: Namespace `library`, ConfigMap, PostgreSQL (StatefulSet + PVC + Service), API (Deployment из 2 реплик с пробами startup/readiness/liveness + Service NodePort 30080).

```bash
minikube start
docker build -t library-api:1.2.0 . && minikube image load library-api:1.2.0
kubectl apply -f k8s/00-namespace.yaml
kubectl -n library create secret generic library-db --from-literal=password=<пароль>
kubectl apply -f k8s/01-config.yaml -f k8s/02-postgres.yaml -f k8s/03-api.yaml
curl http://$(minikube ip):30080/api/v1/books
```

Мониторинг (`k8s/monitoring/`): Prometheus (`prometheus-community/prometheus`) и Grafana (`grafana-community/grafana`) с готовыми дашбордами и дашбордом `Library API`. Метрики приложения — `/actuator/prometheus`; Prometheus находит поды по аннотации `prometheus.io/scrape: "true"`.

```bash
kubectl create namespace monitoring
helm install prometheus prometheus-community/prometheus -n monitoring -f k8s/monitoring/prometheus-values.yaml
helm install grafana grafana-community/grafana -n monitoring -f k8s/monitoring/grafana-values.yaml \
  --set adminPassword=<пароль> --set-file dashboards.default.library-api.json=k8s/monitoring/library-api-dashboard.json
kubectl -n monitoring port-forward svc/grafana 3000:80
```

CI/CD (`.github/workflows/ci-cd.yml`): на каждый push — сборка образа с тестами, развёртывание в кластер kind теми же манифестами и smoke-тест через Service.

## Postman

`postman/library-api.postman_collection.json` — импортировать в Postman (Import → файл), при необходимости поменять переменную коллекции `baseUrl`, затем **Run collection**. Коллекция создаёт автора и книгу, проверяет все виды ошибок и удаляет за собой созданные записи, поэтому её можно запускать многократно.

Из консоли то же самое: `npx newman run postman/library-api.postman_collection.json --env-var baseUrl=http://localhost:8080`.
