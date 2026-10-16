package com.aiwriter.service;

import com.aiwriter.dto.request.DocumentRequest;
import com.aiwriter.dto.response.DocumentResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.Document;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.ChapterRepository;
import com.aiwriter.repository.ConflictRepository;
import com.aiwriter.repository.DocumentRepository;
import com.aiwriter.repository.PatchRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository docRepo;
    @Mock private ProjectRepository projectRepo;
    @Mock private ChapterRepository chapterRepo;
    @Mock private ConflictRepository conflictRepo;
    @Mock private PatchRepository patchRepo;
    @InjectMocks private DocumentService documentService;

    private User owner;
    private User other;
    private Project project;
    private Document document;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).username("owner").build();
        other = User.builder().id(2L).username("other").build();
        project = Project.builder().id(10L).name("Test Project").user(owner).build();
        document = Document.builder()
                .id(100L).title("Test Doc").content("Hello").user(owner).project(project)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldCreateDocument() {
        DocumentRequest req = new DocumentRequest();
        req.setTitle("New Doc");
        req.setContent("Some content");

        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));
        when(docRepo.save(any(Document.class))).thenReturn(document);

        DocumentResponse response = documentService.create(1L, 10L, req);

        assertNotNull(response);
        assertEquals("Test Doc", response.getTitle());
        verify(docRepo).save(any(Document.class));
    }

    @Test
    void shouldRejectCreateForNonExistentProject() {
        when(projectRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> documentService.create(1L, 99L, new DocumentRequest()));
    }

    @Test
    void shouldGetOwnedDocument() {
        when(docRepo.findById(100L)).thenReturn(Optional.of(document));

        DocumentResponse response = documentService.get(1L, 100L);

        assertNotNull(response);
        assertEquals("Test Doc", response.getTitle());
    }

    @Test
    void shouldRejectAccessToOthersDocument() {
        when(docRepo.findById(100L)).thenReturn(Optional.of(document));

        assertThrows(ForbiddenException.class, () -> documentService.get(2L, 100L));
    }

    @Test
    void shouldReturn404ForNonexistentDocument() {
        when(docRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> documentService.get(1L, 999L));
    }

    @Test
    void shouldUpdateOwnedDocument() {
        DocumentRequest req = new DocumentRequest();
        req.setTitle("Updated Title");
        req.setContent("Updated content");

        when(docRepo.findById(100L)).thenReturn(Optional.of(document));
        when(docRepo.save(any(Document.class))).thenReturn(document);

        DocumentResponse response = documentService.update(1L, 100L, req);

        assertNotNull(response);
        verify(docRepo).save(any(Document.class));
    }

    @Test
    void shouldDeleteOwnedDocument() {
        when(docRepo.findById(100L)).thenReturn(Optional.of(document));
        when(chapterRepo.findByDocumentId(100L)).thenReturn(List.of());

        documentService.delete(1L, 100L);

        verify(chapterRepo).deleteByDocumentId(100L);
        verify(docRepo).deleteById(100L);
    }

    @Test
    void shouldRejectDeleteOthersDocument() {
        when(docRepo.findById(100L)).thenReturn(Optional.of(document));

        assertThrows(ForbiddenException.class, () -> documentService.delete(2L, 100L));
        verify(docRepo, never()).deleteById(anyLong());
    }

    @Test
    void shouldListDocumentsByProject() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));
        when(docRepo.findByProjectIdOrderByUpdatedAtDesc(10L))
                .thenReturn(List.of(document));

        List<DocumentResponse> list = documentService.listByProject(1L, 10L);

        assertEquals(1, list.size());
        assertEquals("Test Doc", list.get(0).getTitle());
    }

    /** 越权回归：不能往他人项目插入章节。 */
    @Test
    void shouldRejectCreateInOthersProject() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));

        assertThrows(ForbiddenException.class,
                () -> documentService.create(2L, 10L, new DocumentRequest()));
        verify(docRepo, never()).save(any(Document.class));
    }

    /** 越权回归：不能读取他人项目的章节列表。 */
    @Test
    void shouldRejectListOthersProject() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));

        assertThrows(ForbiddenException.class,
                () -> documentService.listByProject(2L, 10L));
    }
}
