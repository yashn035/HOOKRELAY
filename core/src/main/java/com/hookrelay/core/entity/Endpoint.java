package com.hookrelay.core.entity;

import com.hookrelay.core.entity.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "endpoints")
public class Endpoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "secret", nullable = false)
    private String secret;

    @Convert(converter = StringListConverter.class)
    @Column(name = "event_types_filter", nullable = false)
    @Builder.Default
    private List<String> eventTypesFilter = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private int consecutiveFailures = 0;

    @Column(name = "failure_threshold", nullable = false)
    @Builder.Default
    private int failureThreshold = 10;

    @Column(name = "circuit_broken", nullable = false)
    @Builder.Default
    private boolean circuitBroken = false;

    @Column(name = "circuit_broken_at")
    private Instant circuitBrokenAt;

    @Column(name = "rate_limit_per_second")
    private Integer rateLimitPerSecond;

    /**
     * Checks if this endpoint is subscribed to a specific event type.
     * An empty or wildcard list matches all event types.
     */
    public boolean isSubscribedTo(String eventType) {
        if (!active || circuitBroken) {
            return false;
        }
        if (eventTypesFilter == null || eventTypesFilter.isEmpty()) {
            return true;
        }
        return eventTypesFilter.contains("*") || eventTypesFilter.contains(eventType);
    }
}
