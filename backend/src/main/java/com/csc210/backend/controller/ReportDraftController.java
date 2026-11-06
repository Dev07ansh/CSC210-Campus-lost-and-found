package com.csc210.backend.controller;

import com.csc210.backend.model.ReportDraft;
import com.csc210.backend.model.User;
import com.csc210.backend.repository.ReportDraftRepository;
import com.csc210.backend.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Map;

/** Private autosave endpoints for the lost and found report forms. */
@RestController
@RequestMapping("/api/report-drafts")
public class ReportDraftController {
    private final ReportDraftRepository drafts;
    private final CurrentUserService currentUsers;

    public ReportDraftController(ReportDraftRepository drafts, CurrentUserService currentUsers) {
        this.drafts = drafts; this.currentUsers = currentUsers;
    }

    @GetMapping("/{type}")
    public DraftResponse get(@RequestHeader(value = "X-User-Email", required = false) String userEmail,
                             @PathVariable String type) {
        User user = currentUsers.requireUser(userEmail);
        return drafts.findByReporterIdAndReportType(user.getId(), reportType(type)).map(this::response)
                .orElse(new DraftResponse(Map.of(), null));
    }

    @PutMapping("/{type}")
    public DraftResponse save(@RequestHeader(value = "X-User-Email", required = false) String userEmail,
                              @PathVariable String type, @RequestBody DraftRequest request) {
        User user = currentUsers.requireUser(userEmail);
        String reportType = reportType(type);
        ReportDraft draft = drafts.findByReporterIdAndReportType(user.getId(), reportType)
                .orElseGet(() -> new ReportDraft(user, reportType));
        Map<String, String> values = request == null || request.values() == null ? Map.of() : request.values();
        draft.setTitle(value(values, "title"));
        draft.setCategory(value(values, "category"));
        draft.setColor(value(values, "color"));
        draft.setLocation(value(values, "location"));
        draft.setDescription(value(values, "description"));
        draft.setDate(value(values, "date"));
        draft.setTime(value(values, "time"));
        draft.setCollectionDesk(value(values, "collectionDesk"));
        draft.setInteriorColorOrLining(value(values, "interiorColorOrLining"));
        draft.setUniqueMarks(value(values, "uniqueMarks"));
        draft.setContents(value(values, "contents"));
        draft.setUpdatedAt(LocalDateTime.now());
        return response(drafts.save(draft));
    }

    @DeleteMapping("/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "X-User-Email", required = false) String userEmail,
                       @PathVariable String type) {
        User user = currentUsers.requireUser(userEmail);
        drafts.findByReporterIdAndReportType(user.getId(), reportType(type)).ifPresent(drafts::delete);
    }

    private String reportType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (!normalized.equals("LOST") && !normalized.equals("FOUND")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Draft type must be LOST or FOUND.");
        }
        return normalized;
    }
    private String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }
    private DraftResponse response(ReportDraft draft) {
        return new DraftResponse(Map.ofEntries(
                Map.entry("title", draft.getTitle()), Map.entry("category", draft.getCategory()),
                Map.entry("color", draft.getColor()), Map.entry("location", draft.getLocation()),
                Map.entry("description", draft.getDescription()), Map.entry("date", draft.getDate()),
                Map.entry("time", draft.getTime()), Map.entry("collectionDesk", draft.getCollectionDesk()),
                Map.entry("interiorColorOrLining", draft.getInteriorColorOrLining()),
                Map.entry("uniqueMarks", draft.getUniqueMarks()), Map.entry("contents", draft.getContents())), draft.getUpdatedAt());
    }
    public record DraftRequest(Map<String, String> values) {}
    public record DraftResponse(Map<String, String> values, LocalDateTime updatedAt) {}
}
