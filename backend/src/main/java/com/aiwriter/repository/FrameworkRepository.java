package com.aiwriter.repository;

import com.aiwriter.model.Framework;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FrameworkRepository extends JpaRepository<Framework, Long> {
    Optional<Framework> findByProjectId(Long projectId);
}
