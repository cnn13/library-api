package ru.lab15.library.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.lab15.library.domain.Author;

public interface AuthorRepository extends JpaRepository<Author, Long> {
}
