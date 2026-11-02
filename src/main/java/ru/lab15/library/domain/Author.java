package ru.lab15.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "authors")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 60)
    private String country;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Author() {
    }

    public Author(String name, String country, Integer birthYear) {
        this.name = name;
        this.country = country;
        this.birthYear = birthYear;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public Integer getBirthYear() { return birthYear; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
