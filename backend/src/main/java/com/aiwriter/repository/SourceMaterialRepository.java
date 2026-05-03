package com.aiwriter.repository;

import com.aiwriter.model.SourceMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SourceMaterialRepository extends JpaRepository<SourceMaterial, Long> {
    List<SourceMaterial> findByTemplateIdOrderByCreatedAtDesc(Long templateId);
}
