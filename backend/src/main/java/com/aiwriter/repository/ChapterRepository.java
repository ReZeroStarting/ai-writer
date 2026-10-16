package com.aiwriter.repository;

import com.aiwriter.model.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByFrameworkIdOrderByChapterIndexAsc(Long frameworkId);
    Optional<Chapter> findTopByFrameworkIdOrderByChapterIndexDesc(Long frameworkId);
    List<Chapter> findByDocumentId(Long documentId);
    void deleteByFrameworkId(Long frameworkId);
    void deleteByDocumentId(Long documentId);
}
