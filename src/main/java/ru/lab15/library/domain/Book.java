package ru.lab15.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    @Column(name = "published_year", nullable = false)
    private Integer year;

    @Column(length = 13, unique = true)
    private String isbn;

    private Integer pages;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Book() {
    }

    public Book(String title, Author author, Integer year, String isbn, Integer pages) {
        this.title = title;
        this.author = author;
        this.year = year;
        this.isbn = isbn;
        this.pages = pages;
    }

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Author getAuthor() { return author; }
    public Integer getYear() { return year; }
    public String getIsbn() { return isbn; }
    public Integer getPages() { return pages; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setAuthor(Author author) { this.author = author; }
    public void setYear(Integer year) { this.year = year; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setPages(Integer pages) { this.pages = pages; }
}
