package com.aiwriter.service;

import com.aiwriter.dto.request.DocumentRequest;
import com.aiwriter.dto.response.DocumentResponse;
import com.aiwriter.model.Document;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.ChapterRepository;
import com.aiwriter.repository.ConflictRepository;
import com.aiwriter.repository.DocumentRepository;
import com.aiwriter.repository.PatchRepository;
import com.aiwriter.repository.ProjectRepository;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository docRepo;
    private final ProjectRepository projectRepo;
    private final ChapterRepository chapterRepo;
    private final ConflictRepository conflictRepo;
    private final PatchRepository patchRepo;

    public List<DocumentResponse> listByProject(Long userId, Long projectId) {
        findOwnedProject(userId, projectId);
        return docRepo.findByProjectIdOrderByUpdatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    public DocumentResponse create(Long userId, Long projectId, DocumentRequest req) {
        Project project = findOwnedProject(userId, projectId);
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

    @Transactional
    public void delete(Long userId, Long docId) {
        findOwned(userId, docId);
        // 章节通过 document_id 外键引用本文档，须先清理其下冲突/补丁再删章节，否则 FK 冲突
        chapterRepo.findByDocumentId(docId).forEach(ch -> {
            conflictRepo.findByChapterId(ch.getId())
                    .forEach(c -> patchRepo.deleteByConflictId(c.getId()));
            conflictRepo.deleteByChapterId(ch.getId());
        });
        chapterRepo.deleteByDocumentId(docId);
        docRepo.deleteById(docId);
    }

    private Document findOwned(Long userId, Long docId) {
        Document doc = docRepo.findById(docId)
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        if (!doc.getUser().getId().equals(userId))
            throw new ForbiddenException("无权操作此文档");
        return doc;
    }

    /** 校验项目存在且属于当前用户，防止越权往他人项目插入/读取章节。 */
    private Project findOwnedProject(Long userId, Long projectId) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new NotFoundException("项目不存在"));
        if (!project.getUser().getId().equals(userId))
            throw new ForbiddenException("无权操作此项目");
        return project;
    }

    private DocumentResponse toResponse(Document d) {
        return DocumentResponse.builder()
                .id(d.getId()).projectId(d.getProject().getId())
                .title(d.getTitle()).content(d.getContent())
                .createdAt(d.getCreatedAt().toString())
                .updatedAt(d.getUpdatedAt().toString()).build();
    }
}
