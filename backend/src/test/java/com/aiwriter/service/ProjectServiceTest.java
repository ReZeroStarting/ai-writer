package com.aiwriter.service;

import com.aiwriter.dto.request.ProjectRequest;
import com.aiwriter.dto.response.ProjectResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.DocumentRepository;
import com.aiwriter.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepo;
    @Mock private DocumentRepository docRepo;
    @InjectMocks private ProjectService projectService;

    private User owner;
    private User other;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).username("owner").build();
        other = User.builder().id(2L).username("other").build();
        project = Project.builder()
                .id(10L).name("My Project").description("A test project").user(owner)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldCreateProject() {
        ProjectRequest req = new ProjectRequest();
        req.setName("New Project");
        req.setDescription("Description");

        when(projectRepo.save(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectService.create(1L, req);

        assertNotNull(response);
        assertEquals("My Project", response.getName());
        verify(projectRepo).save(any(Project.class));
    }

    @Test
    void shouldListUserProjects() {
        when(projectRepo.findByUserIdOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of(project));

        List<ProjectResponse> list = projectService.list(1L);

        assertEquals(1, list.size());
        assertEquals("My Project", list.get(0).getName());
    }

    @Test
    void shouldReturnEmptyListForNewUser() {
        when(projectRepo.findByUserIdOrderByUpdatedAtDesc(99L))
                .thenReturn(List.of());

        List<ProjectResponse> list = projectService.list(99L);

        assertTrue(list.isEmpty());
    }

    @Test
    void shouldUpdateOwnedProject() {
        ProjectRequest req = new ProjectRequest();
        req.setName("Updated Name");
        req.setDescription("Updated Desc");

        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));
        when(projectRepo.save(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectService.update(1L, 10L, req);

        assertNotNull(response);
        verify(projectRepo).save(any(Project.class));
    }

    @Test
    void shouldRejectUpdateOthersProject() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));

        assertThrows(ForbiddenException.class,
                () -> projectService.update(2L, 10L, new ProjectRequest()));
        verify(projectRepo, never()).save(any());
    }

    @Test
    void shouldDeleteOwnedProject() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));

        projectService.delete(1L, 10L);

        verify(projectRepo).deleteById(10L);
    }

    @Test
    void shouldRejectDeleteOthersProject() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));

        assertThrows(ForbiddenException.class,
                () -> projectService.delete(2L, 10L));
        verify(projectRepo, never()).deleteById(anyLong());
    }

    @Test
    void shouldReturn404ForNonexistentProject() {
        when(projectRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> projectService.update(1L, 999L, new ProjectRequest()));
    }
}
