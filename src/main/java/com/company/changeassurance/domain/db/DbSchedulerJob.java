package com.company.changeassurance.domain.db;

import java.util.Objects;

/**
 * Scheduler job that references a package (read-only catalog evidence).
 */
public record DbSchedulerJob(
        String owner,
        String jobName,
        String jobType,
        String enabled,
        String state,
        String actionExcerpt
) {
    public DbSchedulerJob {
        Objects.requireNonNull(jobName, "jobName must not be null");
        if (jobName.isBlank()) {
            throw new IllegalArgumentException("jobName must not be blank");
        }
        jobName = jobName.trim().toUpperCase();
        owner = owner == null || owner.isBlank() ? null : owner.trim().toUpperCase();
        jobType = jobType == null || jobType.isBlank() ? "UNKNOWN" : jobType.trim().toUpperCase();
        enabled = enabled == null || enabled.isBlank() ? "UNKNOWN" : enabled.trim().toUpperCase();
        state = state == null || state.isBlank() ? "UNKNOWN" : state.trim().toUpperCase();
        actionExcerpt = actionExcerpt == null ? "" : actionExcerpt;
        if (actionExcerpt.length() > 400) {
            actionExcerpt = actionExcerpt.substring(0, 400) + "...";
        }
    }

    public String qualifiedName() {
        return owner == null ? jobName : owner + "." + jobName;
    }
}
