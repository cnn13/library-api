package ru.lab15.library.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.lab15.library.domain.Reader;

public interface ReaderRepository extends JpaRepository<Reader, Long> {

    boolean existsByEmailIgnoreCase(String email);
}
