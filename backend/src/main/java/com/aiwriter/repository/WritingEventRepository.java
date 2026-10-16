package com.aiwriter.repository;

import com.aiwriter.model.WritingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface WritingEventRepository extends JpaRepository<WritingEvent, Long> {
    List<WritingEvent> findByFrameworkIdOrderByTimestampDesc(Long frameworkId);

    @Query("SELECT e FROM WritingEvent e WHERE e.framework.id = :frameworkId AND e.timestamp <= :until ORDER BY e.timestamp ASC")
    List<WritingEvent> findByFrameworkIdUpTo(Long frameworkId, LocalDateTime until);

    void deleteByFrameworkId(Long frameworkId);
}
