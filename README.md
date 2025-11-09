# Kafka Event-Driven Microservices Platform

Пример из статьи ["Kafka для начинающих: работа с оффсетами на практике"](https://habr.com/ru/articles/965218/) на Хабре

## Состав
- `order-service` - идемпотентный продюсер
- `inventory-service` — идемпотентный продюсер и консьюмер
- `notification-service` — идемпотентный консьюмер
- `analytics-service` — дополнительный консьюмер без свойства идемпотентности
- `docker-compose.yml` — кластер Kafka из трёх брокеров + Kafka-UI + базы данных PostgreSQL

## Как запустить

1. Клонировать репозиторий
```bash
git clone https://github.com/Mitohondriyaa/kafka-article-4
cd kafka-article-4
```
2. Поднять контейнеры
```bash
docker-compose up -d
```
3. Выполнить SQL-скрипты в папке `sql-scripts`
4. Запустить микросервисы (при желании можно запускать консьюмеров в нескольких инстансах)
```bash
cd order-service
./mvnw spring-boot:run

cd inventory-service
./mvnw spring-boot:run

cd notification-service
./mvnw spring-boot:run -Dserver.port=8081

cd analytics-service
./mvnw spring-boot:run -Dserver.port=8082
```
5. Развлекаться
