package com.storyteller.repository;

import com.storyteller.model.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserEventRepository extends JpaRepository<UserEvent, UUID> {

    @Query("SELECT e FROM UserEvent e WHERE e.createdAt >= :since")
    List<UserEvent> findRecentEvents(Instant since);
}
