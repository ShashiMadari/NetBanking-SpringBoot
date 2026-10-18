package com.inetbank.identity.audit;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-events")
@PreAuthorize("hasRole('ADMIN')")
public class AuditEventController {

    private final AuditEventService auditEventService;

    public AuditEventController(
            AuditEventService auditEventService) {

        this.auditEventService = auditEventService;
    }

    @GetMapping
    public List<AuditEventResponse> latest(
            Authentication authentication) {

        return auditEventService.getLatestAuditEvents(
                authentication.getName()
        );
    }
}