package com.inetbank.identity.notification;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerNotificationJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerNotificationJdbcRepository(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(
            Long userId,
            String eventType,
            String message) {

        jdbcTemplate.update("""
                INSERT INTO notifications (
                    user_id,
                    channel,
                    event_type,
                    message,
                    is_read,
                    created_at
                )
                VALUES (?, 'IN_APP', ?, ?, 'N', SYSTIMESTAMP)
                """, userId, eventType, message);
    }

    public List<NotificationResponse> find(
            Long userId,
            NotificationPeriod period,
            NotificationCategory category,
            NotificationReadStatus readStatus,
            String search,
            int limit) {

        StringBuilder sql = new StringBuilder("""
                SELECT notification_id,
                       channel,
                       event_type,
                       message,
                       is_read,
                       created_at
                FROM notifications
                WHERE user_id = ?
                """);

        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);

        if (period.getDays() > 0) {
            sql.append("""
                    AND created_at >= ?
                    """);

            parameters.add(Timestamp.valueOf(
                    LocalDateTime.now().minusDays(period.getDays())
            ));
        }

        if (category != NotificationCategory.ALL) {
            sql.append("""
                    AND event_type = ?
                    """);

            parameters.add(category.name());
        }

        if (readStatus == NotificationReadStatus.READ) {
            sql.append("""
                    AND is_read = 'Y'
                    """);
        }

        if (readStatus == NotificationReadStatus.UNREAD) {
            sql.append("""
                    AND is_read = 'N'
                    """);
        }

        if (search != null && !search.isBlank()) {
            sql.append("""
                    AND LOWER(message) LIKE ?
                    """);

            parameters.add(
                    "%" + search.trim().toLowerCase() + "%"
            );
        }

        sql.append("""
                ORDER BY created_at DESC
                FETCH FIRST %d ROWS ONLY
                """.formatted(limit));

        return jdbcTemplate.query(
                sql.toString(),
                (row, index) -> new NotificationResponse(
                        row.getLong("notification_id"),
                        row.getString("channel"),
                        row.getString("event_type"),
                        row.getString("message"),
                        "Y".equalsIgnoreCase(
                                row.getString("is_read")
                        ),
                        row.getTimestamp("created_at")
                                .toLocalDateTime()
                ),
                parameters.toArray()
        );
    }

    public long unreadCount(Long userId) {

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ?
                  AND is_read = 'N'
                """, Long.class, userId);

        return count == null ? 0 : count;
    }

    public boolean markRead(
            Long notificationId,
            Long userId) {

        return jdbcTemplate.update("""
                UPDATE notifications
                SET is_read = 'Y'
                WHERE notification_id = ?
                  AND user_id = ?
                """, notificationId, userId) > 0;
    }

    public int markAllRead(Long userId) {

        return jdbcTemplate.update("""
                UPDATE notifications
                SET is_read = 'Y'
                WHERE user_id = ?
                  AND is_read = 'N'
                """, userId);
    }
}