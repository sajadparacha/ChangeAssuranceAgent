package com.company.changeassurance.application.port.out;

import java.util.Optional;

/**
 * Loads configurable policy thresholds (risk weights, coverage gates). Implementation in Phase 2.
 */
public interface PolicyRepository {

    Optional<String> findPolicyValue(String policyKey);

    String requirePolicyValue(String policyKey);
}
