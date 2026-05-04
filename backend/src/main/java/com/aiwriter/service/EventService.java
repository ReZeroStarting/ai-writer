package com.aiwriter.service;

import com.aiwriter.dto.response.WritingEventResponse;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventService {
    private final WritingEventRepository eventRepo;
    private final FrameworkService frameworkService;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Internal — no auth check. Used by other services. */
    public WritingEvent appendEventInternal(Framework framework, WritingEvent.EventType type, Object payload) {
        WritingEvent event = WritingEvent.builder()
                .framework(framework)
                .eventType(type)
                .payload(payload != null ? toJson(payload) : null)
                .build();
        return eventRepo.save(event);
    }

    public List<WritingEventResponse> list(Long userId, Long frameworkId) {
        frameworkService.findOwned(userId, frameworkId);
        return eventRepo.findByFrameworkIdOrderByTimestampDesc(frameworkId).stream()
                .map(this::toResponse).toList();
    }

    public List<WritingEventResponse> listUpTo(Long userId, Long frameworkId, LocalDateTime until) {
        frameworkService.findOwned(userId, frameworkId);
        return eventRepo.findByFrameworkIdUpTo(frameworkId, until).stream()
                .map(this::toResponse).toList();
    }

    private String toJson(Object obj) {
        try { return mapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    Map<String, ?> parsePayload(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try { return mapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    private WritingEventResponse toResponse(WritingEvent e) {
        return WritingEventResponse.builder()
                .id(e.getId()).eventType(e.getEventType().name())
                .payload(e.getPayload()).timestamp(e.getTimestamp().toString())
                .build();
    }
}
