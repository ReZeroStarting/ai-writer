package com.aiwriter.service;

import com.aiwriter.dto.request.DocumentRequest;
import com.aiwriter.dto.response.DocumentResponse;
import com.aiwriter.model.Document;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.DocumentRepository;
import com.aiwriter.repository.ProjectRepository;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository docRepo;
    private final ProjectRepository projectRepo;

    public List<DocumentResponse> listByProject(Long userId, Long projectId) {
        return docRepo.findByProjectIdOrderByUpdatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    public DocumentResponse create(Long userId, Long projectId, DocumentRequest req) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new NotFoundException("项目不存在"));
        Document doc = Document.builder()
                .project(project).user(User.builder().id(userId).build())
                .title(req.getTitle()).content(req.getContent() != null ? req.getContent() : "")
                .build();
        return toResponse(docRepo.save(doc));
    }

    public DocumentResponse get(Long userId, Long docId) {
        return toResponse(findOwned(userId, docId));
    }

    public DocumentResponse update(Long userId, Long docId, DocumentRequest req) {
        Document doc = findOwned(userId, docId);
        doc.setTitle(req.getTitle()); doc.setContent(req.getContent());
        return toResponse(docRepo.save(doc));
    }

    public void delete(Long userId, Long docId) {
        findOwned(userId, docId);
        docRepo.deleteById(docId);
    }

    private Document findOwned(Long userId, Long docId) {
        Document doc = docRepo.findById(docId)
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        if (!doc.getUser().getId().equals(userId))
            throw new ForbiddenException("无权操作此文档");
        return doc;
    }

    private DocumentResponse toResponse(Document d) {
        return DocumentResponse.builder()
                .id(d.getId()).projectId(d.getProject().getId())
                .title(d.getTitle()).content(d.getContent())
                .createdAt(d.getCreatedAt().toString())
                .updatedAt(d.getUpdatedAt().toString()).build();
    }
}
