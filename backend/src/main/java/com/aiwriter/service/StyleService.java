package com.aiwriter.service;

import com.aiwriter.dto.request.StyleRequest;
import com.aiwriter.dto.response.StyleResponse;
import com.aiwriter.model.StyleTemplate;
import com.aiwriter.model.User;
import com.aiwriter.repository.StyleTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StyleService {
    private final StyleTemplateRepository styleRepo;

    public List<StyleResponse> list(Long userId) {
        return styleRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public StyleResponse create(Long userId, StyleRequest req) {
        StyleTemplate st = StyleTemplate.builder()
                .user(User.builder().id(userId).build())
                .name(req.getName()).systemPrompt(req.getSystemPrompt())
                .description(req.getDescription()).build();
        return toResponse(styleRepo.save(st));
    }

    public StyleResponse get(Long userId, Long id) { return toResponse(findOwned(userId, id)); }

    public StyleResponse update(Long userId, Long id, StyleRequest req) {
        StyleTemplate st = findOwned(userId, id);
        st.setName(req.getName()); st.setSystemPrompt(req.getSystemPrompt());
        st.setDescription(req.getDescription());
        return toResponse(styleRepo.save(st));
    }

    public void delete(Long userId, Long id) {
        findOwned(userId, id);
        styleRepo.deleteById(id);
    }

    private StyleTemplate findOwned(Long userId, Long id) {
        return OwnershipGuard.requireOwned(
                styleRepo.findById(id), userId,
                st -> st.getUser().getId(), "风格模板", "模板");
    }

    private StyleResponse toResponse(StyleTemplate st) {
        return StyleResponse.builder()
                .id(st.getId()).name(st.getName())
                .systemPrompt(st.getSystemPrompt()).description(st.getDescription())
                .createdAt(st.getCreatedAt().toString()).build();
    }
}
