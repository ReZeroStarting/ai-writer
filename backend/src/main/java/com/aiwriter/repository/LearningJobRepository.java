package com.aiwriter.repository;

import com.aiwriter.model.LearningJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningJobRepository extends JpaRepository<LearningJob, Long> {
    List<LearningJob> findByTemplateIdAndUserIdOrderByCreatedAtDesc(Long templateId, Long userId);
    List<LearningJob> findByUserIdOrderByCreatedAtDesc(Long userId);
}
