package com.aiwriter.service;

import com.aiwriter.ai.ClaudeService;
import com.aiwriter.dto.request.AiGenerateElementsRequest;
import com.aiwriter.dto.request.FrameworkElementRequest;
import com.aiwriter.dto.response.ElementResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.FrameworkElementRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElementServiceTest {

    @Mock private FrameworkElementRepository elementRepo;
    @Mock private FrameworkService frameworkService;
    @Mock private ClaudeService claudeService;
    @InjectMocks private ElementService elementService;

    private Framework framework;
    private FrameworkElement element;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(1L).username("owner").build();
        Project project = Project.builder().id(10L).name("P").user(user).build();
        framework = Framework.builder().id(100L).project(project).user(user)
                .status(Framework.FrameworkStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        element = FrameworkElement.builder()
                .id("abc-123").version(1).framework(framework).user(user)
                .type("character").name("张三").content("主角，25岁剑客")
                .isDeprecated(false).createdAt(LocalDateTime.now()).build();
    }

    @Test
    void shouldCreateElement() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.save(any(FrameworkElement.class))).thenReturn(element);

        FrameworkElementRequest req = new FrameworkElementRequest();
        req.setType("character"); req.setName("张三"); req.setContent("主角，25岁剑客");

        ElementResponse response = elementService.create(1L, 100L, req);

        assertNotNull(response);
        assertEquals("张三", response.getName());
        assertEquals(1, response.getVersion());
        verify(elementRepo).save(any(FrameworkElement.class));
    }

    @Test
    void shouldRejectCreateForNonOwner() {
        when(frameworkService.findOwned(2L, 100L)).thenThrow(new ForbiddenException("无权操作"));

        assertThrows(ForbiddenException.class,
                () -> elementService.create(2L, 100L, new FrameworkElementRequest()));
        verify(elementRepo, never()).save(any());
    }

    @Test
    void shouldUpdateElementWithNewVersion() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findLatestByElementId("abc-123")).thenReturn(Optional.of(element));
        FrameworkElement v2 = FrameworkElement.builder()
                .id("abc-123").version(2).framework(framework)
                .user(User.builder().id(1L).build())
                .type("character").name("张三").content("主角，26岁剑客").isDeprecated(false)
                .createdAt(LocalDateTime.now()).build();
        when(elementRepo.save(any(FrameworkElement.class))).thenReturn(v2);

        FrameworkElementRequest req = new FrameworkElementRequest();
        req.setType("character"); req.setName("张三"); req.setContent("主角，26岁剑客");

        ElementResponse response = elementService.update(1L, 100L, "abc-123", req);

        assertEquals(2, response.getVersion());
        assertEquals("主角，26岁剑客", response.getContent());
    }

    @Test
    void shouldThrowNotFoundOnUpdateMissingElement() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findLatestByElementId("nonexistent")).thenReturn(Optional.empty());

        FrameworkElementRequest req = new FrameworkElementRequest();
        req.setType("character"); req.setName("X"); req.setContent("Y");

        assertThrows(NotFoundException.class,
                () -> elementService.update(1L, 100L, "nonexistent", req));
    }

    @Test
    void shouldDeleteElementByDeprecating() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findLatestByElementId("abc-123")).thenReturn(Optional.of(element));

        elementService.delete(1L, 100L, "abc-123");

        assertTrue(element.getIsDeprecated());
        verify(elementRepo).save(element);
    }

    @Test
    void shouldListElementsFilteredByType() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        FrameworkElement loc = FrameworkElement.builder()
                .id("loc-1").version(1).framework(framework)
                .user(User.builder().id(1L).build())
                .type("location").name("长安").content("唐朝都城").isDeprecated(false)
                .createdAt(LocalDateTime.now()).build();
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(element, loc));

        List<ElementResponse> results = elementService.list(1L, 100L, "location");

        assertEquals(1, results.size());
        assertEquals("长安", results.get(0).getName());
    }

    @Test
    void shouldListAllElementsWhenTypeNull() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of(element));

        List<ElementResponse> results = elementService.list(1L, 100L, null);

        assertEquals(1, results.size());
    }

    @Test
    void shouldGetVersionHistory() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        FrameworkElement v2 = FrameworkElement.builder()
                .id("abc-123").version(2).framework(framework)
                .user(User.builder().id(1L).build())
                .type("character").name("张三").content("主角，26岁剑客").isDeprecated(false)
                .createdAt(LocalDateTime.now()).build();
        // 归属校验需要：取最新版本确认元素属于本框架
        when(elementRepo.findLatestByElementId("abc-123")).thenReturn(Optional.of(element));
        when(elementRepo.findVersionsByElementId("abc-123")).thenReturn(List.of(element, v2));

        List<ElementResponse> versions = elementService.getVersions(1L, 100L, "abc-123");

        assertEquals(2, versions.size());
        assertEquals(1, versions.get(0).getVersion());
        assertEquals(2, versions.get(1).getVersion());
    }

    @Test
    void shouldRejectGetVersionsForElementOfAnotherFramework() {
        // 交叉越权：我的框架（100）+ 他人的元素 → 必须 403，而不是读到内容
        Framework other = Framework.builder().id(999L)
                .user(User.builder().id(2L).build()).build();
        FrameworkElement foreign = FrameworkElement.builder()
                .id("foreign-1").version(1).framework(other)
                .user(User.builder().id(2L).build())
                .type("character").name("他人的机密").content("SECRET")
                .isDeprecated(false).createdAt(LocalDateTime.now()).build();
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findLatestByElementId("foreign-1")).thenReturn(Optional.of(foreign));

        assertThrows(ForbiddenException.class,
                () -> elementService.getVersions(1L, 100L, "foreign-1"));
        verify(elementRepo, never()).findVersionsByElementId(anyString());
    }

    @Test
    void shouldRejectDeleteForElementOfAnotherFramework() {
        Framework other = Framework.builder().id(999L)
                .user(User.builder().id(2L).build()).build();
        FrameworkElement foreign = FrameworkElement.builder()
                .id("foreign-1").version(1).framework(other)
                .user(User.builder().id(2L).build())
                .type("character").name("他人的机密").content("SECRET")
                .isDeprecated(false).createdAt(LocalDateTime.now()).build();
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findLatestByElementId("foreign-1")).thenReturn(Optional.of(foreign));

        assertThrows(ForbiddenException.class,
                () -> elementService.delete(1L, 100L, "foreign-1"));
        verify(elementRepo, never()).save(any(FrameworkElement.class));
    }

    @Test
    void shouldAiGenerateAndParseJson() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of());
        when(elementRepo.save(any(FrameworkElement.class))).thenAnswer(inv -> {
            FrameworkElement e = inv.getArgument(0);
            e.setCreatedAt(LocalDateTime.now());
            return e;
        });
        when(claudeService.complete(anyString(), anyString()))
                .thenReturn("[{\"name\":\"李四\",\"content\":\"女主角，法师\"}]");

        AiGenerateElementsRequest req = new AiGenerateElementsRequest();
        req.setType("character"); req.setHint("女主角");

        List<ElementResponse> results = elementService.aiGenerate(1L, 100L, req);

        assertEquals(1, results.size());
        assertEquals("李四", results.get(0).getName());
        verify(claudeService).complete(anyString(), anyString());
    }

    @Test
    void shouldAiGenerateFallbackOnInvalidJson() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(elementRepo.findLatestByFrameworkId(100L)).thenReturn(List.of());
        when(elementRepo.save(any(FrameworkElement.class))).thenAnswer(inv -> {
            FrameworkElement e = inv.getArgument(0);
            e.setCreatedAt(LocalDateTime.now());
            return e;
        });
        when(claudeService.complete(anyString(), anyString())).thenReturn("这不是JSON");

        AiGenerateElementsRequest req = new AiGenerateElementsRequest();
        req.setType("character");

        List<ElementResponse> results = elementService.aiGenerate(1L, 100L, req);

        assertEquals(1, results.size());
        assertEquals("AI 生成", results.get(0).getName());
        assertEquals("这不是JSON", results.get(0).getContent());
    }
}
