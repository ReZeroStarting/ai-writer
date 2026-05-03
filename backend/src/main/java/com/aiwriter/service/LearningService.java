package com.aiwriter.service;

import com.aiwriter.ai.StyleAnalyzer;
import com.aiwriter.dto.request.LearnRequest;
import com.aiwriter.dto.response.JobResponse;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LearningService {
    private final SourceMaterialRepository materialRepo;
    private final LearningJobRepository jobRepo;
    private final StyleTemplateRepository styleRepo;
    private final StyleAnalyzer analyzer;

    @Transactional
    public JobResponse startLearning(Long userId, Long templateId, LearnRequest req) {
        StyleTemplate template = styleRepo.findById(templateId)
                .orElseThrow(() -> new RuntimeException("风格模板不存在"));

        SourceMaterial material = SourceMaterial.builder()
                .user(User.builder().id(userId).build()).template(template)
                .title(req.getTitle()).content(req.getContent()).build();
        material = materialRepo.save(material);

        LearningJob job = LearningJob.builder()
                .user(User.builder().id(userId).build()).template(template)
                .material(material).status(LearningJob.JobStatus.PENDING).build();
        job = jobRepo.save(job);

        processJobAsync(job.getId());
        return toResponse(job);
    }

    @Async
    protected void processJobAsync(Long jobId) {
        LearningJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null) return;
        try {
            job.setStatus(LearningJob.JobStatus.PROCESSING);
            jobRepo.save(job);
            String result = analyzer.analyzeStyle(job.getMaterial().getContent());
            job.setResultPrompt(result);
            job.setStatus(LearningJob.JobStatus.COMPLETED);
            StyleTemplate template = job.getTemplate();
            template.setSystemPrompt(result);
            styleRepo.save(template);
        } catch (Exception e) {
            job.setStatus(LearningJob.JobStatus.FAILED);
            job.setErrorMsg(e.getMessage());
        }
        jobRepo.save(job);
    }

    public List<JobResponse> listJobs(Long userId, Long templateId) {
        return jobRepo.findByTemplateIdAndUserIdOrderByCreatedAtDesc(templateId, userId)
                .stream().map(this::toResponse).toList();
    }

    public JobResponse getJob(Long userId, Long id) {
        LearningJob j = jobRepo.findById(id).orElseThrow(() -> new RuntimeException("任务不存在"));
        if (!j.getUser().getId().equals(userId))
            throw new RuntimeException("无权查看此任务");
        return toResponse(j);
    }

    private JobResponse toResponse(LearningJob j) {
        return JobResponse.builder()
                .id(j.getId()).templateId(j.getTemplate().getId())
                .materialId(j.getMaterial().getId()).status(j.getStatus().name())
                .resultPrompt(j.getResultPrompt()).errorMsg(j.getErrorMsg())
                .createdAt(j.getCreatedAt().toString())
                .updatedAt(j.getUpdatedAt().toString()).build();
    }
}
