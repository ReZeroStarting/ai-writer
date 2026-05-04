package com.aiwriter.repository;

import com.aiwriter.model.FrameworkElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface FrameworkElementRepository extends JpaRepository<FrameworkElement, Long> {
    @Query("SELECT e FROM FrameworkElement e WHERE e.framework.id = :frameworkId AND e.isDeprecated = false AND e.version = (SELECT MAX(e2.version) FROM FrameworkElement e2 WHERE e2.id = e.id) ORDER BY e.type, e.name")
    List<FrameworkElement> findLatestByFrameworkId(Long frameworkId);

    @Query("SELECT e FROM FrameworkElement e WHERE e.id = :elementId ORDER BY e.version DESC")
    List<FrameworkElement> findVersionsByElementId(String elementId);

    @Query("SELECT e FROM FrameworkElement e WHERE e.id = :elementId AND e.isDeprecated = false ORDER BY e.version DESC LIMIT 1")
    Optional<FrameworkElement> findLatestByElementId(String elementId);

    @Query("SELECT MAX(e.version) FROM FrameworkElement e WHERE e.id = :elementId")
    Optional<Integer> findMaxVersionByElementId(String elementId);
}
