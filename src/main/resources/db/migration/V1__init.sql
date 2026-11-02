-- Схема библиотеки: авторы 1 — N книги
CREATE TABLE authors (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    country     VARCHAR(60),
    birth_year  INTEGER      CHECK (birth_year BETWEEN 1000 AND 2100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE books (
    id              BIGSERIAL    PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    author_id       BIGINT       NOT NULL REFERENCES authors (id) ON DELETE RESTRICT,
    published_year  INTEGER      NOT NULL CHECK (published_year BETWEEN 1450 AND 2100),
    isbn            VARCHAR(13)  UNIQUE,
    pages           INTEGER      CHECK (pages BETWEEN 1 AND 10000),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_books_author ON books (author_id);
CREATE INDEX idx_books_year ON books (published_year);
