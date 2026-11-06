package com.csc210.backend.repository;

import com.csc210.backend.model.ReportDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReportDraftRepository extends JpaRepository<ReportDraft, Long> {
    Optional<ReportDraft> findByReporterIdAndReportType(Long reporterId, String reportType);
}
