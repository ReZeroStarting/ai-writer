package com.aiwriter.repository;

import com.aiwriter.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByProjectIdOrderByUpdatedAtDesc(Long projectId);
}
