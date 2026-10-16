package com.aiwriter.service;

import com.aiwriter.dto.response.FrameworkResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FrameworkService {
    private final FrameworkRepository frameworkRepo;
    private final ProjectRepository projectRepo;
    private final FrameworkElementRepository elementRepo;
    private final ChapterRepository chapterRepo;
    private final ConflictRepository conflictRepo;

    public FrameworkResponse openFramework(Long userId, Long projectId) {
        Project project = OwnershipGuard.requireOwned(
                projectRepo.findById(projectId), userId,
                p -> p.getUser().getId(), "项目", "项目");
        if ("framework".equals(project.getMode()))
            throw new IllegalArgumentException("该项目已是框架模式");
        if (frameworkRepo.findByProjectId(projectId).isPresent())
            throw new IllegalArgumentException("该项目已有框架");

        project.setMode("framework");
        projectRepo.save(project);

        Framework fw = Framework.builder()
                .project(project)
                .user(User.builder().id(userId).build())
                .status(Framework.FrameworkStatus.ACTIVE)
                .build();
        return toResponse(frameworkRepo.save(fw));
    }

    public FrameworkResponse getFramework(Long userId, Long frameworkId) {
        Framework fw = findOwned(userId, frameworkId);
        return toResponse(fw);
    }

    public FrameworkResponse getByProjectId(Long userId, Long projectId) {
        Framework fw = frameworkRepo.findByProjectId(projectId)
                .orElseThrow(() -> new NotFoundException("该项目未开启框架模式"));
        if (!fw.getUser().getId().equals(userId))
            throw new ForbiddenException("无权操作此框架");
        return toResponse(fw);
    }

    public FrameworkResponse finishFramework(Long userId, Long frameworkId) {
        Framework fw = findOwned(userId, frameworkId);
        fw.setStatus(Framework.FrameworkStatus.FINISHED);
        frameworkRepo.save(fw);
        return toResponse(fw);
    }

    public Framework findOwned(Long userId, Long frameworkId) {
        return OwnershipGuard.requireOwned(
                frameworkRepo.findById(frameworkId), userId,
                fw -> fw.getUser().getId(), "框架", "框架");
    }

    private FrameworkResponse toResponse(Framework fw) {
        return FrameworkResponse.builder()
                .id(fw.getId())
                .projectId(fw.getProject().getId())
                .status(fw.getStatus().name())
                .elementCount(elementRepo.findLatestByFrameworkId(fw.getId()).size())
                .chapterCount(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(fw.getId()).size())
                .unresolvedConflictCount((int) conflictRepo.findByFrameworkIdOrderByCreatedAtDesc(fw.getId())
                        .stream().filter(c -> c.getStatus() == Conflict.ConflictStatus.PENDING).count())
                .createdAt(fw.getCreatedAt().toString())
                .updatedAt(fw.getUpdatedAt().toString())
                .build();
    }
}
