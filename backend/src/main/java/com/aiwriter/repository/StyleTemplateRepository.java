package com.aiwriter.repository;

import com.aiwriter.model.StyleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StyleTemplateRepository extends JpaRepository<StyleTemplate, Long> {
    List<StyleTemplate> findByUserIdOrderByCreatedAtDesc(Long userId);
}
