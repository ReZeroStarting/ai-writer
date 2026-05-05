package com.aiwriter.service;

import com.aiwriter.dto.response.WritingEventResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.model.*;
import com.aiwriter.repository.WritingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private WritingEventRepository eventRepo;
    @Mock private FrameworkService frameworkService;
    @InjectMocks private EventService eventService;

    private Framework framework;
    private WritingEvent event;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(1L).build();
        framework = Framework.builder().id(100L).user(user)
                .status(Framework.FrameworkStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        event = WritingEvent.builder().id(1L).framework(framework)
                .eventType(WritingEvent.EventType.ELEMENT_CREATED)
                .payload("{\"elementId\":\"abc\"}")
                .timestamp(LocalDateTime.now()).build();
    }

    @Test
    void shouldAppendEventInternal() {
        when(eventRepo.save(any(WritingEvent.class))).thenReturn(event);

        WritingEvent result = eventService.appendEventInternal(framework,
                WritingEvent.EventType.ELEMENT_CREATED,
                Map.of("elementId", "abc"));

        assertNotNull(result);
        assertEquals(WritingEvent.EventType.ELEMENT_CREATED, result.getEventType());
        verify(eventRepo).save(any(WritingEvent.class));
    }

    @Test
    void shouldListEvents() {
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(eventRepo.findByFrameworkIdOrderByTimestampDesc(100L)).thenReturn(List.of(event));

        List<WritingEventResponse> results = eventService.list(1L, 100L);

        assertEquals(1, results.size());
        assertEquals("ELEMENT_CREATED", results.get(0).getEventType());
    }

    @Test
    void shouldRejectListForNonOwner() {
        when(frameworkService.findOwned(2L, 100L)).thenThrow(new ForbiddenException("无权操作"));

        assertThrows(ForbiddenException.class, () -> eventService.list(2L, 100L));
    }

    @Test
    void shouldListEventsUpToTimestamp() {
        LocalDateTime until = LocalDateTime.now().minusHours(1);
        when(frameworkService.findOwned(1L, 100L)).thenReturn(framework);
        when(eventRepo.findByFrameworkIdUpTo(100L, until)).thenReturn(List.of());

        List<WritingEventResponse> results = eventService.listUpTo(1L, 100L, until);

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldHandleNullPayload() {
        WritingEvent evt = WritingEvent.builder().id(2L).framework(framework)
                .eventType(WritingEvent.EventType.FRAMEWORK_FINISHED)
                .payload(null).timestamp(LocalDateTime.now()).build();
        when(eventRepo.save(any(WritingEvent.class))).thenReturn(evt);

        WritingEvent result = eventService.appendEventInternal(framework,
                WritingEvent.EventType.FRAMEWORK_FINISHED, null);

        assertNotNull(result);
        assertNull(result.getPayload());
    }
}
