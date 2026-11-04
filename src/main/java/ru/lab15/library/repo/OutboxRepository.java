package ru.lab15.library.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.lab15.library.domain.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Пачка неотправленных событий с блокировкой строк.
     * SKIP LOCKED: если сервис запущен в нескольких репликах, каждая берёт свои строки
     * и не ждёт чужие — одно событие не публикуется параллельно двумя репликами.
     */
    @Query(value = """
            select * from outbox_events
            where published_at is null
            order by created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
