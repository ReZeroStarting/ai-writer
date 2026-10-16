package com.aiwriter.repository;

import com.aiwriter.model.Patch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchRepository extends JpaRepository<Patch, Long> {
    void deleteByConflictId(Long conflictId);
}
