package com.inetbank.identity.audit;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.identity.registration.User;
import com.inetbank.identity.registration.UserRepository;

@Service
public class AuditEventService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;

    public AuditEventService(
            JdbcTemplate jdbcTemplate,
            UserRepository users) {

        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getLatestAuditEvents(
            String email) {

        User admin = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated administrator was not found."
                ));

        if (!"ADMIN".equals(admin.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator access is required."
            );
        }

        String sql = """
                SELECT audit_event_id,
                       user_id,
                       action,
                       entity_type,
                       entity_id,
                       ip_address,
                       created_at
                  FROM audit_events
                 ORDER BY created_at DESC,
                          audit_event_id DESC
                 FETCH FIRST 50 ROWS ONLY
                """;

        return jdbcTemplate.query(
                sql,
                (row, index) -> {

                    Number eventUserId = (Number) row.getObject(
                            "user_id"
                    );

                    return new AuditEventResponse(
                            row.getLong("audit_event_id"),
                            eventUserId == null
                                    ? null
                                    : eventUserId.longValue(),
                            row.getString("action"),
                            row.getString("entity_type"),
                            row.getString("entity_id"),
                            row.getString("ip_address"),
                            row.getTimestamp("created_at")
                                    .toLocalDateTime()
                    );
                }
        );
    }
}