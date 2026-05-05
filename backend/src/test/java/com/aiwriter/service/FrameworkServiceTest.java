package com.aiwriter.service;

import com.aiwriter.dto.response.FrameworkResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FrameworkServiceTest {

    @Mock private FrameworkRepository frameworkRepo;
    @Mock private ProjectRepository projectRepo;
    @Mock private FrameworkElementRepository elementRepo;
    @Mock private ChapterRepository chapterRepo;
    @Mock private ConflictRepository conflictRepo;
    @InjectMocks private FrameworkService frameworkService;

    private User owner;
    private Project project;
    private Framework framework;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).username("owner").build();
        project = Project.builder().id(10L).name("My Project").user(owner)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        framework = Framework.builder().id(100L).project(project).user(owner)
                .status(Framework.FrameworkStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(elementRepo.findLatestByFrameworkId(anyLong())).thenReturn(List.of());
        when(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(anyLong())).thenReturn(List.of());
        when(conflictRepo.findByFrameworkIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
    }

    @Test
    void shouldOpenFramework() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));
        when(frameworkRepo.findByProjectId(10L)).thenReturn(Optional.empty());
        when(projectRepo.save(any(Project.class))).thenReturn(project);
        when(frameworkRepo.save(any(Framework.class))).thenReturn(framework);

        FrameworkResponse response = frameworkService.openFramework(1L, 10L);

        assertNotNull(response);
        assertEquals("ACTIVE", response.getStatus());
        verify(projectRepo).save(any(Project.class));
        verify(frameworkRepo).save(any(Framework.class));
    }

    @Test
    void shouldRejectOpenIfAlreadyFrameworkMode() {
        project.setMode("framework");
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));

        assertThrows(IllegalArgumentException.class, () -> frameworkService.openFramework(1L, 10L));
        verify(frameworkRepo, never()).save(any());
    }

    @Test
    void shouldRejectOpenIfFrameworkExists() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));
        when(frameworkRepo.findByProjectId(10L)).thenReturn(Optional.of(framework));

        assertThrows(IllegalArgumentException.class, () -> frameworkService.openFramework(1L, 10L));
        verify(frameworkRepo, never()).save(any());
    }

    @Test
    void shouldRejectOpenForNonOwner() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));

        assertThrows(ForbiddenException.class, () -> frameworkService.openFramework(2L, 10L));
    }

    @Test
    void shouldGetFramework() {
        when(frameworkRepo.findById(100L)).thenReturn(Optional.of(framework));

        FrameworkResponse response = frameworkService.getFramework(1L, 100L);

        assertNotNull(response);
        assertEquals(100L, response.getId());
    }

    @Test
    void shouldThrowNotFoundForMissingFramework() {
        when(frameworkRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> frameworkService.getFramework(1L, 999L));
    }

    @Test
    void shouldRejectGetForNonOwner() {
        when(frameworkRepo.findById(100L)).thenReturn(Optional.of(framework));

        assertThrows(ForbiddenException.class, () -> frameworkService.getFramework(2L, 100L));
    }

    @Test
    void shouldGetByProjectId() {
        when(frameworkRepo.findByProjectId(10L)).thenReturn(Optional.of(framework));

        FrameworkResponse response = frameworkService.getByProjectId(1L, 10L);

        assertNotNull(response);
        assertEquals(100L, response.getId());
    }

    @Test
    void shouldThrowNotFoundForGetByProjectId() {
        when(frameworkRepo.findByProjectId(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> frameworkService.getByProjectId(1L, 99L));
    }

    @Test
    void shouldRejectGetByProjectIdForNonOwner() {
        when(frameworkRepo.findByProjectId(10L)).thenReturn(Optional.of(framework));

        assertThrows(ForbiddenException.class, () -> frameworkService.getByProjectId(2L, 10L));
    }

    @Test
    void shouldFinishFramework() {
        when(frameworkRepo.findById(100L)).thenReturn(Optional.of(framework));
        when(frameworkRepo.save(any(Framework.class))).thenReturn(framework);

        FrameworkResponse response = frameworkService.finishFramework(1L, 100L);

        assertEquals("FINISHED", response.getStatus());
        verify(frameworkRepo).save(any(Framework.class));
    }

    @Test
    void shouldRejectFinishForNonOwner() {
        when(frameworkRepo.findById(100L)).thenReturn(Optional.of(framework));

        assertThrows(ForbiddenException.class, () -> frameworkService.finishFramework(2L, 100L));
    }
}
