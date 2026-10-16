package com.aiwriter.service;

import com.aiwriter.dto.request.ProjectRequest;
import com.aiwriter.dto.response.ProjectResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.ChapterRepository;
import com.aiwriter.repository.ConflictRepository;
import com.aiwriter.repository.DocumentRepository;
import com.aiwriter.repository.FrameworkElementRepository;
import com.aiwriter.repository.FrameworkRepository;
import com.aiwriter.repository.PatchRepository;
import com.aiwriter.repository.ProjectRepository;
import com.aiwriter.repository.WritingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepo;
    private final DocumentRepository docRepo;
    private final FrameworkRepository frameworkRepo;
    private final ChapterRepository chapterRepo;
    private final ConflictRepository conflictRepo;
    private final PatchRepository patchRepo;
    private final FrameworkElementRepository elementRepo;
    private final WritingEventRepository eventRepo;

    public List<ProjectResponse> list(Long userId) {
        return projectRepo.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public ProjectResponse create(Long userId, ProjectRequest req) {
        Project p = Project.builder()
                .user(User.builder().id(userId).build())
                .name(req.getName()).description(req.getDescription()).build();
        return toResponse(projectRepo.save(p));
    }

    public ProjectResponse update(Long userId, Long projectId, ProjectRequest req) {
        Project p = findOwned(userId, projectId);
        p.setName(req.getName()); p.setDescription(req.getDescription());
        return toResponse(projectRepo.save(p));
    }

    @Transactional
    public void delete(Long userId, Long projectId) {
        findOwned(userId, projectId);

        // 按外键依赖从叶到根依次清理，避免 FK 约束冲突（框架模式项目曾因此 500）
        frameworkRepo.findByProjectId(projectId).ifPresent(fw -> {
            Long fwId = fw.getId();
            // Patch → Conflict
            conflictRepo.findByFrameworkIdOrderByCreatedAtDesc(fwId)
                    .forEach(c -> patchRepo.deleteByConflictId(c.getId()));
            conflictRepo.deleteByFrameworkId(fwId);
            // Chapter（依赖 framework 与 document）
            chapterRepo.deleteByFrameworkId(fwId);
            // FrameworkElement / WritingEvent
            elementRepo.deleteByFrameworkId(fwId);
            eventRepo.deleteByFrameworkId(fwId);
            // Framework 本体
            frameworkRepo.deleteById(fwId);
        });

        docRepo.deleteByProjectId(projectId);
        projectRepo.deleteById(projectId);
    }

    private Project findOwned(Long userId, Long projectId) {
        Project p = projectRepo.findById(projectId)
                .orElseThrow(() -> new NotFoundException("项目不存在"));
        if (!p.getUser().getId().equals(userId))
            throw new ForbiddenException("无权操作此项目");
        return p;
    }

    private ProjectResponse toResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId()).name(p.getName()).description(p.getDescription())
                .createdAt(p.getCreatedAt().toString())
                .updatedAt(p.getUpdatedAt().toString()).build();
    }
}
