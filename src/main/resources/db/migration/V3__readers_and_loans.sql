-- Читатели и выдача книг
CREATE TABLE readers (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(254) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE loans (
    id           BIGSERIAL   PRIMARY KEY,
    book_id      BIGINT      NOT NULL REFERENCES books (id) ON DELETE RESTRICT,
    reader_id    BIGINT      NOT NULL REFERENCES readers (id) ON DELETE RESTRICT,
    issued_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at       TIMESTAMPTZ NOT NULL,
    returned_at  TIMESTAMPTZ,
    CHECK (due_at > issued_at),
    CHECK (returned_at IS NULL OR returned_at >= issued_at)
);

-- Книга не может быть выдана дважды одновременно: гарантия на уровне БД,
-- работает и при гонке двух запросов, и при нескольких репликах сервиса
CREATE UNIQUE INDEX ux_loans_active_book ON loans (book_id) WHERE returned_at IS NULL;
CREATE INDEX idx_loans_reader_active ON loans (reader_id) WHERE returned_at IS NULL;

INSERT INTO readers (name, email) VALUES
    ('Иван Петров',   'ivan.petrov@library.test'),
    ('Мария Смирнова', 'maria.smirnova@library.test');
