package com.aiwriter.repository;

import com.aiwriter.model.Conflict;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConflictRepository extends JpaRepository<Conflict, Long> {
    List<Conflict> findByFrameworkIdOrderByCreatedAtDesc(Long frameworkId);
    List<Conflict> findByChapterId(Long chapterId);
}
