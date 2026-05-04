package com.aiwriter.service;

import com.aiwriter.dto.request.ProjectRequest;
import com.aiwriter.dto.response.ProjectResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepo;

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
