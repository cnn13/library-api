-- Transactional outbox: событие пишется в той же транзакции, что и выдача/возврат книги,
-- а отдельный процесс публикует его в Kafka. Событие не теряется, даже если Kafka недоступна
-- или сервис упал между коммитом в БД и отправкой.
CREATE TABLE outbox_events (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(50)  NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);
-- выборка неотправленных — по частичному индексу, он остаётся маленьким
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- Проекция, которую строит consumer: популярность книг
CREATE TABLE book_stats (
    book_id          BIGINT      PRIMARY KEY REFERENCES books (id) ON DELETE CASCADE,
    times_issued     INTEGER     NOT NULL DEFAULT 0,
    times_returned   INTEGER     NOT NULL DEFAULT 0,
    overdue_returns  INTEGER     NOT NULL DEFAULT 0,
    last_event_at    TIMESTAMPTZ NOT NULL
);

-- Идемпотентность consumer: каждое событие обрабатывается ровно один раз,
-- даже если Kafka доставила его повторно (at-least-once)
CREATE TABLE processed_events (
    event_id      UUID        PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
