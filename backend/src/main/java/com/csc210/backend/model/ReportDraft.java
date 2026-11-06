package com.csc210.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A private, in-progress report owned by one user and one report type. */
@Entity
@Table(name = "report_draft", uniqueConstraints = {
        @UniqueConstraint(name = "report_draft_owner_type_unique", columnNames = {"reporter_id", "report_type"})
})
public class ReportDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;
    @Column(name = "report_type", nullable = false, length = 8) private String reportType;
    private String title, category, color, location, date, time, collectionDesk;
    @Column(length = 4000) private String description;
    private String interiorColorOrLining, uniqueMarks, contents;
    @Column(nullable = false) private LocalDateTime updatedAt;

    public ReportDraft() {}
    public ReportDraft(User reporter, String reportType) { this.reporter = reporter; this.reportType = reportType; }
    public Long getId() { return id; }
    public User getReporter() { return reporter; } public void setReporter(User value) { reporter = value; }
    public String getReportType() { return reportType; } public void setReportType(String value) { reportType = value; }
    public String getTitle() { return title; } public void setTitle(String value) { title = value; }
    public String getCategory() { return category; } public void setCategory(String value) { category = value; }
    public String getColor() { return color; } public void setColor(String value) { color = value; }
    public String getLocation() { return location; } public void setLocation(String value) { location = value; }
    public String getDate() { return date; } public void setDate(String value) { date = value; }
    public String getTime() { return time; } public void setTime(String value) { time = value; }
    public String getCollectionDesk() { return collectionDesk; } public void setCollectionDesk(String value) { collectionDesk = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
    public String getInteriorColorOrLining() { return interiorColorOrLining; } public void setInteriorColorOrLining(String value) { interiorColorOrLining = value; }
    public String getUniqueMarks() { return uniqueMarks; } public void setUniqueMarks(String value) { uniqueMarks = value; }
    public String getContents() { return contents; } public void setContents(String value) { contents = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
