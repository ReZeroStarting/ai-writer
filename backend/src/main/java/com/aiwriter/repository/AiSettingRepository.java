package com.aiwriter.repository;

import com.aiwriter.model.AiSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSettingRepository extends JpaRepository<AiSetting, Long> {
}
